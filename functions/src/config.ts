export const REGION = "us-central1";
export const INVITE_EXPIRY_DAYS = 7;
export const MIN_LOAN_HOURS = 1;
export const MAX_LOAN_DAYS = 180;
export const DUE_SOON_DAYS = 2;
export const MANUAL_REMINDER_COOLDOWN_HOURS = 6;

export const COLLECTIONS = {
  catalog: "catalog",
  catalogBooks: "catalogBooks",
  isbnLookupCache: "isbnLookupCache",
  isbnLookupThrottle: "isbnLookupThrottle",
  lendingSlots: "lendingSlots",
  loanInviteCodes: "loanInviteCodes",
  loanInvites: "loanInvites",
  loans: "loans",
  notifications: "notifications",
  userBooks: "userBooks",
  users: "users",
} as const;
