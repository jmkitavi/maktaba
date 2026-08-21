import type {
  LendingSlotDocument,
  LoanDocument,
  LoanInviteDocument,
} from "../models";

export type BookRemovalBlock = "pending_invite" | "active_loan" | null;

export function bookRemovalBlock(
  slot: LendingSlotDocument | null,
  invite: LoanInviteDocument | null,
  loan: LoanDocument | null,
  now: Date = new Date(),
): BookRemovalBlock {
  if (
    slot?.state === "invite_pending" &&
    invite?.status === "pending" &&
    invite.expiresAt.toDate().getTime() > now.getTime()
  ) {
    return "pending_invite";
  }
  if (slot?.state === "loan_active" && loan?.isOpen) {
    return "active_loan";
  }
  return null;
}
