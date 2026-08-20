import type { UserRecord } from "firebase-admin/auth";
import { randomUUID } from "node:crypto";
import {
  Timestamp,
  type DocumentReference,
  type QueryDocumentSnapshot,
  type Transaction,
} from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { setGlobalOptions } from "firebase-functions/v2";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import * as functionsV1 from "firebase-functions/v1";

import { ensureAdminApp, getServices } from "./admin";
import {
  COLLECTIONS,
  DUE_SOON_DAYS,
  INVITE_EXPIRY_DAYS,
  MANUAL_REMINDER_COOLDOWN_HOURS,
  REGION,
} from "./config";
import { formatLoanCode, generateLoanCode, normalizeLoanCode } from "./lib/codes";
import {
  assertBookFormatIsLendable,
  resolveBookFormat,
  suggestPhysicalEditionIsbn13,
} from "./lib/bookFormat";
import { DomainError, assertDomain } from "./lib/domain";
import {
  DEFAULT_BOOK_COVER_URL,
  storedCoverFromRecord,
  storeCoverOrPlaceholder,
  type StoredCover,
} from "./lib/coverStorage";
import { normalizeIsbn } from "./lib/isbn";
import { fetchIsbnSearch } from "./lib/isbnSearch";
import {
  assertParticipantRole,
  confirmReturnTransition,
  differenceInCalendarDaysUtc,
  nextManualReminderAllowedAt,
  requestReturnTransition,
  selectDueReminderKind,
  type DueReminderKind,
  type LoanRole,
  type LoanStateSnapshot,
} from "./lib/loanState";
import {
  validateAcceptLoanInviteInput,
  validateCreateLoanInviteInput,
  validateLoanIdInput,
  validateLoanWorkflowIdInput,
  validateResolveLoanInviteInput,
  validateUserProfileInput,
} from "./lib/validation";
import { buildStorageMediaUrl, resolveStorageBucket } from "./lib/storageUrl";
import type {
  BookSnapshot,
  CatalogBookAppRecord,
  CatalogBookRecord,
  FcmTokenDocument,
  LendingSlotDocument,
  LoanDocument,
  LoanInviteCodeDocument,
  LoanInviteDocument,
  NotificationDocument,
  NotificationPreferences,
  NotificationType,
  ReminderState,
  UserBookDocument,
  UserProfileDocument,
  IsbnBookMetadata,
} from "./models";

ensureAdminApp();
const { app, db, messaging, storage } = getServices();
const storageBucket = resolveStorageBucket(
  process.env,
  typeof app.options.storageBucket === "string" ? app.options.storageBucket : null,
);

setGlobalOptions({
  region: REGION,
  maxInstances: 10,
});

interface CallableIdentity {
  uid: string;
  email: string | null;
  displayName: string | null;
  photoURL: string | null;
}

const dateFormatter = new Intl.DateTimeFormat("en-US", {
  month: "short",
  day: "numeric",
  year: "numeric",
});

function normalizeSearchText(value: string): string {
  return value.trim().toLowerCase();
}

function addDays(baseDate: Date, days: number): Date {
  return new Date(baseDate.getTime() + (days * 86_400_000));
}

function formatDate(date: Date): string {
  return dateFormatter.format(date);
}

function pluralizeDay(count: number): string {
  return count === 1 ? "1 day" : `${count} days`;
}

function getOptionalString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function toIso(value: Timestamp | Date | null | undefined): string | null {
  if (!value) {
    return null;
  }

  const date = value instanceof Timestamp ? value.toDate() : value;
  return date.toISOString();
}

function toMillis(value: Timestamp | Date | null | undefined): number | null {
  if (!value) {
    return null;
  }

  return value instanceof Timestamp ? value.toMillis() : value.getTime();
}

function defaultNotificationPreferences(): NotificationPreferences {
  return {
    pushEnabled: true,
    dueSoonEnabled: true,
    dueTodayEnabled: true,
    overdueEnabled: true,
    manualReminderEnabled: true,
  };
}

function deriveDisplayName(args: {
  uid: string;
  email: string | null;
  displayName: string | null;
  existingDisplayName?: string | null;
  overrideDisplayName?: string;
}): string {
  if (args.overrideDisplayName?.trim()) {
    return args.overrideDisplayName.trim();
  }

  if (args.displayName?.trim()) {
    return args.displayName.trim();
  }

  if (args.existingDisplayName?.trim()) {
    return args.existingDisplayName.trim();
  }

  if (args.email?.includes("@")) {
    return args.email.split("@")[0]!;
  }

  return `reader-${args.uid.slice(0, 6)}`;
}

function requireAuth(request: CallableRequest<unknown>): CallableIdentity {
  assertDomain(request.auth?.uid, "unauthenticated", "Authentication is required.");

  return {
    uid: request.auth!.uid,
    email: getOptionalString(request.auth!.token.email),
    displayName: getOptionalString(request.auth!.token["name"]),
    photoURL: getOptionalString(request.auth!.token["picture"]),
  };
}

function withCallableErrors<RequestData, ResponseData>(
  handler: (request: CallableRequest<RequestData>) => Promise<ResponseData>,
) {
  return onCall(async (request) => {
    try {
      return await handler(request as CallableRequest<RequestData>);
    } catch (error: unknown) {
      const httpsError = toHttpsError(error);
      logger.error("Callable execution failed", {
        code: httpsError.code,
        details:
          error instanceof Error
            ? { message: error.message, name: error.name, stack: error.stack }
            : String(error),
      });
      throw httpsError;
    }
  });
}

function toHttpsError(error: unknown): HttpsError {
  if (error instanceof HttpsError) {
    return error;
  }

  if (error instanceof DomainError) {
    return new HttpsError(error.code, error.message);
  }

  return new HttpsError("internal", "Unexpected server error.");
}

function lendingSlotRef(lenderUid: string, copyId: string): DocumentReference {
  return db.collection(COLLECTIONS.lendingSlots).doc(`${lenderUid}_${copyId}`);
}

function emptyReminderState(): ReminderState {
  return {
    borrowerDueSoonSentAt: null,
    borrowerDueDateSentAt: null,
    lenderOverdueSentAt: null,
  };
}

function makeLoanStateSnapshot(loan: LoanDocument): LoanStateSnapshot {
  return {
    status: loan.status,
    isOpen: loan.isOpen,
    lenderUid: loan.lenderUid,
    borrowerUid: loan.borrowerUid,
    dueDate: loan.dueDate.toDate(),
    returnRequestedByUid: loan.returnRequestedByUid,
    lastManualReminderAt: loan.lastManualReminderAt?.toDate() ?? null,
    reminderState: {
      borrowerDueSoonSentAt: loan.reminderState.borrowerDueSoonSentAt?.toDate() ?? null,
      borrowerDueDateSentAt: loan.reminderState.borrowerDueDateSentAt?.toDate() ?? null,
      lenderOverdueSentAt: loan.reminderState.lenderOverdueSentAt?.toDate() ?? null,
    },
  };
}

function coverUrlForBook(book: Pick<BookSnapshot, "coverStoragePath" | "coverUrl">): string {
  if (book.coverUrl) {
    return book.coverUrl;
  }
  if (book.coverStoragePath && storageBucket) {
    return buildStorageMediaUrl(storageBucket, book.coverStoragePath);
  }
  return "";
}

function coverBucket() {
  assertDomain(storageBucket, "failed-precondition", "Storage bucket is not configured.");
  return storage.bucket(storageBucket);
}

function inviteCopyId(invite: Pick<LoanInviteDocument, "copyId" | "catalogBookId">): string {
  return invite.copyId || invite.catalogBookId;
}

function loanCopyId(loan: Pick<LoanDocument, "copyId" | "catalogBookId">): string {
  return loan.copyId || loan.catalogBookId;
}

function serializeResolvedInvite(
  inviteId: string,
  invite: LoanInviteDocument,
  canAccept: boolean,
) {
  return {
    loanId: invite.loanId ?? inviteId,
    copyId: inviteCopyId(invite),
    inviteCode: invite.code,
    status: invite.status,
    catalogBookId: invite.catalogBookId,
    title: invite.book.title,
    author: invite.book.author,
    authors: [invite.book.author],
    coverUrl: coverUrlForBook(invite.book),
    lenderDisplayName: invite.lenderDisplayName,
    dueAtMillis: invite.dueDate.toMillis(),
    canAccept,
  };
}

