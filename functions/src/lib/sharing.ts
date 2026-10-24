import { createHash } from "node:crypto";
import { HttpsError } from "firebase-functions/v2/https";
import { Timestamp } from "firebase-admin/firestore";
import { resolveBookFormat } from "./bookFormat";

export type Fields = Record<string, unknown>;
export interface SharingSettings {
  libraryShared: boolean;
  libraryName: string;
  hiddenCatalogIds: string[];
  publicationEpoch: number;
}
export interface CollectionChanges { name?: string; catalogIds?: string[]; shared?: boolean }
export interface SourceDocument { id: string; data: Fields }
export interface PublicationSource {
  profile: Fields;
  copies: SourceDocument[];
  loans: SourceDocument[];
  collections: SourceDocument[];
  catalog: SourceDocument[];
}
export interface PublicBook {
  catalogBookId: string;
  title: string;
  author: string;
  coverUrl: string;
  format: "PHYSICAL" | "DIGITAL" | "UNKNOWN";
  addedAt: Timestamp;
  publicationEpoch: number;
}
export interface PublicShelfBook extends PublicBook { availability: "available" | "on_loan" }
export const REPORT_REASONS = [
  "inappropriate_name", "harassment", "impersonation", "spam", "other",
] as const;
export type ReportReason = typeof REPORT_REASONS[number];

