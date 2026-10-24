import { Timestamp, type Firestore, type Transaction, type DocumentReference } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import {
  buildPublication, settings, sourceHash, strings, requestKey, searchPrefixes,
  type Fields, type PublicationSource, type SharingSettings, type CollectionChanges, type ReportReason,
} from "./lib/sharing";

function profileRef(db: Firestore, uid: string) { return db.doc(`users/${uid}`); }
function jobRef(db: Firestore, uid: string) { return db.doc(`publicationJobs/${uid}`); }
function publicRefs(db: Firestore, uid: string) {
  return {
    gate: db.doc(`publicLibraryState/${uid}`),
    library: db.doc(`sharedLibraries/${uid}`),
    reader: db.doc(`sharedReaders/${uid}`),
  };
}
function epochOf(data: Fields): number { return settings(data).publicationEpoch; }

/** Constant-size revocation: old descendants are denied by their gate/epoch. */
export function revokeInTransaction(db: Firestore, tx: Transaction, uid: string, epoch: number): void {
  const refs = publicRefs(db, uid);
  const updatedAt = Timestamp.now();
  tx.set(refs.gate, { published: false, publicationEpoch: epoch, updatedAt });
  // Replace, do not merge: no old counts, covers or names survive the barrier.
  tx.set(refs.library, { ownerUid: uid, published: false, publicationEpoch: epoch, updatedAt });
  tx.set(refs.reader, { ownerUid: uid, published: false, publicationEpoch: epoch, updatedAt });
  tx.set(jobRef(db, uid), { publicationEpoch: epoch, pending: true, updatedAt });
}

export async function readSource(db: Firestore, tx: Transaction, uid: string): Promise<PublicationSource> {
  const [profile, copies, loans, collections] = await Promise.all([
    tx.get(profileRef(db, uid)),
    tx.get(db.collection("userBooks").where("ownerId", "==", uid)),
    tx.get(db.collection("loans").where("lenderUid", "==", uid)),
    tx.get(db.collection(`users/${uid}/collections`)),
  ]);
  const ids = new Set<string>();
  for (const copy of copies.docs) {
    const data: Fields = copy.data();
    if (typeof data["catalogBookId"] === "string") ids.add(data["catalogBookId"]);
  }
  for (const collection of collections.docs) {
    const data: Fields = collection.data();
    for (const id of strings(data["catalogIds"])) ids.add(id);
  }
  const catalog = ids.size ? await tx.getAll(...[...ids].sort().map((id) => db.doc(`catalogBooks/${id}`))) : [];
  return {
    profile: profile.data() ?? {},
    copies: copies.docs.map((doc) => ({ id: doc.id, data: doc.data() })),
    loans: loans.docs.map((doc) => ({ id: doc.id, data: doc.data() })),
    collections: collections.docs.map((doc) => ({ id: doc.id, data: doc.data() })),
    catalog: catalog.filter((doc) => doc.exists).map((doc) => ({ id: doc.id, data: doc.data() ?? {} })),
  };
}

export function epochMatches(profile: Fields, epoch: number): boolean {
  return epochOf(profile) === epoch;
}

export function isPublicationContention(error: unknown): boolean {
  return error instanceof Error && "code" in error &&
    (error.code === 10 || (error.code === 3 && /Transaction is invalid or closed/u.test(error.message)));
}

/**
 * Every staged chunk and final publish is epoch-fenced. Final source comparison
 * also fences source changes whose Firestore event has not arrived yet.
 */