function serializeLoan(loanId: string, loan: LoanDocument) {
  return {
    loanId,
    copyId: loanCopyId(loan),
    status: loan.status,
    isOpen: loan.isOpen,
    catalogBookId: loan.catalogBookId,
    title: loan.book.title,
    author: loan.book.author,
    authors: [loan.book.author],
    coverUrl: coverUrlForBook(loan.book),
    lenderDisplayName: loan.lenderDisplayName,
    borrowerDisplayName: loan.borrowerDisplayName,
    dueAtMillis: loan.dueDate.toMillis(),
    acceptedAtMillis: loan.acceptedAt.toMillis(),
    returnRequestedAtMillis: toMillis(loan.returnRequestedAt),
    returnedAtMillis: toMillis(loan.returnedAt),
  };
}

function serializeCancelledInvite(inviteId: string, invite: LoanInviteDocument) {
  return {
    loanId: invite.loanId ?? inviteId,
    copyId: inviteCopyId(invite),
    inviteCode: invite.code,
    status: invite.status,
    catalogBookId: invite.catalogBookId,
    title: invite.book.title,
    author: invite.book.author,
    authors: [invite.book.author],
    coverUrl: coverUrlForBook(invite.book),
    lenderDisplayName: invite.lenderDisplayName,
    dueAtMillis: invite.dueDate.toMillis(),
    expiresAtMillis: invite.expiresAt.toMillis(),
    cancelledAtMillis: toMillis(invite.cancelledAt),
    expiredAtMillis: toMillis(invite.expiredAt),
  };
}

function buildBookSnapshot(catalog: CatalogBookRecord): BookSnapshot {
  return {
    catalogBookId: catalog.id,
    title: catalog.title,
    author: catalog.author,
    coverStoragePath: catalog.coverStoragePath,
    coverUrl: catalog.coverUrl,
  };
}

function buildNotificationData(
  values: Record<string, string | null | undefined>,
): Record<string, string> {
  const data: Record<string, string> = {};

  for (const [key, value] of Object.entries(values)) {
    if (typeof value === "string" && value.length > 0) {
      data[key] = value;
    }
  }

  return data;
}

function buildLoanNotificationData(args: {
  loanId: string;
  inviteId: string;
  copyId: string;
  catalogBookId: string;
  type: NotificationType;
}): Record<string, string> {
  return buildNotificationData({
    loanId: args.loanId,
    inviteId: args.inviteId,
    copyId: args.copyId,
    catalogBookId: args.catalogBookId,
    type: args.type,
  });
}

function buildNotificationDocument(args: {
  recipientUid: string;
  actorUid?: string | null;
  type: NotificationType;
  title: string;
  body: string;
  loanId?: string | null;
  inviteId?: string | null;
  catalogBookId?: string | null;
  data?: Record<string, string>;
  now: Timestamp;
}): NotificationDocument {
  return {
    recipientUid: args.recipientUid,
    actorUid: args.actorUid ?? null,
    type: args.type,
    title: args.title,
    body: args.body,
    loanId: args.loanId ?? null,
    inviteId: args.inviteId ?? null,
    catalogBookId: args.catalogBookId ?? null,
    data: args.data ?? {},
    isRead: false,
    readAt: null,
    createdAt: args.now,
    updatedAt: args.now,
    push: {
      status: "pending",
      attemptedAt: null,
      sentAt: null,
      sentCount: 0,
      failureCount: 0,
      invalidTokenCount: 0,
      lastError: null,
    },
  };
}

function queueNotification(transaction: Transaction, notification: NotificationDocument): void {
  const notificationRef = db.collection(COLLECTIONS.notifications).doc();
  transaction.set(notificationRef, notification);
}

function clearSlot(
  transaction: Transaction,
  slotRef: DocumentReference,
  lenderUid: string,
  copyId: string,
  catalogBookId: string,
  now: Timestamp,
): void {
  const availableSlot: LendingSlotDocument = {
    lenderUid,
    copyId,
    catalogBookId,
    state: "available",
    currentInviteId: null,
    currentLoanId: null,
    updatedAt: now,
  };

  transaction.set(slotRef, availableSlot, { merge: true });
}

function markInviteExpired(
  transaction: Transaction,
  inviteRef: DocumentReference,
  codeRef: DocumentReference,
  slotRef: DocumentReference,
  invite: LoanInviteDocument,
  now: Timestamp,
): void {
  transaction.set(
    inviteRef,
    {
      status: "expired",
      expiredAt: now,
      updatedAt: now,
    },
    { merge: true },
  );
  transaction.set(
    codeRef,
    {
      status: "expired",
      updatedAt: now,
    },
    { merge: true },
  );
  clearSlot(transaction, slotRef, invite.lenderUid, inviteCopyId(invite), invite.catalogBookId, now);
}

function isInviteExpired(invite: LoanInviteDocument, now: Timestamp): boolean {
  const nowMillis = now.toMillis();
  return invite.expiresAt.toMillis() <= nowMillis || invite.dueDate.toMillis() <= nowMillis;
}

function markReminderState(
  currentState: ReminderState | undefined,
  kind: DueReminderKind,
  now: Timestamp,
): ReminderState {
  const state = currentState ?? emptyReminderState();

  return {
    borrowerDueSoonSentAt:
      kind === "borrower_due_soon" ? now : state.borrowerDueSoonSentAt ?? null,
    borrowerDueDateSentAt:
      kind === "borrower_due_today" ? now : state.borrowerDueDateSentAt ?? null,
    lenderOverdueSentAt:
      kind === "lender_overdue" ? now : state.lenderOverdueSentAt ?? null,
  };
}

function buildInviteAcceptedNotification(
  loanId: string,
  inviteId: string,
  loan: LoanDocument,
  now: Timestamp,
): NotificationDocument {
  return buildNotificationDocument({
    recipientUid: loan.lenderUid,
    actorUid: loan.borrowerUid,
    type: "loan_invite_accepted",
    title: "Borrow confirmed",
    body: `${loan.borrowerDisplayName} accepted your invite for "${loan.book.title}".`,
    loanId,
    inviteId,
    catalogBookId: loan.catalogBookId,
    data: buildLoanNotificationData({
      loanId,
      inviteId,
      copyId: loanCopyId(loan),
      catalogBookId: loan.catalogBookId,
      type: "loan_invite_accepted",
    }),
    now,
  });
}

function buildReturnRequestNotification(
  loanId: string,
  loan: LoanDocument,
  role: LoanRole,
  actorUid: string,
  now: Timestamp,
): NotificationDocument {
  if (role === "borrower") {
    return buildNotificationDocument({
      recipientUid: loan.lenderUid,
      actorUid,
      type: "return_requested",
      title: "Return ready",
      body: `${loan.borrowerDisplayName} asked you to confirm the return of "${loan.book.title}".`,
      loanId,
      inviteId: loan.inviteId,
      catalogBookId: loan.catalogBookId,
      data: buildLoanNotificationData({
        loanId,
        inviteId: loan.inviteId,
        copyId: loanCopyId(loan),
        catalogBookId: loan.catalogBookId,
        type: "return_requested",
      }),
      now,
    });
  }

  return buildNotificationDocument({
    recipientUid: loan.borrowerUid,
    actorUid,
    type: "return_requested",
    title: "Return requested",
    body: `${loan.lenderDisplayName} asked for "${loan.book.title}" back by ${formatDate(
      loan.dueDate.toDate(),
    )}.`,
    loanId,
    inviteId: loan.inviteId,
    catalogBookId: loan.catalogBookId,
    data: buildLoanNotificationData({
      loanId,
      inviteId: loan.inviteId,
      copyId: loanCopyId(loan),
      catalogBookId: loan.catalogBookId,
      type: "return_requested",
    }),
    now,
  });
}

