export function normalizeStorageBucket(bucketName: string): string {
  return bucketName.replace(/^gs:\/\//, "").replace(/\/+$/, "");
}

export function buildStorageMediaUrl(bucketName: string, objectPath: string): string {
  return `https://firebasestorage.googleapis.com/v0/b/${encodeURIComponent(
    normalizeStorageBucket(bucketName),
  )}/o/${encodeURIComponent(objectPath)}?alt=media`;
}

export function resolveStorageBucket(
  env: NodeJS.ProcessEnv = process.env,
  appStorageBucket?: string | null,
): string | null {
  if (typeof appStorageBucket === "string" && appStorageBucket.trim()) {
    return normalizeStorageBucket(appStorageBucket);
  }

  const firebaseConfig = env["FIREBASE_CONFIG"];
  if (firebaseConfig) {
    try {
      const parsed = JSON.parse(firebaseConfig) as { storageBucket?: unknown };
      if (typeof parsed.storageBucket === "string" && parsed.storageBucket.trim()) {
        return normalizeStorageBucket(parsed.storageBucket);
      }
    } catch {
      // Ignore malformed FIREBASE_CONFIG and continue falling back.
    }
  }

  const explicitBucket = env["FIREBASE_STORAGE_BUCKET"];
  if (typeof explicitBucket === "string" && explicitBucket.trim()) {
    return normalizeStorageBucket(explicitBucket);
  }

  const projectId = env["GCLOUD_PROJECT"] ?? env["GOOGLE_CLOUD_PROJECT"];
  if (typeof projectId === "string" && projectId.trim()) {
    return `${projectId.trim()}.appspot.com`;
  }

  return null;
}
