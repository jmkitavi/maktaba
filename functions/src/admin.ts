import { getApps, initializeApp, type App } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { getStorage } from "firebase-admin/storage";

export interface AdminInitOptions {
  projectId?: string;
  storageBucket?: string;
}

export function ensureAdminApp(options: AdminInitOptions = {}): App {
  if (getApps().length > 0) {
    return getApps()[0]!;
  }

  return initializeApp({
    ...(options.projectId ? { projectId: options.projectId } : {}),
    ...(options.storageBucket ? { storageBucket: options.storageBucket } : {}),
  });
}

export function getServices(options: AdminInitOptions = {}) {
  const app = ensureAdminApp(options);

  return {
    app,
    auth: getAuth(app),
    db: getFirestore(app),
    messaging: getMessaging(app),
    storage: getStorage(app),
  };
}