function buildReturnConfirmedNotification(
  loanId: string,
  loan: LoanDocument,
  actorUid: string,
  now: Timestamp,
): NotificationDocument {
  const confirmedByLender = actorUid === loan.lenderUid;
  return buildNotificationDocument({
    recipientUid: confirmedByLender ? loan.borrowerUid : loan.lenderUid,
    actorUid,
    type: "return_confirmed",
    title: "Return confirmed",
    body: `${
      confirmedByLender ? loan.lenderDisplayName : loan.borrowerDisplayName
    } confirmed the return of "${loan.book.title}".`,
    loanId,
    inviteId: loan.inviteId,
    catalogBookId: loan.catalogBookId,
    data: buildLoanNotificationData({
      loanId,
      inviteId: loan.inviteId,
      copyId: loanCopyId(loan),
      catalogBookId: loan.catalogBookId,
      type: "return_confirmed",
    }),
    now,
  });
}

function buildManualReminderNotification(
  loanId: string,
  loan: LoanDocument,
  role: LoanRole,
  actorUid: string,
  now: Timestamp,
): NotificationDocument {
  const actorName = role === "lender" ? loan.lenderDisplayName : loan.borrowerDisplayName;
  const recipientUid = role === "lender" ? loan.borrowerUid : loan.lenderUid;

  return buildNotificationDocument({
    recipientUid,
    actorUid,
    type: "loan_reminder",
    title: "Loan reminder",
    body: `${actorName} sent a reminder about "${loan.book.title}" (due ${formatDate(
      loan.dueDate.toDate(),
    )}).`,
    loanId,
    inviteId: loan.inviteId,
    catalogBookId: loan.catalogBookId,
    data: buildLoanNotificationData({
      loanId,
      inviteId: loan.inviteId,
      copyId: loanCopyId(loan),
      catalogBookId: loan.catalogBookId,
      type: "loan_reminder",
    }),
    now,
  });
}

function buildScheduledReminderNotification(
  loanId: string,
  loan: LoanDocument,
  kind: DueReminderKind,
  now: Timestamp,
): NotificationDocument {
  const daysUntilDue = differenceInCalendarDaysUtc(loan.dueDate.toDate(), now.toDate());

  if (kind === "borrower_due_soon") {
    return buildNotificationDocument({
      recipientUid: loan.borrowerUid,
      actorUid: loan.lenderUid,
      type: "borrower_due_soon",
      title: "Return reminder",
      body: `"${loan.book.title}" is due back in ${pluralizeDay(daysUntilDue)}.`,
      loanId,
      inviteId: loan.inviteId,
      catalogBookId: loan.catalogBookId,
      data: buildLoanNotificationData({
        loanId,
        inviteId: loan.inviteId,
        copyId: loanCopyId(loan),
        catalogBookId: loan.catalogBookId,
        type: "borrower_due_soon",
      }),
      now,
    });
  }

  if (kind === "borrower_due_today") {
    return buildNotificationDocument({
      recipientUid: loan.borrowerUid,
      actorUid: loan.lenderUid,
      type: "borrower_due_today",
      title: "Return due today",
      body: `"${loan.book.title}" is due back today.`,
      loanId,
      inviteId: loan.inviteId,
      catalogBookId: loan.catalogBookId,
      data: buildLoanNotificationData({
        loanId,
        inviteId: loan.inviteId,
        copyId: loanCopyId(loan),
        catalogBookId: loan.catalogBookId,
        type: "borrower_due_today",
      }),
      now,
    });
  }

  return buildNotificationDocument({
    recipientUid: loan.lenderUid,
    actorUid: loan.borrowerUid,
    type: "lender_overdue",
    title: "Loan update",
    body: `${loan.borrowerDisplayName} hasn't returned "${loan.book.title}" yet — due ${formatDate(
      loan.dueDate.toDate(),
    )}.`,
    loanId,
    inviteId: loan.inviteId,
    catalogBookId: loan.catalogBookId,
    data: buildLoanNotificationData({
      loanId,
      inviteId: loan.inviteId,
      copyId: loanCopyId(loan),
      catalogBookId: loan.catalogBookId,
      type: "lender_overdue",
    }),
    now,
  });
}

function buildFcmPayload(notificationId: string, notification: NotificationDocument) {
  return {
    notification: {
      title: notification.title,
      body: notification.body,
    },
    data: {
      notificationId,
      type: notification.type,
      ...notification.data,
    },
    android: {
      priority: "high" as const,
      notification: {
        channelId: "loan-updates",
      },
    },
  };
}

function chunk<T>(items: readonly T[], size: number): T[][] {
  const chunks: T[][] = [];

  for (let index = 0; index < items.length; index += size) {
    chunks.push(items.slice(index, index + size));
  }

  return chunks;
}

function buildCatalogRecordFromAppRecord(
  catalogBookId: string,
  appRecord: CatalogBookAppRecord,
  now: Timestamp,
): CatalogBookRecord {
  const author = appRecord.authors[0] ?? "Unknown author";
  const genre = appRecord.genres[0] ?? "";
  const physicalEdition = appRecord.physicalEditionIsbn13;

  return {
    id: catalogBookId,
    title: appRecord.title,
    author,
    genre,
    publishedYear: appRecord.publishedYear,
    pages: appRecord.pageCount,
    description: appRecord.description,
    coverStoragePath: appRecord.coverStoragePath,
    coverUrl: appRecord.coverUrl,
    coverSource: appRecord.coverSource,
    coverOriginalUrl: appRecord.coverOriginalUrl,
    coverContentHash: appRecord.coverContentHash,
    coverCachedAt: appRecord.coverCachedAt,
    searchableTitle: normalizeSearchText(appRecord.title),
    searchableAuthor: normalizeSearchText(author),
    ...(appRecord.binding ? { binding: appRecord.binding } : {}),
    format: resolveBookFormat(appRecord.binding, appRecord.format),
    ...(physicalEdition ? { physicalEditionIsbn13: physicalEdition } : {}),
    createdAt: appRecord.createdAt ?? now,
    updatedAt: now,
  };
}

async function ensureUserProfileDocument(
  identity: CallableIdentity | Pick<UserRecord, "uid" | "email" | "displayName" | "photoURL">,
  overrideDisplayName?: string,
): Promise<{ created: boolean; profile: UserProfileDocument }> {
  const userRef = db.collection(COLLECTIONS.users).doc(identity.uid);
  let created = false;

  const profile = await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(userRef);
    const now = Timestamp.now();
    const existing = snapshot.exists ? (snapshot.data() as UserProfileDocument) : undefined;

    const displayName = deriveDisplayName({
      uid: identity.uid,
      email: identity.email ?? null,
      displayName: identity.displayName ?? null,
      existingDisplayName: existing?.displayName ?? null,
      overrideDisplayName,
    });

    const profileDoc: UserProfileDocument = {
      uid: identity.uid,
      displayName,
      searchableDisplayName: normalizeSearchText(displayName),
      email: identity.email ?? existing?.email ?? null,
      photoURL: identity.photoURL ?? existing?.photoURL ?? null,
      notificationPreferences: existing?.notificationPreferences ?? defaultNotificationPreferences(),
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
      lastSeenAt: now,
    };

    created = !snapshot.exists;
    transaction.set(userRef, profileDoc, { merge: true });
    return profileDoc;
  });

  return { created, profile };
}

