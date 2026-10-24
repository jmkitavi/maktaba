import { onCall, HttpsError, type CallableRequest } from "firebase-functions/v2/https";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { getServices } from "./admin";
import {
  authenticatedInput, boolean, catalogIds, id, reportDetails, reportReason, text, titleVisibilityChanges,
  type Fields, type CollectionChanges,
} from "./lib/sharing";
import {
  changePrivateCollection, createPrivateCollection, mutateSettings,
  refreshPublication, submitLibraryReport,
} from "./sharingService";
import { REGION } from "./config";

function input(request: CallableRequest<unknown>, keys: string[]): { uid: string; data: Fields } {
  return authenticatedInput(request.auth?.uid, request.data, keys);
}
const options = { region: REGION, maxInstances: 10 };
export const setLibrarySharing = onCall<unknown>(options, async (request) => {
  const { uid, data } = input(request, ["libraryShared", "libraryName"]);
  const libraryShared = boolean(data["libraryShared"], "Library sharing");
  const libraryName = text(data["libraryName"], "Library name", 40);
  const state = await mutateSettings(getServices().db, uid, (profile) => {
    if (libraryShared && profile["librarySharingBlocked"] === true) {
      throw new HttpsError("failed-precondition", "Sharing is unavailable for this account.");
    }
    return { libraryShared, libraryName };
  });
  return { settings: state };
});
export const setTitleVisibility = onCall<unknown>(options, async (request) => {
  const { uid, data } = input(request, ["catalogBookId", "hidden"]);
  const catalogBookId = id(data["catalogBookId"], "Catalogue ID");
  const hidden = boolean(data["hidden"], "Hidden");
  const state = await mutateSettings(getServices().db, uid, (profile) => titleVisibilityChanges(profile, catalogBookId, hidden));
  return { settings: state };
});
export const createCollection = onCall<unknown>(options, async (request) => {
  const { uid, data } = input(request, ["name", "requestId"]);
  return { collectionId: await createPrivateCollection(
    getServices().db, uid, text(data["name"], "Collection name", 60), id(data["requestId"], "Request ID"),
  ) };
});
export const updateCollection = onCall<unknown>(options, async (request) => {
  const { uid, data } = input(request, ["collectionId", "name", "catalogIds", "shared"]);
  const collectionId = id(data["collectionId"], "Collection ID");
  const changes: CollectionChanges = {};
  if ("name" in data) changes.name = text(data["name"], "Collection name", 60);
  if ("catalogIds" in data) changes.catalogIds = catalogIds(data["catalogIds"]);
  if ("shared" in data) changes.shared = boolean(data["shared"], "Collection sharing");
  if (!Object.keys(changes).length) throw new HttpsError("invalid-argument", "Choose a collection change.");
  await changePrivateCollection(getServices().db, uid, collectionId, changes);
  return { collectionId };
});
export const deleteCollection = onCall<unknown>(options, async (request) => {
  const { uid, data } = input(request, ["collectionId"]);
  await changePrivateCollection(getServices().db, uid, id(data["collectionId"], "Collection ID"), null);
  return { deleted: true };
});
export const reportLibrary = onCall<unknown>(options, async (request) => {
  const { uid, data } = input(request, ["ownerUid", "reason", "details", "requestId"]);
  return { reportId: await submitLibraryReport(
    getServices().db, uid, id(data["ownerUid"], "Reader ID"), reportReason(data["reason"]),
    reportDetails(data["details"]), id(data["requestId"], "Request ID"),
  ) };
});

const triggerOptions = { ...options, retry: true };
export const onSharingUserBookWritten = onDocumentWritten(
  { ...triggerOptions, document: "userBooks/{copyId}" }, async (event) => {
    const owners = new Set<unknown>([event.data?.before.get("ownerId"), event.data?.after.get("ownerId")]);
    for (const uid of owners) if (typeof uid === "string") await refreshPublication(getServices().db, uid);
  },
);
export const onSharingLoanWritten = onDocumentWritten(
  { ...triggerOptions, document: "loans/{loanId}" }, async (event) => {
    const owners = new Set<unknown>([event.data?.before.get("lenderUid"), event.data?.after.get("lenderUid")]);
    for (const uid of owners) if (typeof uid === "string") await refreshPublication(getServices().db, uid);
  },
);
export const onSharingProfileWritten = onDocumentWritten(
  { ...triggerOptions, document: "users/{uid}" }, async (event) => {
    const before: Fields = event.data?.before.data() ?? {};
    const after: Fields = event.data?.after.data() ?? {};
    const relevant = ["displayName", "photoUrl", "photoURL", "libraryShared", "libraryName", "hiddenCatalogIds", "librarySharingBlocked"];
    if (relevant.some((key) => JSON.stringify(before[key]) !== JSON.stringify(after[key]))) {
      await refreshPublication(getServices().db, event.params.uid);
    }
  },
);
export const onSharingCollectionWritten = onDocumentWritten(
  { ...triggerOptions, document: "users/{uid}/collections/{collectionId}" },
  async (event) => refreshPublication(getServices().db, event.params.uid),
);
export const onSharingCatalogWritten = onDocumentWritten(
  { ...triggerOptions, document: "catalogBooks/{catalogId}" }, async (event) => {
    const db = getServices().db;
    const copies = await db.collection("userBooks").where("catalogBookId", "==", event.params.catalogId).get();
    const collections = await db.collectionGroup("collections").where("catalogIds", "array-contains", event.params.catalogId).get();
    const owners = new Set<string>();
    for (const copy of copies.docs) {
      const uid: unknown = copy.get("ownerId");
      if (typeof uid === "string") owners.add(uid);
    }
    for (const collection of collections.docs) {
      if (collection.ref.parent.parent?.parent.id === "users") owners.add(collection.ref.parent.parent.id);
    }
    for (const uid of owners) await refreshPublication(db, uid);
  },
);
export const onPublicationJobWritten = onDocumentWritten(
  { ...triggerOptions, document: "publicationJobs/{uid}" }, async (event) => {
    if (event.data?.after.get("pending") === true) await refreshPublication(getServices().db, event.params.uid);
  },
);
