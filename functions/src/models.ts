export type LoanInviteStatus = "pending" | "accepted" | "cancelled" | "expired";
export type LoanStatus = "active" | "return_requested" | "returned";
export type BookFormat = "PHYSICAL" | "DIGITAL" | "UNKNOWN";

export type NotificationType =
  | "borrower_due_soon"
  | "borrower_due_today"
  | "lender_overdue"
  | "loan_invite_accepted"
  | "loan_reminder"
  | "return_confirmed"
  | "return_requested";

export interface BookSnapshot {
  catalogBookId: string;
  title: string;
  author: string;
  coverStoragePath: string;
  coverUrl?: string;
}

export interface CatalogBookRecord {
  id: string;
  title: string;
  author: string;
  genre: string;
  publishedYear: number;
  pages: number;
  description: string;
  coverStoragePath: string;
  coverUrl?: string;
  coverSource?: "storage" | "placeholder";
  coverOriginalUrl?: string;
  coverContentHash?: string;
  coverCachedAt?: FirebaseFirestore.Timestamp;
  searchableTitle: string;
  searchableAuthor: string;
  binding?: string;
  format?: BookFormat;
  physicalEditionIsbn13?: string;
  createdAt: FirebaseFirestore.Timestamp;
  updatedAt: FirebaseFirestore.Timestamp;
}

export interface CatalogBookAppRecord {
  title: string;
  normalizedTitle: string;
  authors: string[];
  coverUrl: string;
  coverStoragePath: string;
  genres: string[];
  publishedYear: number;
  pageCount: number;
  description: string;
  isbn13?: string;
  isbn10?: string;
  publisher?: string;
  publishedDate?: string;
  binding?: string;
  format?: BookFormat;
  physicalEditionIsbn13?: string;
  metadataSource?: "firebase" | "isbnsearch" | "manual";
  metadataSourceUrl?: string;
  metadataFetchedAt?: FirebaseFirestore.Timestamp | null;
  coverSource?: "storage" | "placeholder";
  coverOriginalUrl?: string;
  coverContentHash?: string;
  coverCachedAt?: FirebaseFirestore.Timestamp;
  createdByUid?: string;
  createdAt: FirebaseFirestore.Timestamp;
  updatedAt: FirebaseFirestore.Timestamp;
}

export interface IsbnBookMetadata {
  title: string;
  isbn13: string;
  isbn10: string | null;
  authors: string[];
  binding: string | null;
  format: BookFormat;
  physicalEditionIsbn13?: string;
  publisher: string | null;
  publishedDate: string | null;
  coverUrl: string | null;
  coverStoragePath?: string;
  coverSource?: "storage" | "placeholder";
  coverOriginalUrl?: string;
  coverContentHash?: string;
  sourceUrl: string;
}

export interface NotificationPreferences {
  pushEnabled: boolean;
  dueSoonEnabled: boolean;
  dueTodayEnabled: boolean;
  overdueEnabled: boolean;
  manualReminderEnabled: boolean;
}

export interface UserProfileDocument {
  uid: string;
  displayName: string;
  searchableDisplayName: string;
  email: string | null;
  photoURL: string | null;
  notificationPreferences: NotificationPreferences;
  createdAt: FirebaseFirestore.Timestamp;
  updatedAt: FirebaseFirestore.Timestamp;
  lastSeenAt: FirebaseFirestore.Timestamp;
}

export interface LoanInviteDocument {
  code: string;
  codeKey: string;
  copyId: string;
  catalogBookId: string;
  book: BookSnapshot;
  lenderUid: string;
  lenderDisplayName: string;
  borrowerNameHint: string | null;
  borrowerUid: string | null;
  borrowerDisplayName: string | null;
  status: LoanInviteStatus;
  dueDate: FirebaseFirestore.Timestamp;
  expiresAt: FirebaseFirestore.Timestamp;
  loanId: string | null;
  createdAt: FirebaseFirestore.Timestamp;
  updatedAt: FirebaseFirestore.Timestamp;
  acceptedAt: FirebaseFirestore.Timestamp | null;
  cancelledAt: FirebaseFirestore.Timestamp | null;
  expiredAt: FirebaseFirestore.Timestamp | null;
}

export interface ReminderState {
  borrowerDueSoonSentAt: FirebaseFirestore.Timestamp | null;
  borrowerDueDateSentAt: FirebaseFirestore.Timestamp | null;
  lenderOverdueSentAt: FirebaseFirestore.Timestamp | null;
}

export interface LoanDocument {
  inviteId: string;
  copyId: string;
  catalogBookId: string;
  book: BookSnapshot;
  lenderUid: string;
  lenderDisplayName: string;
  borrowerUid: string;
  borrowerDisplayName: string;
  participants: [string, string];
  status: LoanStatus;
  isOpen: boolean;
  dueDate: FirebaseFirestore.Timestamp;
  createdAt: FirebaseFirestore.Timestamp;
  acceptedAt: FirebaseFirestore.Timestamp;
  updatedAt: FirebaseFirestore.Timestamp;
  returnRequestedAt: FirebaseFirestore.Timestamp | null;
  returnRequestedByUid: string | null;
  returnedAt: FirebaseFirestore.Timestamp | null;
  returnConfirmedByUid: string | null;
  lastManualReminderAt: FirebaseFirestore.Timestamp | null;
  lastManualReminderByUid: string | null;
  reminderState: ReminderState;
}

export interface NotificationPushState {
  status: "pending" | "sent" | "no_tokens" | "failed";
  attemptedAt: FirebaseFirestore.Timestamp | null;
  sentAt: FirebaseFirestore.Timestamp | null;
  sentCount: number;
  failureCount: number;
  invalidTokenCount: number;
  lastError: string | null;
}

export interface NotificationDocument {
  recipientUid: string;
  actorUid: string | null;
  type: NotificationType;
  title: string;
  body: string;
  loanId: string | null;
  inviteId: string | null;
  catalogBookId: string | null;
  data: Record<string, string>;
  isRead: boolean;
  readAt: FirebaseFirestore.Timestamp | null;
  createdAt: FirebaseFirestore.Timestamp;
  updatedAt: FirebaseFirestore.Timestamp;
  push: NotificationPushState;
}

export interface LoanInviteCodeDocument {
  code: string;
  inviteId: string;
  status: LoanInviteStatus;
  expiresAt: FirebaseFirestore.Timestamp;
  createdAt: FirebaseFirestore.Timestamp;
  updatedAt: FirebaseFirestore.Timestamp;
}

export interface FcmTokenDocument {
  token: string;
  platform: "android" | "ios" | "web" | "unknown";
  createdAt: FirebaseFirestore.Timestamp;
  updatedAt: FirebaseFirestore.Timestamp;
  lastSeenAt: FirebaseFirestore.Timestamp;
  appVersion?: string;
}

export interface WishlistDocument {
  addedAt: FirebaseFirestore.Timestamp;
}

export interface UserBookDocument {
  ownerId: string;
  catalogBookId: string;
  status: "AVAILABLE" | "ARCHIVED";
  createdAt: FirebaseFirestore.Timestamp;
  updatedAt: FirebaseFirestore.Timestamp;
}

export interface LendingSlotDocument {
  lenderUid: string;
  copyId: string;
  catalogBookId: string;
  state: "available" | "invite_pending" | "loan_active";
  currentInviteId: string | null;
  currentLoanId: string | null;
  updatedAt: FirebaseFirestore.Timestamp;
}
