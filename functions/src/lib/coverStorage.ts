import { createHash } from "node:crypto";

import type { Bucket } from "@google-cloud/storage";

import { DomainError } from "./domain";
import { buildStorageMediaUrl } from "./storageUrl";

const MAX_COVER_BYTES = 5 * 1024 * 1024;
const REQUEST_TIMEOUT_MS = 8_000;
const MAX_REDIRECTS = 3;
const PLACEHOLDER_PATH = "catalog-covers/placeholders/missing-cover.jpg";
const TRUSTED_COVER_HOSTS = new Set([
  "images.isbndb.com",
  "images-eu.ssl-images-amazon.com",
  "images-na.ssl-images-amazon.com",
  "isbnsearch.org",
  "m.media-amazon.com",
  "www.isbnsearch.org",
]);

export const DEFAULT_BOOK_COVER_URL =
  "https://images.isbndb.com/covers/18052683482712.jpg";

const MIME_EXTENSIONS = new Map([
  ["image/jpeg", "jpg"],
  ["image/png", "png"],
  ["image/webp", "webp"],
]);

export interface StoredCover {
  coverUrl: string;
  coverStoragePath: string;
  coverSource: "storage" | "placeholder";
  coverOriginalUrl: string;
  coverContentHash: string;
}

export function storedCoverFromRecord(
  record: {
    coverUrl?: string | null;
    coverStoragePath?: string;
    coverSource?: string;
    coverOriginalUrl?: string;
    coverContentHash?: string;
  },
  bucketName: string,
): StoredCover | null {
  const path = record.coverStoragePath?.trim() ?? "";
  if (!path.startsWith("catalog-covers/")) {
    return null;
  }
  const fileName = path.split("/").pop() ?? "";
  const inferredHash = fileName.split(".")[0] ?? "";
  return {
    coverUrl: record.coverUrl || buildStorageMediaUrl(bucketName, path),
    coverStoragePath: path,
    coverSource: path.startsWith("catalog-covers/placeholders/") ? "placeholder" : "storage",
    coverOriginalUrl: record.coverOriginalUrl ?? "",
    coverContentHash: record.coverContentHash ?? inferredHash,
  };
}

export function assertTrustedCoverUrl(rawUrl: string): URL {
  let url: URL;
  try {
    url = new URL(rawUrl);
  } catch {
    throw new DomainError("invalid-argument", "The cover URL is invalid.");
  }
  if (
    url.protocol !== "https:" ||
    url.username ||
    url.password ||
    url.port ||
    !TRUSTED_COVER_HOSTS.has(url.hostname.toLowerCase())
  ) {
    throw new DomainError("invalid-argument", "The cover host is not supported.");
  }
  return url;
}

function validateImageBytes(contentType: string, bytes: Buffer): void {
  const valid = contentType === "image/jpeg"
    ? bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff
    : contentType === "image/png"
      ? bytes.length >= 8 && bytes.subarray(0, 8).equals(
        Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
      )
      : contentType === "image/webp"
        ? bytes.length >= 12 &&
          bytes.subarray(0, 4).toString("ascii") === "RIFF" &&
          bytes.subarray(8, 12).toString("ascii") === "WEBP"
        : false;
  if (!valid) {
    throw new DomainError("failed-precondition", "The cover provider returned an invalid image.");
  }
}

async function fetchTrustedImage(
  rawUrl: string,
  fetchImpl: typeof fetch,
  redirects = 0,
): Promise<{ bytes: Buffer; contentType: string; finalUrl: string }> {
  const url = assertTrustedCoverUrl(rawUrl);
  const response = await fetchImpl(url, {
    headers: {
      accept: "image/jpeg,image/png,image/webp",
      "user-agent": "Maktaba/1.0 (+https://github.com/jmkitavi/maktaba; cover cache)",
    },
    redirect: "manual",
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });
  if (response.status >= 300 && response.status < 400) {
    const location = response.headers.get("location");
    if (!location || redirects >= MAX_REDIRECTS) {
      throw new DomainError("failed-precondition", "The cover provider redirected unexpectedly.");
    }
    return fetchTrustedImage(new URL(location, url).toString(), fetchImpl, redirects + 1);
  }
  if (!response.ok) {
    throw new DomainError("failed-precondition", "The cover provider is temporarily unavailable.");
  }
  const contentType = response.headers.get("content-type")?.split(";")[0]?.trim().toLowerCase() ?? "";
  if (!MIME_EXTENSIONS.has(contentType)) {
    throw new DomainError("failed-precondition", "The cover provider returned an unsupported image.");
  }
  const declaredLength = Number(response.headers.get("content-length") ?? 0);
  if (declaredLength > MAX_COVER_BYTES) {
    throw new DomainError("failed-precondition", "The cover image is too large.");
  }
  if (!response.body) {
    throw new DomainError("failed-precondition", "The cover provider returned an empty image.");
  }
  const chunks: Buffer[] = [];
  let totalBytes = 0;
  const reader = response.body.getReader();
  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    totalBytes += value.byteLength;
    if (totalBytes > MAX_COVER_BYTES) {
      await reader.cancel();
      throw new DomainError("failed-precondition", "The cover image is too large.");
    }
    chunks.push(Buffer.from(value));
  }
  const bytes = Buffer.concat(chunks, totalBytes);
  if (bytes.length === 0) {
    throw new DomainError("failed-precondition", "The cover image is empty or too large.");
  }
  validateImageBytes(contentType, bytes);
  return { bytes, contentType, finalUrl: url.toString() };
}