async function loadInviteByTransaction(
  transaction: Transaction,
  args: { loanId?: string; normalizedInviteCode?: string },
): Promise<{
  inviteRef: DocumentReference;
  invite: LoanInviteDocument;
  codeRef: DocumentReference;
  code: LoanInviteCodeDocument | null;
}> {
  if (args.normalizedInviteCode) {
    const codeRef = db.collection(COLLECTIONS.loanInviteCodes).doc(args.normalizedInviteCode);
    const codeSnap = await transaction.get(codeRef);
    assertDomain(codeSnap.exists, "not-found", "Loan invite not found.");
    const code = codeSnap.data() as LoanInviteCodeDocument;
    const inviteRef = db.collection(COLLECTIONS.loanInvites).doc(code.inviteId);
    const inviteSnap = await transaction.get(inviteRef);
    assertDomain(inviteSnap.exists, "not-found", "Loan invite not found.");

    return {
      inviteRef,
      invite: inviteSnap.data() as LoanInviteDocument,
      codeRef,
      code,
    };
  }

  assertDomain(args.loanId, "invalid-argument", "loanId or inviteCode is required.");
  const inviteRef = db.collection(COLLECTIONS.loanInvites).doc(args.loanId);
  const inviteSnap = await transaction.get(inviteRef);
  assertDomain(inviteSnap.exists, "not-found", "Loan invite not found.");
  const invite = inviteSnap.data() as LoanInviteDocument;
  const codeRef = db.collection(COLLECTIONS.loanInviteCodes).doc(invite.codeKey);
  const codeSnap = await transaction.get(codeRef);

  return {
    inviteRef,
    invite,
    codeRef,
    code: codeSnap.exists ? (codeSnap.data() as LoanInviteCodeDocument) : null,
  };
}

interface IsbnLookupCacheDocument {
  status: "found" | "not_found";
  metadata: IsbnBookMetadata | null;
  responseHash: string | null;
  fetchedAt: Timestamp;
  expiresAt: Timestamp;
  parserVersion: number;
}

function metadataFromCatalog(
  id: string,
  record: CatalogBookAppRecord,
): IsbnBookMetadata & { catalogBookId: string } {
  const format = resolveBookFormat(record.binding, record.format);
  const physicalEditionIsbn13 = record.physicalEditionIsbn13 ??
    suggestPhysicalEditionIsbn13(record.isbn13, format);
  return {
    catalogBookId: id,
    title: record.title,
    isbn13: record.isbn13 ?? "",
    isbn10: record.isbn10 ?? null,
    authors: record.authors,
    binding: record.binding ?? null,
    format,
    ...(physicalEditionIsbn13 ? { physicalEditionIsbn13 } : {}),
    publisher: record.publisher ?? null,
    publishedDate: record.publishedDate ?? null,
    coverUrl: record.coverUrl || null,
    coverStoragePath: record.coverStoragePath || undefined,
    coverSource: record.coverSource,
    coverOriginalUrl: record.coverOriginalUrl,
    coverContentHash: record.coverContentHash,
    sourceUrl: record.metadataSourceUrl ?? "",
  };
}

async function cacheCatalogCover(
  catalogBookId: string,
  record: CatalogBookAppRecord,
): Promise<CatalogBookAppRecord> {
  const existingStoredCover = storedCoverFromRecord(record, storageBucket!);
  const storedCover = existingStoredCover ?? await storeCoverOrPlaceholder({
    bucket: coverBucket(),
    bucketName: storageBucket!,
    sourceUrl: record.coverOriginalUrl || record.coverUrl,
    catalogKey: record.isbn13 || catalogBookId,
  });
  const now = Timestamp.now();
  const update = {
    ...storedCover,
    coverCachedAt: now,
    updatedAt: now,
  };
  const batch = db.batch();
  batch.set(db.collection(COLLECTIONS.catalogBooks).doc(catalogBookId), update, { merge: true });
  batch.set(db.collection(COLLECTIONS.catalog).doc(catalogBookId), update, { merge: true });
  await batch.commit();
  return { ...record, ...update };
}

async function findCatalogByIsbn(isbn13: string, isbn10: string | null) {
  const by13 = await db.collection(COLLECTIONS.catalogBooks)
    .where("isbn13", "==", isbn13)
    .limit(1)
    .get();
  if (!by13.empty) {
    return by13.docs[0]!;
  }
  if (!isbn10) {
    return null;
  }
  const by10 = await db.collection(COLLECTIONS.catalogBooks)
    .where("isbn10", "==", isbn10)
    .limit(1)
    .get();
  return by10.docs[0] ?? null;
}

export const lookupBookByIsbn = withCallableErrors(async (request) => {
  const auth = requireAuth(request);
  const normalized = normalizeIsbn((request.data as { isbn?: unknown } | null)?.isbn);
  const catalogSnapshot = await findCatalogByIsbn(normalized.isbn13, normalized.isbn10);
  if (catalogSnapshot) {
    const catalogRecord = await cacheCatalogCover(
      catalogSnapshot.id,
      catalogSnapshot.data() as CatalogBookAppRecord,
    );
    return {
      source: "firebase",
      metadata: metadataFromCatalog(
        catalogSnapshot.id,
        catalogRecord,
      ),
    };
  }

  const cacheRef = db.collection(COLLECTIONS.isbnLookupCache).doc(normalized.isbn13);
  const cacheSnapshot = await cacheRef.get();
  if (cacheSnapshot.exists) {
    const cached = cacheSnapshot.data() as IsbnLookupCacheDocument;
    if (cached.expiresAt.toMillis() > Date.now()) {
      if (cached.status === "not_found") {
        throw new DomainError("not-found", "No book was found for that ISBN.");
      }
      assertDomain(cached.metadata, "not-found", "No book was found for that ISBN.");
      let cachedMetadata = cached.metadata;
      if (!storedCoverFromRecord(cachedMetadata, storageBucket!)) {
        const storedCover = await storeCoverOrPlaceholder({
          bucket: coverBucket(),
          bucketName: storageBucket!,
          sourceUrl: cachedMetadata.coverUrl,
          catalogKey: normalized.isbn13,
        });
        cachedMetadata = { ...cachedMetadata, ...storedCover };
        await cacheRef.set({ metadata: cachedMetadata }, { merge: true });
      }
      const format = resolveBookFormat(cachedMetadata.binding, cachedMetadata.format);
      const physicalEditionIsbn13 = cachedMetadata.physicalEditionIsbn13 ??
        suggestPhysicalEditionIsbn13(cachedMetadata.isbn13, format);
      return {
        source: "isbnsearch",
        cached: true,
        metadata: {
          ...cachedMetadata,
          format,
          ...(physicalEditionIsbn13 ? { physicalEditionIsbn13 } : {}),
        },
      };
    }
  }

  assertDomain(
    process.env["ISBNSEARCH_ENABLED"] !== "false",
    "failed-precondition",
    "ISBN lookup is temporarily unavailable. Enter the book details manually.",
  );

  const throttleRef = db.collection(COLLECTIONS.isbnLookupThrottle).doc(auth.uid);
  await db.runTransaction(async (transaction) => {
    const throttleSnapshot = await transaction.get(throttleRef);
    const nextAllowedAt = throttleSnapshot.get("nextAllowedAt") as Timestamp | undefined;
    assertDomain(
      !nextAllowedAt || nextAllowedAt.toMillis() <= Date.now(),
      "resource-exhausted",
      "Please wait a moment before looking up another ISBN.",
    );
    transaction.set(throttleRef, {
      nextAllowedAt: Timestamp.fromMillis(Date.now() + 3_000),
      updatedAt: Timestamp.now(),
    });
  });

  try {
    const result = await fetchIsbnSearch(normalized.isbn13);
    const storedCover = await storeCoverOrPlaceholder({
      bucket: coverBucket(),
      bucketName: storageBucket!,
      sourceUrl: result.metadata.coverUrl,
      catalogKey: normalized.isbn13,
    });
    const metadata: IsbnBookMetadata = {
      ...result.metadata,
      ...storedCover,
    };
    const now = Timestamp.now();
    await cacheRef.set({
      status: "found",
      metadata,
      responseHash: result.responseHash,
      fetchedAt: now,
      expiresAt: Timestamp.fromMillis(now.toMillis() + (30 * 86_400_000)),
      parserVersion: 1,
    } satisfies IsbnLookupCacheDocument);
    logger.info("ISBN lookup succeeded", { uid: auth.uid, isbn13: normalized.isbn13 });
    return { source: "isbnsearch", cached: false, metadata };
  } catch (error: unknown) {
    if (error instanceof DomainError && error.code === "not-found") {
      const now = Timestamp.now();
      await cacheRef.set({
        status: "not_found",
        metadata: null,
        responseHash: null,
        fetchedAt: now,
        expiresAt: Timestamp.fromMillis(now.toMillis() + 86_400_000),
        parserVersion: 1,
      } satisfies IsbnLookupCacheDocument);
    }
    if (error instanceof Error && error.name === "TimeoutError") {
      throw new DomainError(
        "failed-precondition",
        "ISBN lookup timed out. Enter the book details manually or try again.",
      );
    }
    throw error;
  }
});

