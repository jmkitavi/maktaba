# Maktaba Firebase backend

All callable functions require Firebase Authentication.

## Collections and shared libraries

Sharing is default-off. There is no migration, automatic opt-in or backfill.
Existing `userBooks`, loans, profiles and `users/{uid}/wishlist` stay private.
Clients cannot write sharing settings, public mirrors or custom collections directly.
Normal legacy profile-field updates remain permitted without allowing sharing-field
changes or deletion. Custom collections never replace Want to Read storage.

Callable payloads (all require Authentication):

| Endpoint | Request | Response |
| --- | --- | --- |
| `setLibrarySharing` | `{libraryShared: boolean, libraryName: string}` | `{settings}` |
| `setTitleVisibility` | `{catalogBookId: string, hidden: boolean}` | `{settings}` |
| `createCollection` | `{name: string, requestId: string}` | `{collectionId}` |
| `updateCollection` | `{collectionId, name?, catalogIds?, shared?}` | `{collectionId}` |
| `deleteCollection` | `{collectionId}` | `{deleted: true}` |
| `reportLibrary` | `{ownerUid, reason, details, requestId}` | `{reportId}` |

`settings` contains `libraryShared`, `libraryName`, `hiddenCatalogIds` and
`publicationEpoch`. Names are trimmed, control-character-free, 1–40 characters for
libraries and 1–60 for collections. Collections allow up to 500 unique catalogue
IDs; all selected titles must exist, but ownership is deliberately not required.
Up to 200 owner-global hidden catalogue IDs are retained independently of copies
and private list membership. Hiding excludes a title from every public owner view,
including shared lists, cover mosaics and visible counts; it does not affect other
owners. Digital titles are included with their format, but are not lendable.
Whole-library mirrors exclude borrowed copies and aggregate duplicate owned copies;
any free copy makes a title available. Accepted active/return-requested open loans
make the corresponding copy unavailable; invitation previews do not.

Create/report request IDs are retained-draft retry keys (1–128 characters, no `/`,
`.` or `..`). Reuse the same ID and payload after a lost response. Receipts are
private/server-only, persist after collection deletion/library opt-out and prevent
duplicate commits. Reusing an ID with different input returns `already-exists`.
Report reasons: `inappropriate_name`, `harassment`, `impersonation`, `spam`, `other`.
Details are optional, at most 500 characters. Reports are private server-written
`libraryReports`; successful submission promises no moderation outcome or timeline.
New reports require an active library, cannot target oneself, and are limited to
one per minute and five per rolling 24-hour window per reporter. Identical committed
retries bypass rate limits and remain successful after the target opts out.

### Publication authorization and queries

Mutations transactionally update private authoritative state, increment the owner
epoch once and revoke `publicLibraryState/{uid}` while replacing directory/reader
summaries with unpublished, title-free records. The durable private
`publicationJobs/{uid}` event retries interrupted work. Rebuilds stage sanitized
children in chunks of 350 under the same epoch, then compare the complete current
source fingerprint in a transaction before publishing summaries and the gate at
that same epoch. Chunk writes are fenced by epoch, pending state and build
fingerprint; obsolete/out-of-order/retried events cannot republish stale titles.
If a source changes mid-build, retry at a new fenced epoch. Gate documents contain
only `published`, `publicationEpoch`, `updatedAt` (never hidden IDs).

Old descendants can remain stored, but rules deny every orphan, inactive parent
and stale epoch. Opting out of the whole library does not opt out of independently
shared collections. Collection metadata is catalogue-only and has no availability,
ownership, lender, dates or loan fields.

Directory queries use `sharedLibraries`, `where("published", "==", true)`,
`orderBy("updatedAt", "desc")`, `limit(20)` and `startAfter(lastSnapshot)` for further
pages. Search adds `where("searchPrefixes", "array-contains", normalizedQuery)`.
Normalize with NFKD, strip combining marks, trim, lowercase and collapse whitespace. Prefixes cover whole
library/reader names and individual words, up to 60 characters and 500 unique
prefixes. It is prefix search, not arbitrary substring search.
Directory LIST authorization uses stored server-owned publication fields, not a
gate read per result.

Per-owner views must first read `publicLibraryState/{uid}` and the active parent.
Books require `where("publicationEpoch", "==", gate.publicationEpoch)`, optional
`orderBy("addedAt", "desc")`, and `limit(60)`. Shared reader collections additionally
require `where("published", "==", true)`, with optional `orderBy("updatedAt", "desc")`
and `limit(60)`. Use cursors rather than offsets. `sharedReaders` and gates do not
support directory listing. Never load another reader's private profile.
On revocation, authorization errors, sign-out or unvalidated/offline gate state,
clear rendered public associations. The gate can be active for independently
shared lists while the whole-library parent is unavailable.

Relevant Firestore indexes are in `firestore.indexes.json`: directory publication/
updatedAt; directory publication/searchPrefixes/updatedAt; public books epoch/
addedAt; public collections published/epoch/updatedAt; collection-group catalogue
membership for catalogue metadata-change fan-out. Existing indexes are preserved.