function safeObjectSegment(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9_-]+/g, "_").replace(/^_+|_+$/g, "") || "book";
}

export async function storeTrustedCover(args: {
  bucket: Bucket;
  bucketName: string;
  sourceUrl: string;
  catalogKey: string;
  fetchImpl?: typeof fetch;
  placeholder?: boolean;
  objectPath?: string;
}): Promise<StoredCover> {
  const fetched = await fetchTrustedImage(args.sourceUrl, args.fetchImpl ?? fetch);
  const hash = createHash("sha256").update(fetched.bytes).digest("hex");
  const extension = MIME_EXTENSIONS.get(fetched.contentType)!;
  const prefix = args.placeholder ? "catalog-covers/placeholders" : "catalog-covers/books";
  const objectPath = args.objectPath ??
    `${prefix}/${safeObjectSegment(args.catalogKey)}/${hash}.${extension}`;
  const file = args.bucket.file(objectPath);
  const [exists] = await file.exists();
  if (!exists) {
    await file.save(fetched.bytes, {
      resumable: false,
      contentType: fetched.contentType,
      metadata: {
        cacheControl: "public,max-age=31536000,immutable",
        metadata: {
          sourceUrl: fetched.finalUrl,
          sourceHost: new URL(fetched.finalUrl).hostname,
          contentHash: hash,
          catalogKey: args.catalogKey,
          cachedAt: new Date().toISOString(),
        },
      },
    });
  }
  return {
    coverUrl: buildStorageMediaUrl(args.bucketName, objectPath),
    coverStoragePath: objectPath,
    coverSource: args.placeholder ? "placeholder" : "storage",
    coverOriginalUrl: fetched.finalUrl,
    coverContentHash: hash,
  };
}

export async function storeCoverOrPlaceholder(args: {
  bucket: Bucket;
  bucketName: string;
  sourceUrl?: string | null;
  catalogKey: string;
  fetchImpl?: typeof fetch;
}): Promise<StoredCover> {
  const placeholderFile = args.bucket.file(PLACEHOLDER_PATH);
  const existingPlaceholder = async (): Promise<StoredCover | null> => {
    const [exists] = await placeholderFile.exists();
    if (!exists) {
      return null;
    }
    const [metadata] = await placeholderFile.getMetadata();
    return {
      coverUrl: buildStorageMediaUrl(args.bucketName, PLACEHOLDER_PATH),
      coverStoragePath: PLACEHOLDER_PATH,
      coverSource: "placeholder",
      coverOriginalUrl: String(metadata.metadata?.["sourceUrl"] ?? DEFAULT_BOOK_COVER_URL),
      coverContentHash: String(metadata.metadata?.["contentHash"] ?? ""),
    };
  };
  if (args.sourceUrl) {
    for (let attempt = 0; attempt < 3; attempt += 1) {
      try {
        return await storeTrustedCover({
          ...args,
          sourceUrl: args.sourceUrl,
        });
      } catch (error) {
        const retryableFetchFailure =
          error instanceof DomainError ||
          (error instanceof Error && (error.name === "TimeoutError" || error.name === "TypeError"));
        if (!retryableFetchFailure) {
          throw error;
        }
        if (attempt < 2) {
          await new Promise((resolve) => setTimeout(resolve, 200 * (attempt + 1)));
        }
      }
    }
  }
  const cachedPlaceholder = await existingPlaceholder();
  if (cachedPlaceholder) {
    return cachedPlaceholder;
  }
  try {
    return await storeTrustedCover({
      ...args,
      sourceUrl: DEFAULT_BOOK_COVER_URL,
      catalogKey: "missing-cover",
      placeholder: true,
      objectPath: PLACEHOLDER_PATH,
    });
  } catch (error) {
    const unavailable =
      error instanceof DomainError ||
      (error instanceof Error && (error.name === "TimeoutError" || error.name === "TypeError"));
    if (!unavailable) {
      throw error;
    }
    return {
      coverUrl: "",
      coverStoragePath: "",
      coverSource: "placeholder",
      coverOriginalUrl: DEFAULT_BOOK_COVER_URL,
      coverContentHash: "",
    };
  }
}
