export type DomainErrorCode =
  | "already-exists"
  | "failed-precondition"
  | "internal"
  | "invalid-argument"
  | "not-found"
  | "permission-denied"
  | "resource-exhausted"
  | "unauthenticated";

export class DomainError extends Error {
  constructor(
    public readonly code: DomainErrorCode,
    message: string,
  ) {
    super(message);
    this.name = "DomainError";
  }
}

export function assertDomain(
  condition: unknown,
  code: DomainErrorCode,
  message: string,
): asserts condition {
  if (!condition) {
    throw new DomainError(code, message);
  }
}