export function objectInput(value: unknown): Fields {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    throw new HttpsError("invalid-argument", "Provide an object.");
  }
  return Object.fromEntries(Object.entries(value));
}
export function keysOnly(data: Fields, keys: string[]): void {
  if (Object.keys(data).some((key) => !keys.includes(key))) {
    throw new HttpsError("invalid-argument", "Unexpected input field.");
  }
}
export function authenticatedInput(uid: string | undefined, value: unknown, keys: string[]): { uid: string; data: Fields } {
  if (!uid) throw new HttpsError("unauthenticated", "Sign in to continue.");
  const data = objectInput(value);
  keysOnly(data, keys);
  return { uid, data };
}
export function text(value: unknown, label: string, max: number): string {
  if (typeof value !== "string" || !value.trim() || value.trim().length > max ||
      [...value].some((char) => char.charCodeAt(0) < 32 || char.charCodeAt(0) === 127)) {
    throw new HttpsError("invalid-argument", `${label} must contain 1–${max} characters without control characters.`);
  }
  return value.trim();
}
export function id(value: unknown, label = "ID"): string {
  const result = text(value, label, 128);
  if (result.includes("/") || result === "." || result === ".." || result !== value) {
    throw new HttpsError("invalid-argument", `${label} is invalid.`);
  }
  return result;
}
export function boolean(value: unknown, label: string): boolean {
  if (typeof value !== "boolean") throw new HttpsError("invalid-argument", `${label} must be true or false.`);
  return value;
}
export function catalogIds(value: unknown, max = 500): string[] {
  if (!Array.isArray(value) || value.length > max) {
    throw new HttpsError("invalid-argument", `Select at most ${max} catalogue titles.`);
  }
  const ids = value.map((entry: unknown) => id(entry, "Catalogue ID"));
  if (new Set(ids).size !== ids.length) throw new HttpsError("invalid-argument", "Catalogue IDs must be unique.");
  return ids;
}
export function strings(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}
export function settings(profile: Fields): SharingSettings {
  return {
    libraryShared: profile["libraryShared"] === true,
    libraryName: typeof profile["libraryName"] === "string" ? profile["libraryName"] : "",
    hiddenCatalogIds: strings(profile["hiddenCatalogIds"]),
    publicationEpoch: typeof profile["publicationEpoch"] === "number" ? profile["publicationEpoch"] : 0,
  };
}
export function titleVisibilityChanges(profile: Fields, catalogBookId: string, hidden: boolean): { hiddenCatalogIds: string[] } {
  const ids = new Set(settings(profile).hiddenCatalogIds);
  if (hidden) ids.add(catalogBookId); else ids.delete(catalogBookId);
  if (ids.size > 200) throw new HttpsError("resource-exhausted", "You can hide at most 200 titles.");
  return { hiddenCatalogIds: [...ids] };
}
export function requestKey(uid: string, requestId: string): string {
  return createHash("sha256").update(`${uid}\0${requestId}`).digest("hex");
}
export function searchPrefixes(...names: string[]): string[] {
  const prefixes = new Set<string>();
  for (const name of names) {
    const normalized = name.normalize("NFKD").replace(/\p{M}/gu, "").trim().toLowerCase().replace(/\s+/gu, " ");
    for (const part of [normalized, ...normalized.split(" ")]) {
      for (let size = 1; size <= Math.min(60, part.length); size++) prefixes.add(part.slice(0, size));
    }
  }
  return [...prefixes].slice(0, 500);
}
function safeString(value: unknown): string { return typeof value === "string" ? value : ""; }
function timestamp(value: unknown): Timestamp { return value instanceof Timestamp ? value : Timestamp.fromMillis(0); }
export function sourceHash(source: PublicationSource): string {
  const profile = source.profile;
  // Exclude bookkeeping so the barrier itself does not invalidate its own snapshot.
  return createHash("sha256").update(JSON.stringify({
    profile: {
      libraryShared: profile["libraryShared"], libraryName: profile["libraryName"],
      hiddenCatalogIds: profile["hiddenCatalogIds"], displayName: profile["displayName"],
      photoUrl: profile["photoUrl"], photoURL: profile["photoURL"],
      librarySharingBlocked: profile["librarySharingBlocked"],
    },
    copies: source.copies, loans: source.loans, collections: source.collections, catalog: source.catalog,
  })).digest("hex");
}
export function buildPublication(source: PublicationSource, epoch: number) {
  const state = settings(source.profile);
  const hidden = new Set(state.hiddenCatalogIds);
  const catalogue = new Map(source.catalog.map((doc) => [doc.id, doc.data]));
  const openCopies = new Set(source.loans.filter(({ data }) =>
    data["isOpen"] === true && (data["status"] === "active" || data["status"] === "return_requested"),
  ).map(({ data }) => safeString(data["copyId"])));
  const owned = new Map<string, SourceDocument[]>();
  for (const copy of source.copies) {
    const catalogId = safeString(copy.data["catalogBookId"]);
    if (copy.data["status"] === "ARCHIVED" || hidden.has(catalogId)) continue;
    const copies = owned.get(catalogId) ?? [];
    copies.push(copy);
    owned.set(catalogId, copies);
  }
  function book(catalogId: string, addedAt: unknown): PublicBook | undefined {
    const data = catalogue.get(catalogId);
    if (!data || hidden.has(catalogId)) return undefined;
    return {
      catalogBookId: catalogId, title: safeString(data["title"]),
      author: safeString(data["author"]) || strings(data["authors"]).join(", "),
      coverUrl: safeString(data["coverUrl"]),
      format: resolveBookFormat(typeof data["binding"] === "string" ? data["binding"] : undefined, data["format"]),
      addedAt: timestamp(addedAt), publicationEpoch: epoch,
    };
  }
  const libraryBooks: PublicShelfBook[] = [];
  if (state.libraryShared && source.profile["librarySharingBlocked"] !== true) {
    for (const [catalogId, copies] of owned) {
      const metadata = book(catalogId, copies[0]?.data["createdAt"]);
      if (metadata) libraryBooks.push({
        ...metadata, availability: copies.some((copy) => !openCopies.has(copy.id)) ? "available" : "on_loan",
      });
    }
  }
  const collections = source.profile["librarySharingBlocked"] === true ? [] : source.collections
    .filter(({ data }) => data["shared"] === true)
    .map(({ id: collectionId, data }) => ({
      collectionId, name: safeString(data["name"]),
      books: strings(data["catalogIds"]).flatMap((catalogId) => {
        const metadata = book(catalogId, data["createdAt"]);
        return metadata ? [metadata] : [];
      }),
    }));
  return { libraryBooks, collections };
}

export function reportReason(value: unknown): ReportReason {
  for (const reason of REPORT_REASONS) if (reason === value) return reason;
  throw new HttpsError("invalid-argument", "Choose a valid report reason.");
}
export function reportDetails(value: unknown): string {
  if (value === undefined || value === null) return "";
  if (typeof value !== "string" || value.length > 500 ||
      [...value].some((char) => {
        const code = char.charCodeAt(0);
        return (code < 32 && code !== 9 && code !== 10 && code !== 13) || code === 127;
      })) {
    throw new HttpsError("invalid-argument", "Report details must be at most 500 characters.");
  }
  return value.trim();
}