export async function rebuildPublication(db: Firestore, uid: string, epoch: number): Promise<boolean> {
  const stagedSource = await db.runTransaction(async (tx) => {
    const source = await readSource(db, tx, uid);
    const job = await tx.get(jobRef(db, uid));
    if (!epochMatches(source.profile, epoch)) return null;
    if (job.get("pending") === false) return "complete";
    const fingerprint = sourceHash(source);
    const building: unknown = job.get("buildingFingerprint");
    if (typeof building === "string" && building !== fingerprint) return null;
    tx.set(job.ref, { buildingFingerprint: fingerprint }, { merge: true });
    return source;
  });
  if (stagedSource === "complete") return true;
  if (stagedSource === null) return false;
  const source = stagedSource;
  const fingerprint = sourceHash(source);
  const projection = buildPublication(source, epoch);
  const refs = publicRefs(db, uid);
  const updatedAt = Timestamp.now();
  const writes: { ref: DocumentReference; data: object }[] = [];
  for (const book of projection.libraryBooks) {
    writes.push({ ref: refs.library.collection("books").doc(book.catalogBookId), data: book });
  }
  for (const collection of projection.collections) {
    const parent = refs.reader.collection("collections").doc(collection.collectionId);
    writes.push({ ref: parent, data: {
      ownerUid: uid, name: collection.name, bookCount: collection.books.length,
      coverUrls: collection.books.map((book) => book.coverUrl).filter(Boolean).slice(0, 4),
      updatedAt, published: true, publicationEpoch: epoch,
    } });
    for (const book of collection.books) {
      writes.push({ ref: parent.collection("books").doc(book.catalogBookId), data: book });
    }
  }
  for (let offset = 0; offset < writes.length; offset += 350) {
    const staged = await db.runTransaction(async (tx) => {
      const current = await tx.get(profileRef(db, uid));
      const job = await tx.get(jobRef(db, uid));
      if (!epochMatches(current.data() ?? {}, epoch) || job.get("pending") !== true ||
          job.get("buildingFingerprint") !== fingerprint) return false;
      for (const write of writes.slice(offset, offset + 350)) tx.set(write.ref, write.data);
      return true;
    });
    if (!staged) return false;
  }
  return db.runTransaction(async (tx) => {
    const current = await readSource(db, tx, uid);
    const job = await tx.get(jobRef(db, uid));
    if (!epochMatches(current.profile, epoch) || sourceHash(current) !== fingerprint) return false;
    if (job.get("pending") === false) return true;
    if (job.get("buildingFingerprint") !== fingerprint) return false;
    const state = settings(current.profile);
    const libraryPublished = state.libraryShared && current.profile["librarySharingBlocked"] !== true;
    const readerPublished = libraryPublished || projection.collections.length > 0;
    const ownerDisplayName = typeof current.profile["displayName"] === "string" ? current.profile["displayName"] : "";
    const photo = current.profile["photoUrl"] ?? current.profile["photoURL"];
    const ownerPhotoUrl = typeof photo === "string" ? photo : null;
    if (libraryPublished) tx.set(refs.library, {
      ownerUid: uid, libraryName: state.libraryName, ownerDisplayName, ownerPhotoUrl,
      bookCount: projection.libraryBooks.length,
      coverUrls: projection.libraryBooks.map((book) => book.coverUrl).filter(Boolean).slice(0, 4),
      searchPrefixes: searchPrefixes(state.libraryName, ownerDisplayName),
      updatedAt, published: true, publicationEpoch: epoch,
    });
    tx.set(refs.reader, {
      ownerUid: uid, ownerDisplayName, ownerPhotoUrl, collectionCount: projection.collections.length,
      updatedAt, published: readerPublished, publicationEpoch: epoch,
    });
    tx.set(refs.gate, { published: readerPublished, publicationEpoch: epoch, updatedAt });
    tx.set(jobRef(db, uid), {
      publicationEpoch: epoch, pending: false, fingerprint, updatedAt,
    });
    return true;
  });
}

/** Recomputes current state, never trusting an event's historical payload. */
export async function refreshPublication(db: Firestore, uid: string): Promise<void> {
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const epoch = await db.runTransaction(async (tx) => {
        const source = await readSource(db, tx, uid);
        const job = await tx.get(jobRef(db, uid));
        const jobData: Fields = job.data() ?? {};
        const state = settings(source.profile);
        if (!source.profile["libraryShared"] && !source.collections.some(({ data }) => data["shared"] === true) &&
            !job.exists) return null; // Legacy/default-off users never get opted in/backfilled.
        if (jobData["pending"] === false && jobData["fingerprint"] === sourceHash(source)) return null;
        if (jobData["pending"] === true && jobData["publicationEpoch"] === state.publicationEpoch &&
            (typeof jobData["buildingFingerprint"] !== "string" || jobData["buildingFingerprint"] === sourceHash(source))) {
          return state.publicationEpoch;
        }
        const next = state.publicationEpoch + 1;
        tx.set(profileRef(db, uid), { publicationEpoch: next }, { merge: true });
        revokeInTransaction(db, tx, uid, next);
        return next;
      });
      if (epoch === null || await rebuildPublication(db, uid, epoch)) return;
    } catch (error: unknown) {
      if (!isPublicationContention(error) || attempt === 2) throw error;
      await new Promise((resolve) => setTimeout(resolve, 25 * (attempt + 1)));
    }
  }
  // Sustained churn remains fail-closed and uses the durable event retry.
  throw new Error("Publication changed while rebuilding; retry current authoritative state.");
}

type SettingsMutation = (profile: Fields) => Fields;
export async function mutateSettings(db: Firestore, uid: string, mutate: SettingsMutation): Promise<SharingSettings> {
  const result = await db.runTransaction(async (tx) => {
    const snapshot = await tx.get(profileRef(db, uid));
    if (!snapshot.exists) throw new HttpsError("failed-precondition", "Finish setting up your profile first.");
    const profile: Fields = snapshot.data() ?? {};
    const changes = mutate(profile);
    const changed = Object.entries(changes).some(([key, value]) => JSON.stringify(profile[key]) !== JSON.stringify(value));
    if (!changed) return settings(profile);
    const epoch = epochOf(profile) + 1;
    const next = { ...profile, ...changes, publicationEpoch: epoch };
    tx.set(snapshot.ref, { ...changes, publicationEpoch: epoch, updatedAt: Timestamp.now() }, { merge: true });
    revokeInTransaction(db, tx, uid, epoch);
    return settings(next);
  });
  if (!await rebuildPublication(db, uid, result.publicationEpoch)) await refreshPublication(db, uid);
  return settings((await profileRef(db, uid).get()).data() ?? {});
}

