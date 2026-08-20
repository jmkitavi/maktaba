import { FieldPath, Timestamp, type QueryDocumentSnapshot } from "firebase-admin/firestore";

import { getServices } from "../admin";
import { COLLECTIONS } from "../config";
import { storedCoverFromRecord, storeCoverOrPlaceholder } from "../lib/coverStorage";
import type { CatalogBookAppRecord } from "../models";

interface Options {
  projectId: string;
  bucketName: string;
  dryRun: boolean;
  force: boolean;
  limit: number | null;
}

function parseArgs(argv: string[]): Options {
  const valueAfter = (name: string): string | undefined => {
    const index = argv.indexOf(name);
    return index >= 0 ? argv[index + 1] : undefined;
  };
  const projectId = valueAfter("--project") ?? process.env["GCLOUD_PROJECT"] ?? "maktaba-2e21d";
  const bucketName = valueAfter("--bucket") ??
    process.env["FIREBASE_STORAGE_BUCKET"] ??
    `${projectId}.firebasestorage.app`;
  const rawLimit = valueAfter("--limit");
  const limit = rawLimit ? Number.parseInt(rawLimit, 10) : null;
  if (limit !== null && (!Number.isInteger(limit) || limit <= 0)) {
    throw new Error("--limit must be a positive integer.");
  }
  return {
    projectId,
    bucketName,
    dryRun: argv.includes("--dry-run"),
    force: argv.includes("--force"),
    limit,
  };
}

async function run(): Promise<void> {
  const options = parseArgs(process.argv.slice(2));
  const { db, storage } = getServices({
    projectId: options.projectId,
    storageBucket: options.bucketName,
  });
  const bucket = storage.bucket(options.bucketName);
  const pageSize = Math.min(options.limit ?? 100, 100);
  let cursor: QueryDocumentSnapshot | null = null;
  let scanned = 0;
  let migrated = 0;
  let skipped = 0;
  let failed = 0;

  do {
    let query = db.collection(COLLECTIONS.catalogBooks)
      .orderBy(FieldPath.documentId())
      .limit(pageSize);
    if (cursor) {
      query = query.startAfter(cursor);
    }
    const snapshot = await query.get();
    if (snapshot.empty) {
      break;
    }
    for (const document of snapshot.docs) {
      if (options.limit !== null && scanned >= options.limit) {
        break;
      }
      scanned += 1;
      const record = document.data() as CatalogBookAppRecord;
      const existingStoredCover = storedCoverFromRecord(record, options.bucketName);
      if (!options.force && existingStoredCover) {
        skipped += 1;
        console.log(`skip ${document.id}: already cached`);
        continue;
      }
      if (options.force && existingStoredCover && !record.coverOriginalUrl) {
        skipped += 1;
        console.log(`skip ${document.id}: cached cover has no provider URL to refresh`);
        continue;
      }
      if (options.dryRun) {
        migrated += 1;
        console.log(
          `would migrate ${document.id}: ${
            record.coverOriginalUrl || (existingStoredCover ? "repair metadata" : record.coverUrl || "placeholder")
          }`,
        );
        continue;
      }
      try {
        const storedCover = options.force
          ? await storeCoverOrPlaceholder({
            bucket,
            bucketName: options.bucketName,
            sourceUrl: record.coverOriginalUrl,
            catalogKey: record.isbn13 || document.id,
          })
          : existingStoredCover ?? await storeCoverOrPlaceholder({
            bucket,
            bucketName: options.bucketName,
            sourceUrl: record.coverOriginalUrl || record.coverUrl,
            catalogKey: record.isbn13 || document.id,
          });
        const now = Timestamp.now();
        const update = {
          ...storedCover,
          coverCachedAt: now,
          updatedAt: now,
        };
        const batch = db.batch();
        batch.set(document.ref, update, { merge: true });
        batch.set(db.collection(COLLECTIONS.catalog).doc(document.id), update, { merge: true });
        await batch.commit();
        migrated += 1;
        console.log(`migrated ${document.id}: ${storedCover.coverStoragePath}`);
      } catch (error) {
        failed += 1;
        console.error(`failed ${document.id}:`, error);
      }
    }
    cursor = snapshot.docs[snapshot.docs.length - 1] ?? null;
    if (snapshot.size < pageSize || (options.limit !== null && scanned >= options.limit)) {
      break;
    }
  } while (cursor);

  console.log(JSON.stringify({
    projectId: options.projectId,
    bucketName: options.bucketName,
    dryRun: options.dryRun,
    force: options.force,
    scanned,
    migrated,
    skipped,
    failed,
  }, null, 2));
  if (failed > 0) {
    process.exitCode = 1;
  }
}

void run();
