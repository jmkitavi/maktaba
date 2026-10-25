import assert from "node:assert/strict";
import { after, before, beforeEach, test } from "node:test";
import { readFile } from "node:fs/promises";
import { initializeTestEnvironment, assertFails, assertSucceeds, type RulesTestEnvironment } from "@firebase/rules-unit-testing";
import {
  collection, doc, getDoc, getDocs, limit, orderBy, query, setDoc, startAfter, updateDoc, where,
  Timestamp as ClientTimestamp,
} from "firebase/firestore";
import { initializeApp, deleteApp, type App } from "firebase-admin/app";
import { getFirestore, Timestamp, type Firestore } from "firebase-admin/firestore";
import {
  changePrivateCollection, createPrivateCollection, mutateSettings, rebuildPublication,
  readSource, refreshPublication, revokeInTransaction, submitLibraryReport,
} from "../../sharingService";
import { sourceHash } from "../../lib/sharing";

const projectId = "demo-maktaba-sharing-rules";
let environment: RulesTestEnvironment;
let app: App;
let db: Firestore;
const owner = "owner-reader";
const other = "other-reader";
before(async () => {
  const host = process.env["FIRESTORE_EMULATOR_HOST"];
  if (!host || !/^(127\.0\.0\.1|localhost):\d+$/u.test(host)) {
    throw new Error("Set FIRESTORE_EMULATOR_HOST to an explicitly local Firestore emulator; live access is forbidden.");
  }
  const [hostname, port] = host.split(":");
  environment = await initializeTestEnvironment({
    projectId,
    firestore: {
      host: hostname, port: Number(port),
      rules: await readFile("../firestore.rules", "utf8"),
    },
  });
  app = initializeApp({ projectId }, "sharing-rules");
  db = getFirestore(app);
});
beforeEach(async () => { await environment.clearFirestore(); });
after(async () => {
  if (environment) await environment.cleanup();
  if (app) await deleteApp(app);
});
async function seedOwner(uid = owner) {
  await db.doc(`users/${uid}`).set({
    displayName: uid, email: null, photoUrl: null, notificationsEnabled: true,
    createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
  });
}
async function seedBook(id = "title", uid = owner, copy = `${uid}-${id}`) {
  await db.doc(`catalogBooks/${id}`).set({ title: id, authors: ["Writer"], coverUrl: `https://cover/${id}`, format: "PHYSICAL" });
  await db.doc(`userBooks/${copy}`).set({
    ownerId: uid, catalogBookId: id, status: "AVAILABLE", createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
  });
}
async function share(uid = owner) {
  return mutateSettings(db, uid, () => ({ libraryShared: true, libraryName: `${uid}'s Shelf` }));
}
test("owned-copy edits persist privately, reject malformed/borrowed edits and never change public catalogue metadata", async () => {
  await seedOwner();
  await seedBook();
  await share();
  const self = environment.authenticatedContext(owner).firestore();
  const reader = environment.authenticatedContext(other).firestore();
  const path = `userBooks/${owner}-title`;
  const privateDetails = {
    title: "My private title", author: "My author", binding: "Paperback",
    isbn13: "9780306406157", isbn10: "", publisher: "My publisher",
    published: "2026", genre: "Fiction", pages: 123, description: "My notes",
  };
  await assertSucceeds(updateDoc(doc(self, path), { privateDetails, updatedAt: ClientTimestamp.now() }));
  assert.deepEqual((await getDoc(doc(self, path))).get("privateDetails"), privateDetails);
  await assertFails(getDoc(doc(reader, path)));
  await assertFails(updateDoc(doc(reader, path), { privateDetails, updatedAt: ClientTimestamp.now() }));
  for (const invalid of [
    { ...privateDetails, title: "" }, { ...privateDetails, pages: -1 },
    { ...privateDetails, pages: 100001 }, { ...privateDetails, pages: "123" },
    { ...privateDetails, isbn13: "malformed" }, { ...privateDetails, ownerId: other },
    { ...privateDetails, description: "x".repeat(20001) },
  ]) {
    await assertFails(updateDoc(doc(self, path), { privateDetails: invalid, updatedAt: ClientTimestamp.now() }));
  }
  await assertFails(updateDoc(doc(self, path), { ownerId: other }));
  await assertFails(updateDoc(doc(self, path), { catalogBookId: "another-title" }));
  await refreshPublication(db, owner);
  assert.equal((await getDoc(doc(reader, `sharedLibraries/${owner}/books/title`))).get("title"), "title");
  assert.equal((await db.doc("catalogBooks/title").get()).get("title"), "title");
});
test("default-off legacy accounts remain private; owner data and server sharing fields stay protected", async () => {
  await seedOwner();
  await seedBook();
  await refreshPublication(db, owner);
  assert.equal((await db.doc(`sharedLibraries/${owner}`).get()).exists, false);
  const self = environment.authenticatedContext(owner).firestore();
  const reader = environment.authenticatedContext(other).firestore();
  const anonymous = environment.unauthenticatedContext().firestore();
  await assertSucceeds(getDoc(doc(self, `users/${owner}`)));
  await assertFails(getDoc(doc(reader, `users/${owner}`)));
  await assertFails(getDoc(doc(reader, `userBooks/${owner}-title`)));
  await assertFails(getDoc(doc(anonymous, `userBooks/${owner}-title`)));
  await assertSucceeds(updateDoc(doc(self, `users/${owner}`), { displayName: "New Name" }));
  await assertFails(updateDoc(doc(self, `users/${owner}`), { libraryShared: true }));
  await assertFails(setDoc(doc(reader, `sharedLibraries/${owner}`), { published: true }));
  await share();
  await assertSucceeds(updateDoc(doc(self, `users/${owner}`), { displayName: "Legacy update still works" }));
  await assertFails(updateDoc(doc(self, `users/${owner}`), { hiddenCatalogIds: [] }));
  await assertFails(updateDoc(doc(self, `users/${owner}`), { publicationEpoch: 42 }));
  // Profiles created by the existing callable use photoURL/preferences instead
  // of the optional legacy client-only photoUrl/notificationsEnabled fields.
  await db.doc(`users/${owner}`).set({
    uid: owner, displayName: "Server-created profile", email: null, photoURL: null,
    notificationPreferences: { pushEnabled: true }, searchableDisplayName: "server-created profile",
    createdAt: Timestamp.now(), updatedAt: Timestamp.now(), lastSeenAt: Timestamp.now(),
    libraryShared: true, libraryName: "Shelf", hiddenCatalogIds: [], publicationEpoch: 1,
  });
  await assertSucceeds(updateDoc(doc(self, `users/${owner}`), { displayName: "Compatible update" }));
  await assertFails(updateDoc(doc(self, `users/${owner}`), { notificationPreferences: { pushEnabled: false } }));
  await assertFails(getDoc(doc(anonymous, `sharedLibraries/${owner}`)));
  const safe = (await getDoc(doc(reader, `sharedLibraries/${owner}`))).data();
  assert.ok(safe);
  assert.ok(!("email" in safe));
  assert.ok(!("hiddenCatalogIds" in (await getDoc(doc(reader, `publicLibraryState/${owner}`))).data()!));
});
test("directory uses stored published constraints and paginates more than ten owners; prefix query and bounded shelf pages", async () => {
  const batch = db.batch();
  for (let index = 0; index < 25; index++) {
    const uid = `reader-${index}`;
    batch.set(db.doc(`sharedLibraries/${uid}`), {
      ownerUid: uid, published: true, publicationEpoch: 1, updatedAt: Timestamp.fromMillis(index),
      searchPrefixes: ["read", uid], libraryName: uid,
    });
    batch.set(db.doc(`publicLibraryState/${uid}`), { published: true, publicationEpoch: 1, updatedAt: Timestamp.now() });
  }
  batch.set(db.doc("sharedLibraries/private"), { published: false, updatedAt: Timestamp.now() });
  await batch.commit();
  const reader = environment.authenticatedContext(other).firestore();
  const directory = collection(reader, "sharedLibraries");
  const first = await assertSucceeds(getDocs(query(directory, where("published", "==", true), orderBy("updatedAt", "desc"), limit(20))));
  assert.equal(first.size, 20);
  const second = await assertSucceeds(getDocs(query(directory, where("published", "==", true), orderBy("updatedAt", "desc"),
    startAfter(first.docs[19]!), limit(20))));
  assert.equal(second.size, 5);
  const searched = await assertSucceeds(getDocs(query(directory, where("published", "==", true),
    where("searchPrefixes", "array-contains", "read"), orderBy("updatedAt", "desc"), limit(20))));
  assert.equal(searched.size, 20);
  await assertFails(getDocs(query(directory, limit(20))));
  await assertFails(getDocs(query(directory, where("published", "==", true), limit(21))));
  await assertFails(getDocs(query(directory, where("published", "==", true))));
  await seedOwner();
  const books = db.batch();
  for (let index = 0; index < 65; index++) {
    books.set(db.doc(`catalogBooks/book-${index}`), { title: `Book ${index}`, format: "PHYSICAL" });
    books.set(db.doc(`userBooks/copy-${index}`), { ownerId: owner, catalogBookId: `book-${index}`, createdAt: Timestamp.fromMillis(index) });
  }
  await books.commit();
  const state = await share();
  const shelf = collection(reader, `sharedLibraries/${owner}/books`);
  const page = await assertSucceeds(getDocs(query(shelf, where("publicationEpoch", "==", state.publicationEpoch),
    orderBy("addedAt", "desc"), limit(60))));
  assert.equal(page.size, 60);
  const tail = await assertSucceeds(getDocs(query(shelf, where("publicationEpoch", "==", state.publicationEpoch),
    orderBy("addedAt", "desc"), startAfter(page.docs[59]!), limit(60))));
  assert.equal(tail.size, 5);
  await assertFails(getDocs(query(shelf, limit(60))));
  await assertFails(getDocs(query(shelf, where("publicationEpoch", "==", state.publicationEpoch), limit(61))));
});
test("global hide revokes all owner views immediately, rebuild filters counts/covers; other owners and private membership unaffected", async () => {
  await seedOwner();
  await seedOwner(other);
  await seedBook();
  await seedBook("title", other);
  const listId = await createPrivateCollection(db, owner, "My list", "create-once");
  await changePrivateCollection(db, owner, listId, { catalogIds: ["title"], shared: true });
  const state = await share();
  await share(other);
  const reader = environment.authenticatedContext("viewer").firestore();
  const sharedBook = doc(reader, `sharedReaders/${owner}/collections/${listId}/books/title`);
  await assertSucceeds(getDoc(sharedBook));
  // Inspect the bounded revocation independently from the subsequent async build.
  const nextEpoch = state.publicationEpoch + 1;
  await db.runTransaction(async (tx) => {
    tx.update(db.doc(`users/${owner}`), { hiddenCatalogIds: ["title"], publicationEpoch: nextEpoch });
    revokeInTransaction(db, tx, owner, nextEpoch);
  });
  for (const path of [
    `sharedLibraries/${owner}`, `sharedLibraries/${owner}/books/title`,
    `sharedReaders/${owner}`, `sharedReaders/${owner}/collections/${listId}`,
    `sharedReaders/${owner}/collections/${listId}/books/title`,
  ]) await assertFails(getDoc(doc(reader, path)));
  assert.deepEqual((await db.doc(`sharedLibraries/${owner}`).get()).get("coverUrls"), undefined);
  assert.equal(await rebuildPublication(db, owner, state.publicationEpoch), false);
  assert.equal(await rebuildPublication(db, owner, nextEpoch), true);
  await assertFails(getDoc(sharedBook));
  await assertFails(getDoc(doc(reader, `sharedLibraries/${owner}/books/title`)));
  assert.equal((await getDoc(doc(reader, `sharedLibraries/${owner}`))).get("bookCount"), 0);
  const list = await getDoc(doc(reader, `sharedReaders/${owner}/collections/${listId}`));
  assert.equal(list.get("bookCount"), 0);
  assert.deepEqual(list.get("coverUrls"), []);
  assert.deepEqual((await db.doc(`users/${owner}/collections/${listId}`).get()).get("catalogIds"), ["title"]);
  await assertSucceeds(getDoc(doc(reader, `sharedLibraries/${other}/books/title`)));
  // Duplicate/out-of-order events recompute latest state, never resurrect exclusions.
  await refreshPublication(db, owner);
  await refreshPublication(db, owner);
  assert.equal((await db.doc(`users/${owner}`).get()).get("publicationEpoch"), nextEpoch);
  await db.doc(`userBooks/${owner}-title`).delete();
  await refreshPublication(db, owner);
  await assertFails(getDoc(sharedBook));
  assert.deepEqual((await db.doc(`users/${owner}`).get()).get("hiddenCatalogIds"), ["title"]);
});
test("collection CRUD is private and idempotent, shared lists work with library off, per-list off/delete and orphan descendants fail closed", async () => {
  await seedOwner();
  await seedBook();
  const listId = await createPrivateCollection(db, owner, "First", "stable-create");
  assert.equal(await createPrivateCollection(db, owner, "First", "stable-create"), listId);
  await assert.rejects(createPrivateCollection(db, owner, "Different", "stable-create"), { code: "already-exists" });
  const self = environment.authenticatedContext(owner).firestore();
  const reader = environment.authenticatedContext(other).firestore();
  const listPath = `users/${owner}/collections/${listId}`;
  await assertSucceeds(getDoc(doc(self, listPath)));
  await assertFails(getDoc(doc(reader, listPath)));
  await assertFails(updateDoc(doc(self, listPath), { shared: true }));
  await assert.rejects(changePrivateCollection(db, other, listId, { name: "Steal" }));
  await changePrivateCollection(db, owner, listId, { name: "Renamed", catalogIds: ["title"], shared: true });
  const epoch = (await db.doc(`publicLibraryState/${owner}`).get()).get("publicationEpoch");
  assert.equal(typeof epoch, "number");
  const publicLists = collection(reader, `sharedReaders/${owner}/collections`);
  assert.equal((await assertSucceeds(getDocs(query(publicLists, where("published", "==", true),
    where("publicationEpoch", "==", epoch), orderBy("updatedAt", "desc"), limit(60))))).size, 1);
  await assertFails(getDocs(query(publicLists, where("published", "==", true), limit(60))));
  const publicBooks = collection(reader, `sharedReaders/${owner}/collections/${listId}/books`);
  assert.equal((await assertSucceeds(getDocs(query(publicBooks, where("publicationEpoch", "==", epoch), limit(60))))).size, 1);
  const metadata = (await getDoc(doc(publicBooks, "title"))).data()!;
  assert.ok(!("availability" in metadata));
  assert.ok(!("ownerId" in metadata));
  await assertFails(getDoc(doc(reader, `sharedLibraries/${owner}`)));
  await changePrivateCollection(db, owner, listId, { shared: false });
  await assertFails(getDoc(doc(publicBooks, "title")));
  await assertFails(getDoc(doc(reader, `sharedReaders/${owner}/collections/${listId}`)));
  await changePrivateCollection(db, owner, listId, { shared: true });
  await changePrivateCollection(db, owner, listId, null);
  await changePrivateCollection(db, owner, listId, null);
  await assertFails(getDoc(doc(publicBooks, "title")));
  assert.equal(await createPrivateCollection(db, owner, "First", "stable-create"), listId);
  assert.equal((await db.doc(listPath).get()).exists, false, "retries never recreate deleted committed collections");
  // Existing private Want to Read storage and owner-only access are unchanged.
  await assertSucceeds(setDoc(doc(self, `users/${owner}/wishlist/title`), { addedAt: ClientTimestamp.now() }));
  await assertFails(getDoc(doc(reader, `users/${owner}/wishlist/title`)));
});
test("off and stale/out-of-order builders cannot reopen a public library, including orphan direct gets", async () => {
  await seedOwner();
  await seedBook();
  const initial = await share();
  await mutateSettings(db, owner, () => ({ libraryShared: false, libraryName: "Private" }));
  assert.equal(await rebuildPublication(db, owner, initial.publicationEpoch), false);
  await refreshPublication(db, owner);
  const reader = environment.authenticatedContext(other).firestore();
  await assertFails(getDoc(doc(reader, `sharedLibraries/${owner}/books/title`)));
  await db.doc(`sharedLibraries/${owner}`).delete();
  await assertFails(getDoc(doc(reader, `sharedLibraries/${owner}/books/title`)));
});
test("copy, loan, profile and catalogue refreshes publish current sanitized metadata and never borrowed ownership", async () => {
  await seedOwner();
  await seedOwner(other);
  await seedBook();
  await seedBook("title", owner, "duplicate-copy");
  await seedBook("digital", owner);
  await db.doc("catalogBooks/digital").update({ format: "DIGITAL" });
  await seedBook("borrowed-title", other);
  await db.doc("loans/incoming").set({
    lenderUid: other, borrowerUid: owner, copyId: `${other}-borrowed-title`,
    catalogBookId: "borrowed-title", isOpen: true, status: "active",
  });
  await share();
  const reader = environment.authenticatedContext("viewer").firestore();
  const title = doc(reader, `sharedLibraries/${owner}/books/title`);
  await db.doc("loans/outgoing-a").set({
    lenderUid: owner, borrowerUid: other, copyId: `${owner}-title`,
    catalogBookId: "title", isOpen: true, status: "active", dueDate: Timestamp.now(),
  });
  await refreshPublication(db, owner);
  assert.equal((await getDoc(title)).get("availability"), "available");
  await db.doc("loans/outgoing-b").set({
    lenderUid: owner, borrowerUid: other, copyId: "duplicate-copy",
    catalogBookId: "title", isOpen: true, status: "return_requested",
  });
  await refreshPublication(db, owner);
  assert.equal((await getDoc(title)).get("availability"), "on_loan");
  const publicData = (await getDoc(title)).data()!;
  for (const privateField of ["borrowerUid", "lenderUid", "copyId", "dueDate", "loanId"]) assert.ok(!(privateField in publicData));
  await db.doc("loans/outgoing-a").update({ status: "returned", isOpen: false });
  await refreshPublication(db, owner);
  assert.equal((await getDoc(title)).get("availability"), "available");
  assert.equal((await getDoc(doc(reader, `sharedLibraries/${owner}/books/digital`))).get("format"), "DIGITAL");
  assert.equal((await getDoc(doc(reader, `sharedLibraries/${owner}`))).get("bookCount"), 2);
  await assertFails(getDoc(doc(reader, `sharedLibraries/${owner}/books/borrowed-title`)));
  await db.doc(`users/${owner}`).update({ displayName: "Café Reader", photoURL: "https://avatar" });
  await db.doc("catalogBooks/title").update({ title: "Current catalogue title", coverUrl: "https://current-cover" });
  await refreshPublication(db, owner);
  assert.equal((await getDoc(title)).get("title"), "Current catalogue title");
  assert.equal((await getDoc(doc(reader, `sharedLibraries/${owner}`))).get("ownerDisplayName"), "Café Reader");
  assert.ok((await getDoc(doc(reader, `sharedLibraries/${owner}`))).get("searchPrefixes").includes("cafe"));
  await db.doc("userBooks/duplicate-copy").delete();
  await db.doc(`userBooks/${owner}-title`).delete();
  await refreshPublication(db, owner);
  await assertFails(getDoc(title));
});
test("source changes during a staged build are fenced and retried at a new epoch", async () => {
  await seedOwner();
  await seedBook();
  await share();
  await db.runTransaction(async (tx) => {
    tx.update(db.doc(`users/${owner}`), { publicationEpoch: 2 });
    revokeInTransaction(db, tx, owner, 2);
  });
  await db.runTransaction(async (tx) => {
    const staged = await readSource(db, tx, owner);
    tx.update(db.doc(`publicationJobs/${owner}`), { buildingFingerprint: sourceHash(staged) });
  });
  // The source changes before its event is handled; an old in-progress build
  // must not publish at the same epoch using its outdated snapshot.
  await db.doc(`userBooks/${owner}-title`).delete();
  assert.equal(await rebuildPublication(db, owner, 2), false);
  await refreshPublication(db, owner);
  assert.equal((await db.doc(`users/${owner}`).get()).get("publicationEpoch"), 3);
  assert.equal(await rebuildPublication(db, owner, 2), false);
  assert.equal((await db.doc(`publicLibraryState/${owner}`).get()).get("published"), true);
  assert.equal((await db.doc(`sharedLibraries/${owner}`).get()).get("bookCount"), 0);
});
test("reports use private stable receipts; retries bypass limits without duplicating reports, new submissions are limited", async () => {
  await seedOwner();
  await share();
  const reportId = await submitLibraryReport(db, other, owner, "spam", "Details", "stable-report");
  assert.equal(await submitLibraryReport(db, other, owner, "spam", "Details", "stable-report"), reportId);
  assert.equal((await db.collection("libraryReports").get()).size, 1);
  await assert.rejects(submitLibraryReport(db, other, owner, "other", "", "stable-report"), { code: "already-exists" });
  await assert.rejects(submitLibraryReport(db, other, owner, "spam", "", "new-report"), { code: "resource-exhausted" });
  await assert.rejects(submitLibraryReport(db, owner, owner, "spam", "", "self-report"), { code: "invalid-argument" });
  await db.doc(`libraryReportLimits/${other}`).update({
    count: 5, lastReportAt: Timestamp.fromMillis(Date.now() - 61_000), windowStartedAt: Timestamp.now(),
  });
  await assert.rejects(submitLibraryReport(db, other, owner, "spam", "", "daily-limit"), { code: "resource-exhausted" });
  await mutateSettings(db, owner, () => ({ libraryShared: false }));
  assert.equal(await submitLibraryReport(db, other, owner, "spam", "Details", "stable-report"), reportId);
  const reader = environment.authenticatedContext(other).firestore();
  for (const path of [`libraryReports/${reportId}`, `users/${other}/reportReceipts/${reportId}`, `libraryReportLimits/${other}`]) {
    await assertFails(getDoc(doc(reader, path)));
    await assertFails(setDoc(doc(reader, path), { reason: "spam" }));
  }
});