interface AddBookInput {
  catalogBookId?: unknown;
  isbn?: unknown;
  isbn10?: unknown;
  isbn13?: unknown;
  title?: unknown;
  authors?: unknown;
  publisher?: unknown;
  publishedDate?: unknown;
  binding?: unknown;
  pageCount?: unknown;
  genres?: unknown;
  description?: unknown;
  coverUrl?: unknown;
  metadataSource?: unknown;
  metadataSourceUrl?: unknown;
}

function requiredString(value: unknown, field: string, maxLength: number): string {
  assertDomain(typeof value === "string", "invalid-argument", `${field} is required.`);
  const result = value.trim();
  assertDomain(result.length > 0 && result.length <= maxLength, "invalid-argument", `${field} is invalid.`);
  return result;
}

function optionalString(value: unknown, maxLength: number): string {
  if (typeof value !== "string") {
    return "";
  }
  return value.trim().slice(0, maxLength);
}

export const addBookToLibrary = withCallableErrors(async (request) => {
  const auth = requireAuth(request);
  const input = (request.data ?? {}) as AddBookInput;
  const title = requiredString(input.title, "Title", 240);
  assertDomain(Array.isArray(input.authors), "invalid-argument", "At least one author is required.");
  const authors = input.authors
    .filter((value): value is string => typeof value === "string")
    .map((value) => value.trim())
    .filter(Boolean)
    .slice(0, 12);
  assertDomain(authors.length > 0, "invalid-argument", "At least one author is required.");

  const rawIsbn = input.isbn13 ?? input.isbn10 ?? input.isbn;
  const normalized = rawIsbn ? normalizeIsbn(rawIsbn) : null;
  const requestedIsbn10 = typeof input.isbn10 === "string" ? input.isbn10.trim() : normalized?.isbn10;
  const requestedCatalogId = optionalString(input.catalogBookId, 128);
  const requestedCatalog = requestedCatalogId
    ? await db.collection(COLLECTIONS.catalogBooks).doc(requestedCatalogId).get()
    : null;
  assertDomain(
    !requestedCatalogId || requestedCatalog?.exists,
    "not-found",
    "The selected catalog book no longer exists.",
  );
  const existingCatalog = requestedCatalog?.exists
    ? requestedCatalog
    : normalized
      ? await findCatalogByIsbn(normalized.isbn13, requestedIsbn10 ?? null)
      : null;
  const catalogRef = existingCatalog?.ref ??
    db.collection(COLLECTIONS.catalogBooks).doc(
      normalized ? `isbn_${normalized.isbn13}` : `manual_${randomUUID()}`,
    );
  const legacyRef = db.collection(COLLECTIONS.catalog).doc(catalogRef.id);
  let storedCover: StoredCover | null = null;
  if (!existingCatalog) {
    let cachedCover: IsbnBookMetadata | null = null;
    if (normalized) {
      const cachedSnapshot = await db.collection(COLLECTIONS.isbnLookupCache)
        .doc(normalized.isbn13)
        .get();
      const cachedDocument = cachedSnapshot.exists
        ? cachedSnapshot.data() as IsbnLookupCacheDocument
        : null;
      cachedCover = cachedDocument?.status === "found" ? cachedDocument.metadata : null;
    }
    if (
      cachedCover?.coverUrl &&
      cachedCover.coverStoragePath &&
      cachedCover.coverSource &&
      cachedCover.coverOriginalUrl &&
      cachedCover.coverContentHash
    ) {
      storedCover = {
        coverUrl: cachedCover.coverUrl,
        coverStoragePath: cachedCover.coverStoragePath,
        coverSource: cachedCover.coverSource,
        coverOriginalUrl: cachedCover.coverOriginalUrl,
        coverContentHash: cachedCover.coverContentHash,
      };
    } else {
      storedCover = await storeCoverOrPlaceholder({
        bucket: coverBucket(),
        bucketName: storageBucket!,
        sourceUrl: optionalString(input.coverUrl, 2048) || DEFAULT_BOOK_COVER_URL,
        catalogKey: normalized?.isbn13 ?? catalogRef.id,
      });
    }
  }

  const result = await db.runTransaction(async (transaction) => {
    const [catalogSnapshot, duplicateSnapshot] = await Promise.all([
      transaction.get(catalogRef),
      transaction.get(
        db.collection(COLLECTIONS.userBooks)
          .where("ownerId", "==", auth.uid)
          .where("catalogBookId", "==", catalogRef.id)
          .limit(1),
      ),
    ]);
    assertDomain(duplicateSnapshot.empty, "already-exists", "This book is already in your library.");

    const now = Timestamp.now();
    const existing = catalogSnapshot.exists
      ? catalogSnapshot.data() as CatalogBookAppRecord
      : null;
    if (!existing) {
      const publishedDate = optionalString(input.publishedDate, 32);
      const publishedYear = Number.parseInt(publishedDate.slice(0, 4), 10) || 0;
      assertDomain(storedCover, "internal", "Cover metadata was not prepared.");
      const genres = Array.isArray(input.genres)
        ? input.genres.filter((value): value is string => typeof value === "string")
          .map((value) => value.trim()).filter(Boolean).slice(0, 12)
        : [];
      const pageCount = typeof input.pageCount === "number" && input.pageCount >= 0
        ? Math.floor(input.pageCount)
        : 0;
      const metadataSource = input.metadataSource === "isbnsearch" ? "isbnsearch" : "manual";
      const binding = optionalString(input.binding, 120);
      const format = resolveBookFormat(binding);
      const physicalEditionIsbn13 = suggestPhysicalEditionIsbn13(
        normalized?.isbn13,
        format,
      );
      const appRecord: CatalogBookAppRecord = {
        title,
        normalizedTitle: normalizeSearchText(title),
        authors,
        coverUrl: storedCover.coverUrl,
        coverStoragePath: storedCover.coverStoragePath,
        genres,
        publishedYear,
        pageCount,
        description: optionalString(input.description, 10_000),
        isbn13: normalized?.isbn13 ?? "",
        isbn10: requestedIsbn10 ?? "",
        publisher: optionalString(input.publisher, 240),
        publishedDate,
        binding,
        format,
        ...(physicalEditionIsbn13 ? { physicalEditionIsbn13 } : {}),
        metadataSource,
        metadataSourceUrl: optionalString(input.metadataSourceUrl, 2048),
        metadataFetchedAt: metadataSource === "isbnsearch" ? now : null,
        coverSource: storedCover.coverSource,
        coverOriginalUrl: storedCover.coverOriginalUrl,
        coverContentHash: storedCover.coverContentHash,
        coverCachedAt: now,
        createdByUid: auth.uid,
        createdAt: now,
        updatedAt: now,
      };
      transaction.create(catalogRef, appRecord);
      transaction.create(legacyRef, {
        id: catalogRef.id,
        title,
        author: authors[0],
        genre: genres[0] ?? "",
        publishedYear,
        pages: pageCount,
        description: appRecord.description,
        coverStoragePath: storedCover.coverStoragePath,
        coverUrl: storedCover.coverUrl,
        searchableTitle: normalizeSearchText(title),
        searchableAuthor: normalizeSearchText(authors[0]!),
        isbn13: normalized?.isbn13 ?? "",
        isbn10: requestedIsbn10 ?? "",
        publisher: appRecord.publisher,
        publishedDate,
        ...(appRecord.binding ? { binding: appRecord.binding } : {}),
        format: appRecord.format,
        ...(physicalEditionIsbn13 ? { physicalEditionIsbn13 } : {}),
        metadataSource,
        metadataSourceUrl: appRecord.metadataSourceUrl,
        metadataFetchedAt: appRecord.metadataFetchedAt,
        coverSource: appRecord.coverSource,
        coverOriginalUrl: storedCover.coverOriginalUrl,
        coverContentHash: storedCover.coverContentHash,
        coverCachedAt: now,
        createdAt: now,
        updatedAt: now,
      });
    }

    const copyRef = db.collection(COLLECTIONS.userBooks).doc();
    transaction.create(copyRef, {
      ownerId: auth.uid,
      catalogBookId: catalogRef.id,
      status: "AVAILABLE",
      createdAt: now,
      updatedAt: now,
    } satisfies UserBookDocument);
    const binding = existing?.binding ?? optionalString(input.binding, 120);
    const format = resolveBookFormat(binding, existing?.format);
    const physicalEditionIsbn13 = existing?.physicalEditionIsbn13 ??
      suggestPhysicalEditionIsbn13(existing?.isbn13 ?? normalized?.isbn13, format);
    return {
      catalogBookId: catalogRef.id,
      copyId: copyRef.id,
      reused: Boolean(existing),
      binding,
      format,
      ...(physicalEditionIsbn13 ? { physicalEditionIsbn13 } : {}),
    };
  });

  return result;
});

