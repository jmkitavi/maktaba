import assert from "node:assert/strict";
import test from "node:test";

import type { Bucket } from "@google-cloud/storage";

import {
  assertTrustedCoverUrl,
  DEFAULT_BOOK_COVER_URL,
  storeCoverOrPlaceholder,
  storeTrustedCover,
} from "../lib/coverStorage";

const JPEG = Buffer.from([0xff, 0xd8, 0xff, 0xd9]);

function response(body: Buffer, init: ResponseInit = {}): Response {
  return new Response(body, {
    status: 200,
    headers: {
      "content-type": "image/jpeg",
      "content-length": String(body.length),
    },
    ...init,
  });
}

function fakeBucket(existing = false) {
  const saves: Array<{ path: string; bytes: Buffer; options: unknown }> = [];
  const bucket = {
    file(path: string) {
      return {
        async exists() {
          return [existing];
        },
        async save(bytes: Buffer, options: unknown) {
          saves.push({ path, bytes, options });
        },
      };
    },
  } as unknown as Bucket;
  return { bucket, saves };
}

test("accepts exact trusted HTTPS cover hosts", () => {
  assert.equal(
    assertTrustedCoverUrl("https://images.isbndb.com/covers/book.jpg").hostname,
    "images.isbndb.com",
  );
  assert.equal(
    assertTrustedCoverUrl("https://m.media-amazon.com/images/I/book.jpg").hostname,
    "m.media-amazon.com",
  );
});

test("rejects unsafe or lookalike cover URLs", () => {
  for (const url of [
    "http://images.isbndb.com/cover.jpg",
    "https://images.isbndb.com.evil.test/cover.jpg",
    "https://user@images.isbndb.com/cover.jpg",
    "https://images.isbndb.com:444/cover.jpg",
    "https://example.com/cover.jpg",
  ]) {
    assert.throws(() => assertTrustedCoverUrl(url), /not supported/);
  }
});

test("stores trusted images at deterministic hash paths", async () => {
  const { bucket, saves } = fakeBucket();
  const stored = await storeTrustedCover({
    bucket,
    bucketName: "example.firebasestorage.app",
    sourceUrl: "https://images.isbndb.com/covers/book.jpg",
    catalogKey: "9781250255174",
    fetchImpl: async () => response(JPEG),
  });
  assert.equal(saves.length, 1);
  assert.match(stored.coverStoragePath, /^catalog-covers\/books\/9781250255174\/[a-f0-9]{64}\.jpg$/);
  assert.equal(stored.coverSource, "storage");
  assert.match(stored.coverUrl, /firebasestorage\.googleapis\.com/);
});

test("does not upload an existing content-addressed object", async () => {
  const { bucket, saves } = fakeBucket(true);
  await storeTrustedCover({
    bucket,
    bucketName: "example.firebasestorage.app",
    sourceUrl: "https://images.isbndb.com/covers/book.jpg",
    catalogKey: "book",
    fetchImpl: async () => response(JPEG),
  });
  assert.equal(saves.length, 0);
});

test("revalidates redirect destinations", async () => {
  await assert.rejects(
    storeTrustedCover({
      bucket: fakeBucket().bucket,
      bucketName: "example.firebasestorage.app",
      sourceUrl: "https://images.isbndb.com/covers/book.jpg",
      catalogKey: "book",
      fetchImpl: async () => new Response(null, {
        status: 302,
        headers: { location: "https://evil.test/book.jpg" },
      }),
    }),
    /not supported/,
  );
});

test("rejects unsupported and malformed image responses", async () => {
  await assert.rejects(
    storeTrustedCover({
      bucket: fakeBucket().bucket,
      bucketName: "example.firebasestorage.app",
      sourceUrl: "https://images.isbndb.com/covers/book.jpg",
      catalogKey: "book",
      fetchImpl: async () => new Response("not an image", {
        headers: { "content-type": "text/html" },
      }),
    }),
    /unsupported image/,
  );
  await assert.rejects(
    storeTrustedCover({
      bucket: fakeBucket().bucket,
      bucketName: "example.firebasestorage.app",
      sourceUrl: "https://images.isbndb.com/covers/book.jpg",
      catalogKey: "book",
      fetchImpl: async () => response(Buffer.from("broken")),
    }),
    /invalid image/,
  );
});

test("uses the cached placeholder after provider failures", async () => {
  const { bucket } = fakeBucket();
  let requests = 0;
  const stored = await storeCoverOrPlaceholder({
    bucket,
    bucketName: "example.firebasestorage.app",
    sourceUrl: "https://images.isbndb.com/covers/missing.jpg",
    catalogKey: "book",
    fetchImpl: async (input) => {
      requests += 1;
      return String(input) === DEFAULT_BOOK_COVER_URL
        ? response(JPEG)
        : new Response(null, { status: 404 });
    },
  });
  assert.equal(requests, 4);
  assert.equal(stored.coverSource, "placeholder");
  assert.equal(stored.coverStoragePath, "catalog-covers/placeholders/missing-cover.jpg");
});
