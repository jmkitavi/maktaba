# Maktaba Firebase backend

All callable functions require Firebase Authentication.

## Project setup

1. Enable Email/Password in Firebase Authentication.
2. Use Node.js 22 for Functions builds and Java 21+ for the Emulator Suite.
3. Deploy with an explicit project:

```bash
firebase deploy --project maktaba-2e21d
```

No Admin SDK key is committed; deployment uses the operator's Firebase credentials.

## Cover caching

Trusted ISBN-provider covers are copied into Firebase Storage under
`catalog-covers/`. Catalog documents store the stable Storage URL, object path,
content hash, original provider URL, and cache timestamp. Clients can read
catalog covers but cannot create, update, or delete them.

Backfill existing catalog records with a dry run first:

```bash
cd functions
npm run backfill:covers -- --project maktaba-2e21d \
  --bucket maktaba-2e21d.firebasestorage.app --dry-run
npm run backfill:covers -- --project maktaba-2e21d \
  --bucket maktaba-2e21d.firebasestorage.app
```

The backfill is resumable and skips already cached covers. Use `--limit <n>`
for a bounded run or `--force` to replace an existing cached cover.

For an Android emulator backed by the local Firebase Emulator Suite:

```bash
firebase emulators:start --project demo-maktaba
cd android
./gradlew assembleDebug -PuseFirebaseEmulators=true
```

The flag is opt-in; ordinary debug and release builds continue using the configured production project.

## Canonical callable contracts

Dates in the loan flow use Unix epoch milliseconds.

### eBook metadata

`lookupBookByIsbn` metadata and `addBookToLibrary` responses include the original
`binding`, a server-derived `format` (`PHYSICAL`, `DIGITAL`, or `UNKNOWN`), and
an optional `physicalEditionIsbn13` when a conservative known-edition mapping
exists. `createLoanInvite` rejects `DIGITAL` catalog editions with
`failed-precondition`; older catalog records are classified from `binding` and
otherwise remain `UNKNOWN`.

### `createUserProfileIfNeeded`

Request:

```json
{ "displayName": "Jamie Kitavi" }
```

Response:

```json
{
  "created": true,
  "profile": {
    "uid": "user_uid",
    "displayName": "Jamie Kitavi",
    "email": "reader@example.com",
    "photoURL": null,
    "updatedAt": "2026-08-19T20:00:00.000Z"
  }
}
```

### `createLoanInvite`

Request:

```json
{
  "copyId": "copy_123",
  "dueAtMillis": 1782129600000,
  "borrowerDisplayName": "Maya Chen"
}
```

Response:

```json
{
  "loanId": "generated_workflow_id",
  "inviteCode": "ABCD-EF23"
}
```

Notes:

- `loanId` is created immediately and reused as the eventual `/loans/{loanId}` document ID.
- `copyId` is the top-level `/userBooks/{copyId}` document ID owned by the lender.
- The backend resolves `catalogBookId` from that copy record, so copy IDs no longer need to equal catalog IDs.

### `resolveLoanInvite`

Request:

```json
{ "inviteCode": "ABCD-EF23" }
```

Response:

```json
{
  "loanId": "generated_workflow_id",
  "copyId": "copy_123",
  "catalogBookId": "catalog_123",
  "title": "Example Book",
  "author": "Example Author",
  "authors": ["Example Author"],
  "coverUrl": "https://firebasestorage.googleapis.com/...",
  "lenderDisplayName": "Ava Thompson",
  "dueAtMillis": 1782129600000,
  "status": "pending",
  "canAccept": true
}
```

### Common loan response

Returned by `acceptLoanInvite`, `requestReturn`, and `confirmReturn`:

```json
{
  "loanId": "generated_workflow_id",
  "copyId": "copy_123",
  "catalogBookId": "catalog_123",
  "title": "Example Book",
  "author": "Example Author",
  "authors": ["Example Author"],
  "coverUrl": "https://firebasestorage.googleapis.com/...",
  "lenderDisplayName": "Ava Thompson",
  "borrowerDisplayName": "Maya Chen",
  "dueAtMillis": 1782129600000,
  "status": "active",
  "isOpen": true,
  "acceptedAtMillis": 1781956800000,
  "returnRequestedAtMillis": null,
  "returnedAtMillis": null
}
```

### `acceptLoanInvite`

Request:

```json
{ "loanId": "generated_workflow_id" }
```

### `requestReturn`

Request:

```json
{ "loanId": "generated_workflow_id" }
```

### `confirmReturn`

Request:

```json
{ "loanId": "generated_workflow_id" }
```

### `cancelLoanInvite`

Request:

```json
{ "loanId": "generated_workflow_id" }
```

Response:

```json
{
  "loanId": "generated_workflow_id",
  "copyId": "copy_123",
  "catalogBookId": "catalog_123",
  "title": "Example Book",
  "author": "Example Author",
  "authors": ["Example Author"],
  "coverUrl": "https://firebasestorage.googleapis.com/...",
  "lenderDisplayName": "Ava Thompson",
  "inviteCode": "ABCD-EF23",
  "status": "cancelled",
  "dueAtMillis": 1782129600000,
  "expiresAtMillis": 1782561600000,
  "cancelledAtMillis": 1781956800000,
  "expiredAtMillis": null
}
```

### `sendLoanReminder`

Request:

```json
{ "loanId": "generated_workflow_id" }
```

Response:

```json
{
  "loanId": "generated_workflow_id",
  "copyId": "copy_123",
  "catalogBookId": "catalog_123",
  "title": "Example Book",
  "author": "Example Author",
  "authors": ["Example Author"],
  "coverUrl": "https://firebasestorage.googleapis.com/...",
  "lenderDisplayName": "Ava Thompson",
  "borrowerDisplayName": "Maya Chen",
  "dueAtMillis": 1782129600000,
  "status": "active",
  "isOpen": true,
  "acceptedAtMillis": 1781956800000,
  "returnRequestedAtMillis": null,
  "returnedAtMillis": null,
  "nextReminderAllowedAtMillis": 1781978400000
}
```

## Background functions

- `createUserProfileOnAuthCreate`: creates the `/users/{uid}` profile document. New accounts start with an empty personal library and wishlist.
- `sendDueDateReminders`: scheduled due-soon/due-today/overdue notification creation.
- `fanoutNotificationCreated`: sends FCM push notifications and deletes invalid tokens.