export const createUserProfileIfNeeded = withCallableErrors(async (request) => {
  const auth = requireAuth(request);
  const input = validateUserProfileInput(request.data);
  const { created, profile } = await ensureUserProfileDocument(auth, input.displayName);
  return {
    created,
    profile: {
      uid: profile.uid,
      displayName: profile.displayName,
      email: profile.email,
      photoURL: profile.photoURL,
      updatedAt: toIso(profile.updatedAt),
    },
  };
});

export const createUserProfileOnAuthCreate = functionsV1
  .region(REGION)
  .auth.user()
  .onCreate(async (user) => {
    await ensureUserProfileDocument(user);
  });

export const createLoanInvite = withCallableErrors(async (request) => {
  const auth = requireAuth(request);
  const input = validateCreateLoanInviteInput(request.data);
  const { profile } = await ensureUserProfileDocument(auth);

  const copyRef = db.collection(COLLECTIONS.userBooks).doc(input.copyId);
  const slotRef = lendingSlotRef(auth.uid, input.copyId);

  for (let attempt = 0; attempt < 5; attempt += 1) {
    const displayCode = generateLoanCode();
    const codeKey = normalizeLoanCode(displayCode);
    const inviteRef = db.collection(COLLECTIONS.loanInvites).doc();
    const codeRef = db.collection(COLLECTIONS.loanInviteCodes).doc(codeKey);

    try {
      return await db.runTransaction(async (transaction) => {
        const now = Timestamp.now();
        const [copySnap, slotSnap, codeSnap] = await Promise.all([
          transaction.get(copyRef),
          transaction.get(slotRef),
          transaction.get(codeRef),
        ]);

        assertDomain(copySnap.exists, "not-found", "Book copy not found.");
        assertDomain(!codeSnap.exists, "already-exists", "Loan code collision.");
        const copy = copySnap.data() as UserBookDocument;
        assertDomain(
          copy.ownerId === auth.uid,
          "permission-denied",
          "Only the owner can lend this book copy.",
        );
        assertDomain(
          copy.status === "AVAILABLE",
          "failed-precondition",
          "This book copy is not currently available to lend.",
        );

        if (input.catalogBookId) {
          assertDomain(
            input.catalogBookId === copy.catalogBookId,
            "invalid-argument",
            "copyId does not match the provided catalogBookId.",
          );
        }

        const catalogRef = db.collection(COLLECTIONS.catalog).doc(copy.catalogBookId);
        const catalogBooksRef = db.collection(COLLECTIONS.catalogBooks).doc(copy.catalogBookId);
        const [catalogSnap, catalogBooksSnap] = await Promise.all([
          transaction.get(catalogRef),
          transaction.get(catalogBooksRef),
        ]);
        const legacyCatalog = catalogSnap.exists
          ? catalogSnap.data() as CatalogBookRecord
          : undefined;
        const appCatalog = catalogBooksSnap.exists
          ? catalogBooksSnap.data() as CatalogBookAppRecord
          : undefined;
        const appFormat = resolveBookFormat(appCatalog?.binding, appCatalog?.format);
        const legacyFormat = resolveBookFormat(legacyCatalog?.binding, legacyCatalog?.format);
        assertBookFormatIsLendable(appFormat);
        assertBookFormatIsLendable(legacyFormat);

        const slot = slotSnap.exists ? (slotSnap.data() as LendingSlotDocument) : undefined;
        if (slot?.state === "invite_pending" && slot.currentInviteId) {
          const existingInviteRef = db.collection(COLLECTIONS.loanInvites).doc(slot.currentInviteId);
          const existingInviteSnap = await transaction.get(existingInviteRef);
          if (existingInviteSnap.exists) {
            const existingInvite = existingInviteSnap.data() as LoanInviteDocument;
            const existingCodeRef = db
              .collection(COLLECTIONS.loanInviteCodes)
              .doc(existingInvite.codeKey);

            if (existingInvite.status === "pending" && !isInviteExpired(existingInvite, now)) {
              return {
                loanId: existingInviteRef.id,
                inviteCode: existingInvite.code,
                resumed: true,
              };
            }

            if (existingInvite.status === "pending" && isInviteExpired(existingInvite, now)) {
              markInviteExpired(
                transaction,
                existingInviteRef,
                existingCodeRef,
                slotRef,
                existingInvite,
                now,
              );
            }
          }
        }

        let catalog = legacyCatalog;
        if (!catalog && appCatalog) {
          catalog = buildCatalogRecordFromAppRecord(copy.catalogBookId, appCatalog, now);
          transaction.set(catalogRef, catalog, { merge: true });
        }
        assertDomain(catalog, "not-found", "Catalog book not found.");

        if (slot?.state === "loan_active" && slot.currentLoanId) {
          const existingLoanRef = db.collection(COLLECTIONS.loans).doc(slot.currentLoanId);
          const existingLoanSnap = await transaction.get(existingLoanRef);
          if (existingLoanSnap.exists) {
            const existingLoan = existingLoanSnap.data() as LoanDocument;
            if (existingLoan.isOpen) {
              throw new DomainError(
                "failed-precondition",
                "This book already has an active loan.",
              );
            }
          }
        }

        const book = buildBookSnapshot(catalog);
        const invite: LoanInviteDocument = {
          code: formatLoanCode(codeKey),
          codeKey,
          copyId: input.copyId,
          catalogBookId: copy.catalogBookId,
          book,
          lenderUid: auth.uid,
          lenderDisplayName: profile.displayName,
          borrowerNameHint: input.borrowerDisplayName ?? null,
          borrowerUid: null,
          borrowerDisplayName: null,
          status: "pending",
          dueDate: Timestamp.fromDate(input.dueAt),
          expiresAt: Timestamp.fromDate(addDays(now.toDate(), INVITE_EXPIRY_DAYS)),
          loanId: inviteRef.id,
          createdAt: now,
          updatedAt: now,
          acceptedAt: null,
          cancelledAt: null,
          expiredAt: null,
        };

        const codeDoc: LoanInviteCodeDocument = {
          code: invite.code,
          inviteId: inviteRef.id,
          status: "pending",
          expiresAt: invite.expiresAt,
          createdAt: now,
          updatedAt: now,
        };

        transaction.set(inviteRef, invite);
        transaction.set(codeRef, codeDoc);
        transaction.set(
          slotRef,
          {
            lenderUid: auth.uid,
            copyId: input.copyId,
            catalogBookId: copy.catalogBookId,
            state: "invite_pending",
            currentInviteId: inviteRef.id,
            currentLoanId: null,
            updatedAt: now,
          } satisfies LendingSlotDocument,
          { merge: true },
        );

        return {
          loanId: inviteRef.id,
          inviteCode: invite.code,
        };
      });
    } catch (error: unknown) {
      if (error instanceof DomainError && error.code === "already-exists") {
        continue;
      }

      throw error;
    }
  }

  throw new HttpsError("internal", "Unable to generate a unique loan code.");
});