### Local verification (no live credentials)

```bash
cd functions
. "$HOME/.nvm/nvm.sh" && nvm use 22
npm run lint && npm run typecheck && npm test
```

The real Firestore rules/service integration suite is separate from pure unit
tests. It refuses to run unless `FIRESTORE_EMULATOR_HOST` explicitly targets
localhost/127.0.0.1. Against an already running, isolated local Firestore emulator:

```bash
cd functions
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run test:rules
```

Or, from the repository root, the coordinator can start an isolated run (check
port availability first; do not reuse an unknown emulator):

```bash
firebase emulators:exec --only firestore --project demo-maktaba-sharing-rules \
  'cd functions && npm run test:rules'
```

The suite uses only project `demo-maktaba-sharing-rules` and clears that emulator
project between cases. It covers real directory/shelf cursor queries, owner-only
legacy data, server-write restrictions, synchronous barriers, stale epochs/orphans,
global hiding/counts/covers, independent list CRUD, private Want to Read, and report
receipts/rate limits. It must not be run against live data. Pure `npm test` does not
claim to execute these checks.

The authenticated callable suite uses all four local emulators and the exact
Android configuration project, `maktaba-2e21d`. It creates random local Auth users/
passwords, obtains real emulator ID tokens, sends HTTP callable requests and reads
Firestore through authenticated client SDKs. Every emulator host and project is
checked before Admin fixture access. No default test account, live metadata lookup,
real FCM token, live credentials or production data are required.

With Node 22, Java 17, and ports 9099/8080/5001/9199 free, from the repository root:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export PATH="$JAVA_HOME/bin:$PATH"
npm --prefix functions run build
npx --yes --package firebase-tools@14.27.0 firebase emulators:exec \
  --only auth,firestore,functions,storage --project maktaba-2e21d \
  'cd functions && GCLOUD_PROJECT=maktaba-2e21d FUNCTIONS_EMULATOR_HOST=127.0.0.1:5001 npm run test:callables'
```

This separate suite covers all six new HTTP callables, absent authentication,
ownership/validation, concurrent create/report retry receipts, independently shared
zero-visible lists, real borrowed-list metadata, owner-global hiding, report privacy/
cooldown, normal trigger delivery, concurrent source/refresh convergence and missing
library/list/reader parents while the owner gate remains active. It also executes
real invitation creation/resolution/acceptance, six-hour reminder cooldown,
two-party return authorization, private shelf outcomes and local no-token
notification fan-out. It does not simulate arbitrary function crashes or validate
production index readiness.

Scoped function deployment inventory (coordinator only, after local validation):
`setLibrarySharing`, `setTitleVisibility`, `createCollection`, `updateCollection`,
`deleteCollection`, `reportLibrary`, `onSharingUserBookWritten`,
`onSharingLoanWritten`, `onSharingProfileWritten`, `onSharingCollectionWritten`,
`onSharingCatalogWritten`, `onPublicationJobWritten`; plus compatible rules/indexes.
The publication-job trigger is required for durable interrupted-build recovery.
Do not blanket-deploy unrelated functions or backfill existing accounts.

### Compatible lending metadata and guards

Invitation creation/resolution/cancellation and loan responses now add `format`
(`PHYSICAL`, `DIGITAL`, `UNKNOWN`) and nullable `binding`. Existing invitation/loan
snapshots without format are resolved from trusted catalogue records; missing
metadata remains UNKNOWN, never a fake PHYSICAL default. Pending digital invites
return `canAccept: false`, and acceptance revalidates digital eligibility if
catalogue format changes after invitation creation. The legacy UNKNOWN lending
policy is unchanged.

`confirmReturn` checks participant authorization before even its already-returned
idempotent response. Private loan-list queries may use the server-managed
`participants array-contains uid`; nonparticipants still cannot read loans.
Optional catalogue cover-cache fields are omitted rather than written as undefined
when an app-only catalogue record is first used to create a lending invitation.

These compatible coupled changes require scoped deployment of existing functions
`createLoanInvite`, `resolveLoanInvite`, `acceptLoanInvite`, `requestReturn`,
`confirmReturn`, `cancelLoanInvite`, `sendLoanReminder`, plus the compatible rules.

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
  "inviteCode": "ABCD-EF23",
  "dueAtMillis": 1782129600000,
  "expiresAtMillis": 1782734400000
}
```

Notes:

- `loanId` is created immediately and reused as the eventual `/loans/{loanId}` document ID.
- `copyId` is the top-level `/userBooks/{copyId}` document ID owned by the lender.
- The backend resolves `catalogBookId` from that copy record, so copy IDs no longer need to equal catalog IDs.
- Resumed invitations return the same due date and expiry fields.

### `removeBookFromLibrary`

Request:

```json
{ "copyId": "copy_123" }
```

Response:

```json
{ "copyId": "copy_123", "removed": true }
```

Removal is rejected while the copy has a pending lending invitation or an open
loan. Pending invitations must be cancelled first. Client-side Firestore deletes
for `userBooks` are denied.

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