export async function createPrivateCollection(db: Firestore, uid: string, name: string, requestId: string): Promise<string> {
  const key = requestKey(uid, requestId);
  const receipt = db.doc(`users/${uid}/collectionRequests/${key}`);
  const collection = db.doc(`users/${uid}/collections/${key}`);
  return db.runTransaction(async (tx) => {
    const [profile, previous] = await Promise.all([tx.get(profileRef(db, uid)), tx.get(receipt)]);
    if (!profile.exists) throw new HttpsError("failed-precondition", "Finish setting up your profile first.");
    if (previous.exists) {
      if (previous.get("name") !== name) throw new HttpsError("already-exists", "This request ID was used for another collection.");
      return key;
    }
    const now = Timestamp.now();
    tx.create(collection, { name, catalogIds: [], shared: false, createdAt: now, updatedAt: now });
    tx.create(receipt, { collectionId: key, name, createdAt: now });
    return key;
  });
}

export async function changePrivateCollection(
  db: Firestore, uid: string, collectionId: string, changes: CollectionChanges | null,
): Promise<void> {
  const epoch = await db.runTransaction(async (tx) => {
    const ref = db.doc(`users/${uid}/collections/${collectionId}`);
    const [profile, collection] = await Promise.all([tx.get(profileRef(db, uid)), tx.get(ref)]);
    if (!profile.exists) throw new HttpsError("failed-precondition", "Finish setting up your profile first.");
    if (!collection.exists) {
      if (changes === null) return null; // Retried committed delete.
      throw new HttpsError("not-found", "This collection no longer exists.");
    }
    const current: Fields = collection.data() ?? {};
    if (changes && Object.entries(changes).every(([key, value]) => JSON.stringify(current[key]) === JSON.stringify(value))) return null;
    if (changes?.catalogIds) {
      const ids = changes.catalogIds;
      const catalogue = ids.length ? await tx.getAll(...ids.map((id) => db.doc(`catalogBooks/${id}`))) : [];
      if (catalogue.some((doc) => !doc.exists)) throw new HttpsError("invalid-argument", "A selected catalogue title no longer exists.");
    }
    if (changes?.shared === true && profile.get("librarySharingBlocked") === true) {
      throw new HttpsError("failed-precondition", "Sharing is unavailable for this account.");
    }
    const next = epochOf(profile.data() ?? {}) + 1;
    if (changes === null) tx.delete(ref);
    else tx.update(ref, { ...changes, updatedAt: Timestamp.now() });
    tx.set(profile.ref, { publicationEpoch: next }, { merge: true });
    // Explicitly deactivate removed/off parents too (independently of the owner gate).
    tx.set(db.doc(`sharedReaders/${uid}/collections/${collectionId}`), {
      ownerUid: uid, published: false, publicationEpoch: next, updatedAt: Timestamp.now(),
    });
    revokeInTransaction(db, tx, uid, next);
    return next;
  });
  if (epoch !== null) await rebuildPublication(db, uid, epoch);
}

export async function submitLibraryReport(
  db: Firestore, uid: string, ownerUid: string, reason: ReportReason, details: string, requestId: string,
): Promise<string> {
  if (uid === ownerUid) throw new HttpsError("invalid-argument", "You cannot report your own library.");
  const reportId = requestKey(uid, requestId);
  const receipt = db.doc(`users/${uid}/reportReceipts/${reportId}`);
  const throttle = db.doc(`libraryReportLimits/${uid}`);
  return db.runTransaction(async (tx) => {
    const [previous, limit, target] = await Promise.all([
      tx.get(receipt), tx.get(throttle), tx.get(db.doc(`sharedLibraries/${ownerUid}`)),
    ]);
    if (previous.exists) {
      if (previous.get("ownerUid") !== ownerUid || previous.get("reason") !== reason || previous.get("details") !== details) {
        throw new HttpsError("already-exists", "This request ID was used for a different report.");
      }
      return reportId;
    }
    if (target.get("published") !== true) throw new HttpsError("not-found", "This library is no longer shared.");
    const now = Timestamp.now();
    const started: unknown = limit.get("windowStartedAt");
    const last: unknown = limit.get("lastReportAt");
    const inWindow = started instanceof Timestamp && now.toMillis() - started.toMillis() < 86_400_000;
    const previousCount: unknown = limit.get("count");
    const count = inWindow && typeof previousCount === "number" ? previousCount : 0;
    if (count >= 5 || (last instanceof Timestamp && now.toMillis() - last.toMillis() < 60_000)) {
      throw new HttpsError("resource-exhausted", "Please wait before submitting another report (maximum five per day).");
    }
    const record = { reporterUid: uid, ownerUid, reason, details, createdAt: now };
    tx.create(db.doc(`libraryReports/${reportId}`), record);
    tx.create(receipt, { reportId, ownerUid, reason, details, createdAt: now });
    tx.set(throttle, { count: count + 1, windowStartedAt: inWindow ? started : now, lastReportAt: now });
    return reportId;
  });
}