export const resolveLoanInvite = withCallableErrors(async (request) => {
  const auth = requireAuth(request);
  const input = validateResolveLoanInviteInput(request.data);
  await ensureUserProfileDocument(auth);

  return db.runTransaction(async (transaction) => {
    const now = Timestamp.now();
    const { inviteRef, invite, codeRef } = await loadInviteByTransaction(transaction, input);
    const slotRef = lendingSlotRef(invite.lenderUid, inviteCopyId(invite));

    if (invite.status === "pending" && isInviteExpired(invite, now)) {
      markInviteExpired(transaction, inviteRef, codeRef, slotRef, invite, now);
      throw new DomainError("failed-precondition", "This loan invite has expired.");
    }

    if (invite.status === "accepted" && invite.borrowerUid === auth.uid) {
      return serializeResolvedInvite(inviteRef.id, invite, false);
    }

    assertDomain(invite.status === "pending", "failed-precondition", "This invite is no longer available.");
    assertDomain(invite.lenderUid !== auth.uid, "failed-precondition", "You cannot accept your own invite.");

    return serializeResolvedInvite(inviteRef.id, invite, true);
  });
});

export const acceptLoanInvite = withCallableErrors(async (request) => {
  const auth = requireAuth(request);
  const input = validateAcceptLoanInviteInput(request.data);
  const { profile } = await ensureUserProfileDocument(auth, input.borrowerDisplayName);

  return db.runTransaction(async (transaction) => {
    const now = Timestamp.now();
    const { inviteRef, invite, codeRef, code } = await loadInviteByTransaction(transaction, input);
    const slotRef = lendingSlotRef(invite.lenderUid, inviteCopyId(invite));

    if (invite.status === "pending" && isInviteExpired(invite, now)) {
      markInviteExpired(transaction, inviteRef, codeRef, slotRef, invite, now);
      throw new DomainError("failed-precondition", "This loan invite has expired.");
    }

    assertDomain(invite.lenderUid !== auth.uid, "failed-precondition", "You cannot accept your own invite.");

    const loanRef = db.collection(COLLECTIONS.loans).doc(invite.loanId ?? inviteRef.id);
    const existingLoanSnap = await transaction.get(loanRef);

    if (invite.status === "accepted") {
      assertDomain(
        invite.borrowerUid === auth.uid,
        "failed-precondition",
        "This invite has already been accepted.",
      );
      assertDomain(
        existingLoanSnap.exists,
        "failed-precondition",
        "The accepted invite is missing its loan document.",
      );
      return serializeLoan(loanRef.id, existingLoanSnap.data() as LoanDocument);
    }

    assertDomain(invite.status === "pending", "failed-precondition", "This invite is no longer available.");
    assertDomain(
      invite.dueDate.toMillis() > now.toMillis(),
      "failed-precondition",
      "This invite uses an expired due date.",
    );

    if (existingLoanSnap.exists) {
      const existingLoan = existingLoanSnap.data() as LoanDocument;
      assertDomain(
        existingLoan.borrowerUid === auth.uid,
        "failed-precondition",
        "This invite has already been accepted.",
      );
      return serializeLoan(loanRef.id, existingLoan);
    }

    const loan: LoanDocument = {
      inviteId: inviteRef.id,
      copyId: invite.copyId,
      catalogBookId: invite.catalogBookId,
      book: invite.book,
      lenderUid: invite.lenderUid,
      lenderDisplayName: invite.lenderDisplayName,
      borrowerUid: auth.uid,
      borrowerDisplayName: profile.displayName,
      participants: [invite.lenderUid, auth.uid],
      status: "active",
      isOpen: true,
      dueDate: invite.dueDate,
      createdAt: now,
      acceptedAt: now,
      updatedAt: now,
      returnRequestedAt: null,
      returnRequestedByUid: null,
      returnedAt: null,
      returnConfirmedByUid: null,
      lastManualReminderAt: null,
      lastManualReminderByUid: null,
      reminderState: emptyReminderState(),
    };

    transaction.set(loanRef, loan);
    transaction.set(
      inviteRef,
      {
        status: "accepted",
        borrowerUid: auth.uid,
        borrowerDisplayName: profile.displayName,
        acceptedAt: now,
        loanId: loanRef.id,
        updatedAt: now,
      },
      { merge: true },
    );
    transaction.set(
      codeRef,
      {
        code: invite.code,
        inviteId: inviteRef.id,
        status: "accepted",
        expiresAt: invite.expiresAt,
        createdAt: code?.createdAt ?? now,
        updatedAt: now,
      } satisfies LoanInviteCodeDocument,
      { merge: true },
    );
    transaction.set(
      slotRef,
      {
        lenderUid: invite.lenderUid,
        copyId: invite.copyId,
        catalogBookId: invite.catalogBookId,
        state: "loan_active",
        currentInviteId: inviteRef.id,
        currentLoanId: loanRef.id,
        updatedAt: now,
      } satisfies LendingSlotDocument,
      { merge: true },
    );

    queueNotification(
      transaction,
      buildInviteAcceptedNotification(loanRef.id, inviteRef.id, loan, now),
    );

    return serializeLoan(loanRef.id, loan);
  });
});

export const requestReturn = withCallableErrors(async (request) => {
  const auth = requireAuth(request);
  const input = validateLoanIdInput(request.data);
  await ensureUserProfileDocument(auth);

  return db.runTransaction(async (transaction) => {
    const loanRef = db.collection(COLLECTIONS.loans).doc(input.loanId);
    const loanSnap = await transaction.get(loanRef);
    assertDomain(loanSnap.exists, "not-found", "Loan not found.");

    const loan = loanSnap.data() as LoanDocument;
    const state = makeLoanStateSnapshot(loan);
    const role = assertParticipantRole(state, auth.uid);

    if (!loan.isOpen || loan.status === "returned" || loan.status === "return_requested") {
      return serializeLoan(loanRef.id, loan);
    }

    const now = Timestamp.now();
    requestReturnTransition(state, auth.uid, now.toDate());

    const updatedLoan: LoanDocument = {
      ...loan,
      status: "return_requested",
      returnRequestedAt: now,
      returnRequestedByUid: auth.uid,
      updatedAt: now,
    };

    transaction.set(
      loanRef,
      {
        status: "return_requested",
        returnRequestedAt: now,
        returnRequestedByUid: auth.uid,
        updatedAt: now,
      },
      { merge: true },
    );
    queueNotification(
      transaction,
      buildReturnRequestNotification(loanRef.id, updatedLoan, role, auth.uid, now),
    );

    return serializeLoan(loanRef.id, updatedLoan);
  });
});

export const confirmReturn = withCallableErrors(async (request) => {
  const auth = requireAuth(request);
  const input = validateLoanIdInput(request.data);
  await ensureUserProfileDocument(auth);

  return db.runTransaction(async (transaction) => {
    const loanRef = db.collection(COLLECTIONS.loans).doc(input.loanId);
    const loanSnap = await transaction.get(loanRef);
    assertDomain(loanSnap.exists, "not-found", "Loan not found.");

    const loan = loanSnap.data() as LoanDocument;
    if (!loan.isOpen || loan.status === "returned") {
      return serializeLoan(loanRef.id, loan);
    }

    const now = Timestamp.now();
    confirmReturnTransition(makeLoanStateSnapshot(loan), auth.uid, now.toDate());

    const updatedLoan: LoanDocument = {
      ...loan,
      status: "returned",
      isOpen: false,
      returnedAt: now,
      returnConfirmedByUid: auth.uid,
      updatedAt: now,
    };

    transaction.set(
      loanRef,
      {
        status: "returned",
        isOpen: false,
        returnedAt: now,
        returnConfirmedByUid: auth.uid,
        updatedAt: now,
      },
      { merge: true },
    );
    clearSlot(
      transaction,
      lendingSlotRef(loan.lenderUid, loanCopyId(loan)),
      loan.lenderUid,
      loanCopyId(loan),
      loan.catalogBookId,
      now,
    );
    queueNotification(
      transaction,
      buildReturnConfirmedNotification(loanRef.id, updatedLoan, auth.uid, now),
    );

    return serializeLoan(loanRef.id, updatedLoan);
  });
});

