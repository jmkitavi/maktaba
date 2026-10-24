import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { randomBytes, randomUUID } from "node:crypto";
import { initializeApp, deleteApp, type App } from "firebase-admin/app";
import { FieldValue, getFirestore, Timestamp, type Firestore } from "firebase-admin/firestore";
import { initializeApp as initializeClientApp, deleteApp as deleteClientApp, type FirebaseApp } from "firebase/app";
import { connectAuthEmulator, createUserWithEmailAndPassword, getAuth } from "firebase/auth";
import {
  collection, connectFirestoreEmulator, doc, getDocFromServer, getDocsFromServer, getFirestore as getClientFirestore,
  limit, query, where, type Firestore as ClientFirestore,
} from "firebase/firestore";
import { objectInput, requestKey, type Fields } from "../../lib/sharing";
import { refreshPublication } from "../../sharingService";

const projectId = "maktaba-2e21d";
const region = "us-central1";
let app: App;
let db: Firestore;
const clientApps: FirebaseApp[] = [];
interface Reader { uid: string; token: string; db: ClientFirestore }
class CallableFailure extends Error {
  constructor(readonly status: string, message: string) { super(message); }
}
function localEnvironment(name: string, port: number): void {
  assert.match(process.env[name] ?? "", new RegExp(`^(127\\.0\\.0\\.1|localhost):${port}$`, "u"),
    `${name} must explicitly target the isolated local emulator`);
}
before(() => {
  localEnvironment("FIRESTORE_EMULATOR_HOST", 8080);
  localEnvironment("FIREBASE_AUTH_EMULATOR_HOST", 9099);
  localEnvironment("FIREBASE_STORAGE_EMULATOR_HOST", 9199);
  localEnvironment("FUNCTIONS_EMULATOR_HOST", 5001);
  assert.equal(process.env["GCLOUD_PROJECT"], projectId);
  app = initializeApp({ projectId, storageBucket: `${projectId}.firebasestorage.app` }, "callable-verification");
  db = getFirestore(app);
});
after(async () => {
  await Promise.all(clientApps.map((client) => deleteClientApp(client)));
  if (app) await deleteApp(app);
});
async function invoke(name: string, data: Fields, reader?: Reader): Promise<Fields> {
  const response = await fetch(`http://127.0.0.1:5001/${projectId}/${region}/${name}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...(reader ? { Authorization: `Bearer ${reader.token}` } : {}) },
    body: JSON.stringify({ data }), signal: AbortSignal.timeout(90_000),
  });
  const envelope = objectInput(await response.json());
  if (envelope["error"]) {
    const error = objectInput(envelope["error"]);
    throw new CallableFailure(String(error["status"]), String(error["message"]));
  }
  assert.equal(response.status, 200, `${name} must succeed`);
  return objectInput(envelope["result"]);
}
function failure(status: string) { return (error: unknown) => error instanceof CallableFailure && error.status === status; }
function stringField(data: Fields, name: string): string {
  const value = data[name];
  assert.equal(typeof value, "string", name);
  if (typeof value !== "string") throw new Error(`Missing ${name}`);
  return value;
}
async function reader(label: string): Promise<Reader> {
  const client = initializeClientApp({ projectId, apiKey: "local-emulator-only", authDomain: "localhost" }, randomUUID());
  clientApps.push(client);
  const auth = getAuth(client);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  const clientDb = getClientFirestore(client);
  connectFirestoreEmulator(clientDb, "127.0.0.1", 8080);
  const credential = await createUserWithEmailAndPassword(auth, `${randomUUID()}@example.invalid`, randomBytes(32).toString("hex"));
  const account = { uid: credential.user.uid, token: await credential.user.getIdToken(), db: clientDb };
  await invoke("createUserProfileIfNeeded", { displayName: label }, account);
  assert.equal((await db.collection(`users/${account.uid}/fcmTokens`).get()).size, 0);
  return account;
}
async function seedBook(owner: Reader, format = "PHYSICAL") {
  const catalogBookId = `local-${randomUUID()}`;
  const copyId = `copy-${randomUUID()}`;
  const now = Timestamp.now();
  await db.doc(`catalogBooks/${catalogBookId}`).set({
    title: `Local ${format} title`, normalizedTitle: "local title", authors: ["Local Writer"],
    coverUrl: `http://127.0.0.1:9199/local-cover/${catalogBookId}`, coverStoragePath: "",
    genres: [], publishedYear: 2020, pageCount: 100, description: "", format,
    binding: format === "DIGITAL" ? "eBook" : "Hardcover",
    createdAt: now, updatedAt: now,
  });
  await db.doc(`userBooks/${copyId}`).set({
    ownerId: owner.uid, catalogBookId, status: "AVAILABLE", createdAt: now, updatedAt: now,
  });
  return { catalogBookId, copyId };
}
async function eventually(check: () => Promise<void>, label: string): Promise<void> {
  const deadline = Date.now() + 60_000;
  let last: unknown;
  while (Date.now() < deadline) {
    try { await check(); return; } catch (error: unknown) { last = error; }
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  throw new Error(`${label}: ${last instanceof Error ? last.message : String(last)}`);
}
async function denied(reader: Reader, path: string): Promise<void> {
  await assert.rejects(getDocFromServer(doc(reader.db, path)), (error: unknown) =>
    error instanceof Error && "code" in error && error.code === "permission-denied");
}
async function publicBooks(viewer: Reader, ownerUid: string, collectionId?: string) {
  const gate = await getDocFromServer(doc(viewer.db, `publicLibraryState/${ownerUid}`));
  assert.equal(gate.get("published"), true);
  const path = collectionId
    ? `sharedReaders/${ownerUid}/collections/${collectionId}/books`
    : `sharedLibraries/${ownerUid}/books`;
  return getDocsFromServer(query(collection(viewer.db, path),
    where("publicationEpoch", "==", gate.get("publicationEpoch")), limit(60)));
}

test("six sharing callables use real Auth tokens, validate ownership/input, retain retry receipts, hide independent lists and keep reports private", { timeout: 180_000 }, async () => {
  const owner = await reader("Local Shelf Owner");
  const other = await reader("Local Other Reader");
  const owned = await seedBook(owner);
  const borrowed = await seedBook(other);
  const digital = await seedBook(owner, "DIGITAL");
  for (const [name, payload] of [
    ["setLibrarySharing", { libraryShared: true, libraryName: "Shelf" }],
    ["setTitleVisibility", { catalogBookId: owned.catalogBookId, hidden: true }],
    ["createCollection", { name: "List", requestId: randomUUID() }],
    ["updateCollection", { collectionId: "unknown", shared: true }],
    ["deleteCollection", { collectionId: "unknown" }],
    ["reportLibrary", { ownerUid: owner.uid, reason: "spam", details: "", requestId: randomUUID() }],
  ] satisfies [string, Fields][]) {
    await assert.rejects(invoke(name, payload), failure("UNAUTHENTICATED"));
  }
  await assert.rejects(invoke("setLibrarySharing", { libraryShared: "true", libraryName: "Shelf" }, owner), failure("INVALID_ARGUMENT"));
  await assert.rejects(invoke("createCollection", { name: " ", requestId: randomUUID() }, owner), failure("INVALID_ARGUMENT"));
  await invoke("setLibrarySharing", { libraryShared: true, libraryName: "Owner shelf" }, owner);
  await eventually(async () => { assert.equal((await publicBooks(other, owner.uid)).size, 2); }, "owned/digital library");
  // Catalogue-only membership may reference another owner's title, without
  // asserting ownership; the second test also exercises a genuinely accepted borrow.
  const requestId = randomUUID();
  const creates = await Promise.all([
    invoke("createCollection", { name: "Independent list", requestId }, other),
    invoke("createCollection", { name: "Independent list", requestId }, other),
  ]);
  const collectionId = stringField(creates[0]!, "collectionId");
  assert.equal(creates[1]?.["collectionId"], collectionId);
  await assert.rejects(invoke("createCollection", { name: "Different", requestId }, other), failure("ALREADY_EXISTS"));
  await assert.rejects(invoke("updateCollection", { collectionId, shared: true }, owner), failure("NOT_FOUND"));
  await assert.rejects(invoke("updateCollection", { collectionId, ownerUid: other.uid, shared: true }, owner), failure("INVALID_ARGUMENT"));
  await invoke("setLibrarySharing", { libraryShared: false, libraryName: "Private whole library" }, other);
  await invoke("updateCollection", { collectionId, catalogIds: [owned.catalogBookId, borrowed.catalogBookId, digital.catalogBookId], shared: true }, other);
  assert.equal((await publicBooks(owner, other.uid, collectionId)).size, 3);
  await denied(owner, `sharedLibraries/${other.uid}`);
  for (const id of [owned.catalogBookId, borrowed.catalogBookId, digital.catalogBookId]) {
    await invoke("setTitleVisibility", { catalogBookId: id, hidden: true }, other);
    await denied(owner, `sharedReaders/${other.uid}/collections/${collectionId}/books/${id}`);
  }
  const emptyList = await getDocFromServer(doc(owner.db, `sharedReaders/${other.uid}/collections/${collectionId}`));
  assert.equal(emptyList.get("bookCount"), 0);
  assert.deepEqual(emptyList.get("coverUrls"), []);
  assert.equal((await publicBooks(owner, other.uid, collectionId)).size, 0);
  assert.equal((await getDocFromServer(doc(owner.db, `sharedReaders/${other.uid}`))).get("collectionCount"), 1);
  assert.equal((await publicBooks(other, owner.uid)).size, 2, "one owner's hide never censors another owner");
  assert.equal((await db.doc(`users/${other.uid}/collections/${collectionId}`).get()).get("catalogIds").length, 3);
  const reportRequest = randomUUID();
  const reportPayload = { ownerUid: owner.uid, reason: "spam", details: "Local emulator test only", requestId: reportRequest };
  const reports = await Promise.all([invoke("reportLibrary", reportPayload, other), invoke("reportLibrary", reportPayload, other)]);
  const reportId = stringField(reports[0]!, "reportId");
  assert.equal(reports[1]?.["reportId"], reportId);
  assert.equal((await db.collection("libraryReports").where("reporterUid", "==", other.uid).get()).size, 1);
  await denied(other, `libraryReports/${reportId}`);
  await denied(other, `users/${other.uid}/reportReceipts/${reportId}`);
  await assert.rejects(invoke("reportLibrary", { ...reportPayload, requestId: randomUUID() }, other), failure("RESOURCE_EXHAUSTED"));
  await assert.rejects(invoke("reportLibrary", { ...reportPayload, reason: "invalid", requestId: randomUUID() }, other), failure("INVALID_ARGUMENT"));
  await invoke("setLibrarySharing", { libraryShared: false, libraryName: "Owner shelf" }, owner);
  assert.equal((await invoke("reportLibrary", reportPayload, other))["reportId"], reportId);
  await invoke("updateCollection", { collectionId, shared: false }, other);
  await denied(owner, `sharedReaders/${other.uid}/collections/${collectionId}`);
  assert.equal((await invoke("deleteCollection", { collectionId }, other))["deleted"], true);
  assert.equal((await invoke("deleteCollection", { collectionId }, other))["deleted"], true);
  assert.equal((await invoke("createCollection", { name: "Independent list", requestId }, other))["collectionId"], collectionId);
  assert.equal((await db.doc(`users/${other.uid}/collections/${collectionId}`).get()).exists, false);
  assert.equal((await db.doc(`users/${other.uid}/collectionRequests/${requestKey(other.uid, requestId)}`).get()).exists, true);
});

test("real two-party invitation acceptance, shared borrowed metadata, six-hour reminder guard and other-party return restore shelf outcomes", { timeout: 180_000 }, async () => {
  const lender = await reader("Local Lender");
  const borrower = await reader("Local Borrower");
  const outsider = await reader("Local Observer");
  const book = await seedBook(lender);
  await invoke("setLibrarySharing", { libraryShared: true, libraryName: "Lender shelf" }, lender);
  const invite = await invoke("createLoanInvite", {
    copyId: book.copyId, dueAtMillis: Date.now() + 14 * 86_400_000, borrowerDisplayName: "Local Borrower",
  }, lender);
  const loanId = stringField(invite, "loanId");
  const inviteCode = stringField(invite, "inviteCode");
  assert.equal(invite["format"], "PHYSICAL");
  assert.equal(invite["binding"], "Hardcover");
  const resumed = await invoke("createLoanInvite", { copyId: book.copyId, dueAtMillis: Date.now() + 3 * 86_400_000 }, lender);
  assert.equal(resumed["loanId"], loanId);
  assert.equal(resumed["resumed"], true);
  assert.equal(resumed["dueAtMillis"], invite["dueAtMillis"]);
  assert.equal(resumed["format"], "PHYSICAL");
  const resolved = await invoke("resolveLoanInvite", { inviteCode }, borrower);
  assert.equal(resolved["status"], "pending");
  assert.equal(resolved["canAccept"], true);
  assert.equal(resolved["format"], "PHYSICAL");
  assert.equal(resolved["binding"], "Hardcover");
  // Older invitation snapshots lack format. Resolve from trusted catalogue
  // metadata rather than inventing a PHYSICAL default.
  await db.doc(`loanInvites/${loanId}`).update({ "book.format": FieldValue.delete(), "book.binding": FieldValue.delete() });
  assert.equal((await invoke("resolveLoanInvite", { inviteCode }, borrower))["format"], "PHYSICAL");
  await db.doc(`catalogBooks/${book.catalogBookId}`).update({ format: "DIGITAL", binding: "eBook" });
  const digitalResolved = await invoke("resolveLoanInvite", { inviteCode }, borrower);
  assert.equal(digitalResolved["format"], "DIGITAL");
  assert.equal(digitalResolved["binding"], "eBook");
  assert.equal(digitalResolved["canAccept"], false);
  await assert.rejects(invoke("acceptLoanInvite", { loanId }, borrower), failure("FAILED_PRECONDITION"));
  await db.doc(`catalogBooks/${book.catalogBookId}`).update({ format: "PHYSICAL", binding: "Hardcover" });
  const digital = await seedBook(lender, "DIGITAL");
  await assert.rejects(invoke("createLoanInvite", { copyId: digital.copyId, dueAtMillis: Date.now() + 86_400_000 }, lender), failure("FAILED_PRECONDITION"));
  assert.equal((await db.collection("loans").where("borrowerUid", "==", borrower.uid).get()).size, 0);
  await assert.rejects(invoke("createLoanInvite", { copyId: book.copyId, dueAtMillis: Date.now() + 86_400_000 }, outsider), failure("PERMISSION_DENIED"));
  await assert.rejects(invoke("acceptLoanInvite", { loanId }, lender), failure("FAILED_PRECONDITION"));
  const accepted = await invoke("acceptLoanInvite", { loanId }, borrower);
  assert.equal(accepted["status"], "active");
  assert.equal(accepted["format"], "PHYSICAL");
  assert.equal(accepted["binding"], "Hardcover");
  assert.equal((await getDocFromServer(doc(lender.db, `loans/${loanId}`))).exists(), true);
  await denied(outsider, `loans/${loanId}`);
  assert.equal((await invoke("acceptLoanInvite", { loanId }, borrower))["loanId"], loanId);
  const acceptedSelf = await invoke("resolveLoanInvite", { inviteCode }, borrower);
  assert.equal(acceptedSelf["status"], "accepted");
  assert.equal(acceptedSelf["canAccept"], false);
  await assert.rejects(invoke("resolveLoanInvite", { inviteCode }, outsider), failure("FAILED_PRECONDITION"));
  await eventually(async () => {
    const books = await publicBooks(outsider, lender.uid);
    assert.equal(books.docs.find((entry) => entry.id === book.catalogBookId)?.get("availability"), "on_loan");
  }, "acceptance event changes availability");
  const borrowedLoans = query(collection(borrower.db, "loans"), where("participants", "array-contains", borrower.uid), where("isOpen", "==", true));
  assert.equal((await getDocsFromServer(borrowedLoans)).size, 1);
  const list = await invoke("createCollection", { name: "Borrowed references", requestId: randomUUID() }, borrower);
  const collectionId = stringField(list, "collectionId");
  await invoke("updateCollection", { collectionId, catalogIds: [book.catalogBookId], shared: true }, borrower);
  const listBooks = await publicBooks(outsider, borrower.uid, collectionId);
  assert.equal(listBooks.size, 1);
  assert.deepEqual(Object.keys(listBooks.docs[0]!.data()).sort(),
    ["addedAt", "author", "catalogBookId", "coverUrl", "format", "publicationEpoch", "title"]);
  await denied(outsider, `sharedLibraries/${borrower.uid}`);
  await invoke("setTitleVisibility", { catalogBookId: book.catalogBookId, hidden: true }, borrower);
  await denied(outsider, `sharedReaders/${borrower.uid}/collections/${collectionId}/books/${book.catalogBookId}`);
  assert.equal((await publicBooks(outsider, borrower.uid, collectionId)).size, 0);
  assert.equal((await publicBooks(outsider, lender.uid)).docs.find((entry) => entry.id === book.catalogBookId)?.get("availability"), "on_loan");
  await db.doc(`loans/${loanId}`).update({ "book.format": FieldValue.delete(), "book.binding": FieldValue.delete() });
  const reminder = await invoke("sendLoanReminder", { loanId }, lender);
  assert.equal(reminder["format"], "PHYSICAL");
  assert.equal(reminder["binding"], "Hardcover");
  const loan = await db.doc(`loans/${loanId}`).get();
  const lastReminder: unknown = loan.get("lastManualReminderAt");
  assert.ok(lastReminder instanceof Timestamp);
  assert.equal(reminder["nextReminderAllowedAtMillis"], lastReminder.toMillis() + 6 * 3_600_000);
  assert.equal(loan.get("lastManualReminderByUid"), lender.uid);
  await assert.rejects(invoke("sendLoanReminder", { loanId }, borrower), failure("RESOURCE_EXHAUSTED"));
  await assert.rejects(invoke("sendLoanReminder", { loanId }, outsider), failure("PERMISSION_DENIED"));
  const requested = await invoke("requestReturn", { loanId }, borrower);
  assert.equal(requested["status"], "return_requested");
  assert.equal(requested["format"], "PHYSICAL");
  await assert.rejects(invoke("confirmReturn", { loanId }, borrower), failure("PERMISSION_DENIED"));
  await assert.rejects(invoke("confirmReturn", { loanId }, outsider), failure("PERMISSION_DENIED"));
  const returned = await invoke("confirmReturn", { loanId }, lender);
  assert.equal(returned["status"], "returned");
  assert.equal(returned["format"], "PHYSICAL");
  // Closed-loan idempotence must not bypass participant authorization.
  await assert.rejects(invoke("confirmReturn", { loanId }, outsider), failure("PERMISSION_DENIED"));
  assert.equal((await invoke("confirmReturn", { loanId }, lender))["status"], "returned");
  assert.equal((await getDocsFromServer(borrowedLoans)).size, 0, "borrower's virtual shelf derives only from open loans");
  assert.equal((await db.doc(`userBooks/${book.copyId}`).get()).get("ownerId"), lender.uid);
  assert.equal((await db.doc(`userBooks/${book.copyId}`).get()).get("status"), "AVAILABLE");
  assert.equal((await db.doc(`lendingSlots/${lender.uid}_${book.copyId}`).get()).get("state"), "available");
  assert.equal((await db.collection("userBooks").where("ownerId", "==", borrower.uid).get()).size, 0);
  assert.deepEqual((await db.doc(`users/${borrower.uid}`).get()).get("hiddenCatalogIds"), [book.catalogBookId]);
  assert.deepEqual((await db.doc(`users/${borrower.uid}/collections/${collectionId}`).get()).get("catalogIds"), [book.catalogBookId]);
  await eventually(async () => {
    assert.equal((await publicBooks(outsider, lender.uid)).docs.find((entry) => entry.id === book.catalogBookId)?.get("availability"), "available");
  }, "return event restores availability");
  await eventually(async () => {
    const notifications = await db.collection("notifications").where("loanId", "==", loanId).get();
    assert.ok(notifications.size >= 3);
    for (const notification of notifications.docs) assert.equal(notification.get("push.status"), "no_tokens");
  }, "notifications remain local with no FCM tokens");
});

test("normal source triggers and parallel refreshes converge without exposing hidden data; missing parents deny children with an active gate", { timeout: 180_000 }, async () => {
  const owner = await reader("Local Concurrent Owner");
  const viewer = await reader("Local Concurrent Viewer");
  const first = await seedBook(owner);
  const hidden = await seedBook(owner, "DIGITAL");
  await invoke("setLibrarySharing", { libraryShared: true, libraryName: "Concurrent shelf" }, owner);
  const settled = await Promise.allSettled([
    refreshPublication(db, owner.uid),
    refreshPublication(db, owner.uid),
    db.doc(`catalogBooks/${first.catalogBookId}`).update({ title: "Current source title", updatedAt: Timestamp.now() }),
    invoke("setTitleVisibility", { catalogBookId: hidden.catalogBookId, hidden: true }, owner),
  ]);
  assert.equal(settled[2]?.status, "fulfilled");
  assert.equal(settled[3]?.status, "fulfilled");
  for (const outcome of settled.slice(0, 2)) {
    if (outcome.status === "rejected") assert.match(String(outcome.reason), /Publication changed while rebuilding/u);
  }
  await eventually(async () => {
    const books = await publicBooks(viewer, owner.uid);
    assert.equal(books.size, 1);
    assert.equal(books.docs[0]?.get("title"), "Current source title");
    assert.equal((await getDocFromServer(doc(viewer.db, `sharedLibraries/${owner.uid}`))).get("bookCount"), 1);
  }, "concurrent events publish latest safe source");
  await denied(viewer, `sharedLibraries/${owner.uid}/books/${hidden.catalogBookId}`);
  const added = await seedBook(owner);
  await eventually(async () => { assert.equal((await publicBooks(viewer, owner.uid)).size, 2); }, "copy-add trigger");
  await db.doc(`userBooks/${added.copyId}`).delete();
  await eventually(async () => { assert.equal((await publicBooks(viewer, owner.uid)).size, 1); }, "copy-removal trigger");
  assert.equal((await db.doc(`publicLibraryState/${owner.uid}`).get()).get("published"), true);
  await db.doc(`sharedLibraries/${owner.uid}`).delete();
  await denied(viewer, `sharedLibraries/${owner.uid}/books/${first.catalogBookId}`);
  assert.equal((await db.doc(`publicLibraryState/${owner.uid}`).get()).get("published"), true);
  const created = await invoke("createCollection", { name: "Orphan coverage", requestId: randomUUID() }, owner);
  const collectionId = stringField(created, "collectionId");
  await invoke("updateCollection", { collectionId, catalogIds: [first.catalogBookId], shared: true }, owner);
  const parentPath = `sharedReaders/${owner.uid}/collections/${collectionId}`;
  const childPath = `${parentPath}/books/${first.catalogBookId}`;
  const savedParent = (await db.doc(parentPath).get()).data();
  assert.ok(savedParent);
  assert.equal((await getDocFromServer(doc(viewer.db, childPath))).exists(), true);
  await db.doc(parentPath).delete();
  await denied(viewer, childPath);
  assert.equal((await db.doc(`publicLibraryState/${owner.uid}`).get()).get("published"), true);
  assert.equal((await db.doc(`sharedReaders/${owner.uid}`).get()).get("published"), true);
  await db.doc(parentPath).set(savedParent);
  assert.equal((await getDocFromServer(doc(viewer.db, childPath))).exists(), true);
  await db.doc(`sharedReaders/${owner.uid}`).delete();
  await denied(viewer, parentPath);
  await denied(viewer, childPath);
  assert.equal((await db.doc(`publicLibraryState/${owner.uid}`).get()).get("published"), true);
});
