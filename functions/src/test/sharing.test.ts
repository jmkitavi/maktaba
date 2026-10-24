import assert from "node:assert/strict";
import { test } from "node:test";
import { Timestamp } from "firebase-admin/firestore";
import {
  authenticatedInput, boolean, buildPublication, catalogIds, id, keysOnly, objectInput, reportDetails, reportReason,
  requestKey, searchPrefixes, settings, sourceHash, text, titleVisibilityChanges, type PublicationSource,
} from "../lib/sharing";
import { epochMatches, isPublicationContention } from "../sharingService";

function fixture(): PublicationSource {
  return {
    profile: { libraryShared: true, libraryName: "Maya's Shelf", displayName: "Maya Reader" },
    copies: [
      { id: "copy-a", data: { catalogBookId: "physical", createdAt: Timestamp.fromMillis(1) } },
      { id: "copy-b", data: { catalogBookId: "physical", createdAt: Timestamp.fromMillis(2) } },
      { id: "copy-c", data: { catalogBookId: "digital" } },
    ],
    loans: [{ id: "loan", data: {
      copyId: "copy-a", catalogBookId: "physical", status: "active", isOpen: true,
      borrowerUid: "secret-reader", dueDate: Timestamp.fromMillis(100),
    } }],
    collections: [{ id: "list", data: {
      name: "Reading", shared: true, catalogIds: ["physical", "borrowed", "saved", "digital"],
    } }],
    catalog: ["physical", "digital", "borrowed", "saved"].map((id) => ({
      id, data: { title: id, authors: ["Author"], coverUrl: `https://covers/${id}`,
        format: id === "digital" ? "DIGITAL" : "PHYSICAL", createdByUid: "secret" },
    })),
  };
}
test("legacy settings default off and an independently shared list never opts the library in", () => {
  assert.deepEqual(settings({}), { libraryShared: false, libraryName: "", hiddenCatalogIds: [], publicationEpoch: 0 });
  const source = fixture();
  source.profile = {};
  const result = buildPublication(source, 3);
  assert.equal(result.libraryBooks.length, 0);
  assert.equal(result.collections[0]?.books.length, 4);
});
test("owned duplicates aggregate by title, accepted open loans determine availability; borrowed excluded, digital included", () => {
  const source = fixture();
  let result = buildPublication(source, 1);
  assert.equal(result.libraryBooks.length, 2);
  assert.equal(result.libraryBooks.find((book) => book.catalogBookId === "physical")?.availability, "available");
  assert.equal(result.libraryBooks.find((book) => book.catalogBookId === "digital")?.format, "DIGITAL");
  source.loans.push({ id: "second", data: { copyId: "copy-b", isOpen: true, status: "return_requested" } });
  result = buildPublication(source, 1);
  assert.equal(result.libraryBooks.find((book) => book.catalogBookId === "physical")?.availability, "on_loan");
  source.loans[0]!.data["status"] = "returned";
  source.loans[0]!.data["isOpen"] = false;
  assert.equal(buildPublication(source, 1).libraryBooks[0]?.availability, "available");
  assert.ok(!result.libraryBooks.some((book) => book.catalogBookId === "borrowed"));
});
test("all list metadata is sanitized and global hiding persists without copies without censoring other owners", () => {
  const source = fixture();
  const otherOwner = fixture();
  source.profile["hiddenCatalogIds"] = ["physical", "borrowed"];
  const membership = JSON.stringify(source.collections);
  let result = buildPublication(source, 5);
  assert.deepEqual(result.libraryBooks.map((book) => book.catalogBookId), ["digital"]);
  assert.deepEqual(result.collections[0]?.books.map((book) => book.catalogBookId), ["saved", "digital"]);
  assert.equal(JSON.stringify(source.collections), membership);
  assert.equal(buildPublication(otherOwner, 5).collections[0]?.books.length, 4);
  source.copies = [];
  source.loans = [];
  result = buildPublication(source, 6);
  assert.ok(!result.collections[0]?.books.some((book) => book.catalogBookId === "borrowed"));
  for (const book of result.collections[0]?.books ?? []) {
    assert.deepEqual(Object.keys(book).sort(), [
      "addedAt", "author", "catalogBookId", "coverUrl", "format", "publicationEpoch", "title",
    ]);
  }
});
test("input bounds, strict shapes, identifiers and report enum reject invalid requests", () => {
  assert.throws(() => authenticatedInput(undefined, { name: "Spoof" }, ["name"]), { code: "unauthenticated" });
  assert.throws(() => authenticatedInput("owner", { ownerUid: "other" }, ["name"]), { code: "invalid-argument" });
  for (const invalid of ["", " ".repeat(3), "x".repeat(41), "name\n"]) assert.throws(() => text(invalid, "Name", 40));
  assert.equal(text(" Shelf ", "Name", 40), "Shelf");
  assert.equal(text("x".repeat(60), "Collection", 60).length, 60);
  assert.throws(() => text("x".repeat(61), "Collection", 60));
  assert.throws(() => catalogIds(["same", "same"]));
  assert.throws(() => catalogIds(Array.from({ length: 501 }, (_, i) => `id${i}`)));
  assert.throws(() => id("a/b"));
  assert.throws(() => boolean("true", "Shared"));
  assert.throws(() => keysOnly({ ownerUid: "impersonation" }, ["name"]));
  assert.throws(() => objectInput([]));
  assert.throws(() => reportReason("unsupported"));
  assert.equal(reportReason("harassment"), "harassment");
  assert.equal(reportDetails(" x "), "x");
  assert.equal(reportDetails("x".repeat(500)).length, 500);
  assert.throws(() => reportDetails("x".repeat(501)));
});
test("normalized bounded prefixes cover library/reader names and words", () => {
  const prefixes = searchPrefixes("  Maya’s  Shelf ", "JAMIE Reader");
  for (const query of ["maya", "shelf", "jamie", "reader", "jamie r"]) assert.ok(prefixes.includes(query));
  assert.equal(new Set(prefixes).size, prefixes.length);
  assert.ok(searchPrefixes("x".repeat(1000)).length <= 500);
  assert.ok(searchPrefixes("Café Étagère").includes("cafe"));
  assert.ok(searchPrefixes("Café Étagère").includes("etagere"));
});
test("global hidden preferences are bounded at 200, idempotent, and removable with sharing off", () => {
  const profile = { hiddenCatalogIds: Array.from({ length: 200 }, (_, i) => `title-${i}`) };
  assert.equal(titleVisibilityChanges(profile, "title-0", true).hiddenCatalogIds.length, 200);
  assert.throws(() => titleVisibilityChanges(profile, "additional-title", true), { code: "resource-exhausted" });
  assert.equal(titleVisibilityChanges(profile, "title-0", false).hiddenCatalogIds.length, 199);
  assert.equal(profile.hiddenCatalogIds.length, 200);
  assert.deepEqual(titleVisibilityChanges({}, "borrowed-saved-title", true), { hiddenCatalogIds: ["borrowed-saved-title"] });
});
test("stable request keys are owner scoped; source hashes fence mutations but ignore bookkeeping", () => {
  assert.equal(requestKey("owner", "retry"), requestKey("owner", "retry"));
  assert.notEqual(requestKey("other", "retry"), requestKey("owner", "retry"));
  const source = fixture();
  const hash = sourceHash(source);
  source.profile["publicationEpoch"] = 9;
  assert.equal(sourceHash(source), hash);
  assert.equal(epochMatches(source.profile, 8), false);
  assert.equal(epochMatches(source.profile, 9), true);
  source.profile["hiddenCatalogIds"] = ["physical"];
  assert.notEqual(sourceHash(source), hash);
});
test("publication retries only contention and the emulator's closed-transaction failure, not invalid requests", () => {
  assert.equal(isPublicationContention(Object.assign(new Error("ABORTED"), { code: 10 })), true);
  assert.equal(isPublicationContention(Object.assign(new Error("Transaction is invalid or closed."), { code: 3 })), true);
  assert.equal(isPublicationContention(Object.assign(new Error("Invalid document data"), { code: 3 })), false);
  assert.equal(isPublicationContention(new Error("Other failure")), false);
});