export const cancelLoanInvite = withCallableErrors(async (request) => {
  const auth = requireAuth(request);
  const input = validateLoanWorkflowIdInput(request.data);
  await ensureUserProfileDocument(auth);

  return db.runTransaction(async (transaction) => {
    const now = Timestamp.now();
    const { inviteRef, invite, codeRef } = await loadInviteByTransaction(transaction, input);
    assertDomain(invite.lenderUid === auth.uid, "permission-denied", "Only the lender can cancel this invite.");

    const slotRef = lendingSlotRef(invite.lenderUid, inviteCopyId(invite));

    if (invite.status === "cancelled") {
      return serializeCancelledInvite(inviteRef.id, invite);
    }

    if (invite.status === "accepted") {
      throw new DomainError("failed-precondition", "Accepted invites cannot be cancelled.");
    }

    if (invite.status === "expired" || (invite.status === "pending" && isInviteExpired(invite, now))) {
      if (invite.status === "pending") {
        markInviteExpired(transaction, inviteRef, codeRef, slotRef, invite, now);
      }

      return serializeCancelledInvite(inviteRef.id, {
        ...invite,
        status: "expired",
        expiredAt: invite.expiredAt ?? now,
        updatedAt: now,
      });
    }

    const cancelledInvite: LoanInviteDocument = {
      ...invite,
      status: "cancelled",
      cancelledAt: now,
      updatedAt: now,
    };

    transaction.set(
      inviteRef,
      {
        status: "cancelled",
        cancelledAt: now,
        updatedAt: now,
      },
      { merge: true },
    );
    transaction.set(
      codeRef,
      {
        status: "cancelled",
        updatedAt: now,
      },
      { merge: true },
    );
    clearSlot(transaction, slotRef, invite.lenderUid, inviteCopyId(invite), invite.catalogBookId, now);

    return serializeCancelledInvite(inviteRef.id, cancelledInvite);
  });
});

export const sendLoanReminder = withCallableErrors(async (request) => {
  const auth = requireAuth(request);
  const input = validateLoanIdInput(request.data);
  await ensureUserProfileDocument(auth);

  return db.runTransaction(async (transaction) => {
    const loanRef = db.collection(COLLECTIONS.loans).doc(input.loanId);
    const loanSnap = await transaction.get(loanRef);
    assertDomain(loanSnap.exists, "not-found", "Loan not found.");

    const loan = loanSnap.data() as LoanDocument;
    const state = makeLoanStateSnapshot(loan);
    const role = assertParticipantRole(state, auth.uid);
    assertDomain(state.isOpen, "failed-precondition", "This loan is already closed.");

    const now = Timestamp.now();
    const nextAllowedAt = nextManualReminderAllowedAt(state, MANUAL_REMINDER_COOLDOWN_HOURS);
    assertDomain(
      nextAllowedAt === null || nextAllowedAt.getTime() <= now.toDate().getTime(),
      "resource-exhausted",
      nextAllowedAt
        ? `A reminder was just sent. Try again after ${nextAllowedAt.toISOString()}.`
        : "A reminder was just sent.",
    );

    const updatedLoan: LoanDocument = {
      ...loan,
      lastManualReminderAt: now,
      lastManualReminderByUid: auth.uid,
      updatedAt: now,
    };

    transaction.set(
      loanRef,
      {
        lastManualReminderAt: now,
        lastManualReminderByUid: auth.uid,
        updatedAt: now,
      },
      { merge: true },
    );
    queueNotification(
      transaction,
      buildManualReminderNotification(loanRef.id, updatedLoan, role, auth.uid, now),
    );

    const nextReminderAllowedAt = new Date(
      now.toDate().getTime() + (MANUAL_REMINDER_COOLDOWN_HOURS * 3_600_000),
    );

    return {
      ...serializeLoan(loanRef.id, updatedLoan),
      nextReminderAllowedAtMillis: nextReminderAllowedAt.getTime(),
    };
  });
});

export const sendDueDateReminders = onSchedule(
  {
    schedule: "0 9 * * *",
    timeZone: "Etc/UTC",
  },
  async () => {
    const now = Timestamp.now();
    const threshold = Timestamp.fromDate(addDays(now.toDate(), DUE_SOON_DAYS));
    let processedLoans = 0;
    let createdNotifications = 0;
    let lastDoc: QueryDocumentSnapshot | undefined;

    while (true) {
      let query = db
        .collection(COLLECTIONS.loans)
        .where("isOpen", "==", true)
        .where("dueDate", "<=", threshold)
        .orderBy("dueDate")
        .limit(100);

      if (lastDoc) {
        query = query.startAfter(lastDoc);
      }

      const snapshot = await query.get();
      if (snapshot.empty) {
        break;
      }

      for (const loanSnap of snapshot.docs) {
        processedLoans += 1;
        const created = await db.runTransaction(async (transaction) => {
          const freshSnap = await transaction.get(loanSnap.ref);
          if (!freshSnap.exists) {
            return false;
          }

          const loan = freshSnap.data() as LoanDocument;
          const kind = selectDueReminderKind(makeLoanStateSnapshot(loan), now.toDate());
          if (!kind) {
            return false;
          }

          const reminderState = markReminderState(loan.reminderState, kind, now);
          transaction.set(
            loanSnap.ref,
            {
              reminderState,
              updatedAt: now,
            },
            { merge: true },
          );
          queueNotification(
            transaction,
            buildScheduledReminderNotification(loanSnap.id, { ...loan, reminderState }, kind, now),
          );
          return true;
        });

        if (created) {
          createdNotifications += 1;
        }
      }

      lastDoc = snapshot.docs.at(-1);
      if (!lastDoc) {
        break;
      }
    }

    logger.info("Due-date reminder sweep complete", {
      createdNotifications,
      processedLoans,
    });
  },
);

export const fanoutNotificationCreated = onDocumentCreated(
  `${COLLECTIONS.notifications}/{notificationId}`,
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) {
      return;
    }

    const notification = snapshot.data() as NotificationDocument;
    if (notification.push.status !== "pending") {
      return;
    }

    const tokensSnap = await db
      .collection(COLLECTIONS.users)
      .doc(notification.recipientUid)
      .collection("fcmTokens")
      .get();

    if (tokensSnap.empty) {
      const attemptedAt = Timestamp.now();
      await snapshot.ref.set(
        {
          push: {
            ...notification.push,
            status: "no_tokens",
            attemptedAt,
            sentAt: null,
            sentCount: 0,
            failureCount: 0,
            invalidTokenCount: 0,
            lastError: null,
          },
          updatedAt: attemptedAt,
        },
        { merge: true },
      );
      return;
    }

    const tokenEntries = tokensSnap.docs.map((tokenDoc) => ({
      ref: tokenDoc.ref,
      token: (tokenDoc.data() as FcmTokenDocument).token,
    }));

    let sentCount = 0;
    let failureCount = 0;
    const invalidRefs: DocumentReference[] = [];
    let lastError: string | null = null;

    for (const tokenBatch of chunk(tokenEntries, 500)) {
      const response = await messaging.sendEachForMulticast({
        ...buildFcmPayload(event.params.notificationId, notification),
        tokens: tokenBatch.map((entry) => entry.token),
      });

      response.responses.forEach((sendResult, index) => {
        if (sendResult.success) {
          sentCount += 1;
          return;
        }

        failureCount += 1;
        lastError = sendResult.error?.code ?? "unknown";
        if (
          sendResult.error?.code === "messaging/invalid-registration-token" ||
          sendResult.error?.code === "messaging/registration-token-not-registered"
        ) {
          const invalidEntry = tokenBatch[index];
          if (invalidEntry) {
            invalidRefs.push(invalidEntry.ref);
          }
        }
      });
    }

    if (invalidRefs.length > 0) {
      const batch = db.batch();
      for (const ref of invalidRefs) {
        batch.delete(ref);
      }
      await batch.commit();
    }

    const attemptedAt = Timestamp.now();
    await snapshot.ref.set(
      {
        push: {
          ...notification.push,
          status: sentCount > 0 ? "sent" : "failed",
          attemptedAt,
          sentAt: sentCount > 0 ? attemptedAt : null,
          sentCount,
          failureCount,
          invalidTokenCount: invalidRefs.length,
          lastError,
        },
        updatedAt: attemptedAt,
      },
      { merge: true },
    );
  },
);
