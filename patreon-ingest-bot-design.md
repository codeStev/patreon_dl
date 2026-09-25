# Patreon Ingest Bot — Architecture Design

Status: planning only, no code written yet.

## Problem

Multiple Patreon subscriptions (Nomnom, Bulkamancer, Wicked) distribute 3D-printable
STL models via DMs, each with a different message format and distribution mechanism.
Goal: a self-hosted, dockerized tool that ingests these, tracks what's owned, and
downloads files — with easy support for adding new providers later.

## Stack

- Spring Boot 4.1, Java 25 (updated from the original 3.5/21 draft once
  implementation started — versions checked live rather than assumed)
- Gradle multi-module build: `ingest-core` (library, the three bounded
  contexts + SPIs), `ingest-providers-default` (library, the OSS-contributed
  parsers), `app` (the only module with the Spring Boot plugin — depends on
  both, produces the bootable jar)
- Postgres 18 (metadata/state)
- rclone (Google Drive transfers)
- Playwright (Gumroad checkout automation)
- React frontend (overview UI)
- Deployed via docker compose; CI builds and publishes a combined image to a
  private registry (see Deployment topology and CI/CD)
- 10TB HDD storage on the server — I/O-sensitive, not SSD

## Component overview

```
[Ingestion] IMAP Mailbox
    │ poll (Spring @Scheduled)
    ▼
[Ingestion] PollMailboxUseCase ──records──▶ processed_email (audit trail)
    │ CreatorMessageParser.supports()/parse()
    ▼
[Ingestion → Acquisition] RegisterParsedItemsUseCase
    │ persists
    ▼
[Acquisition] download_source / download_item  (DISCOVERED)
    │
    ▼
[Acquisition] ClaimSourceUseCase — GUMROAD → ClaimPort (GumroadClaimAdapter),
              DRIVE/MMF → no ClaimPort registered → implicit claim
    │
    ▼
[Acquisition] download_source (CLAIMED)
    │
    ▼
[Fulfillment] DownloadQueueService (single choke point, policy + concurrency + bandwidth + hours)
    │ dispatch by source_type
    ▼
[Fulfillment] SourceDownloader (Drive / MMF)
    │
    ▼
Filesystem (HDD)  +  [Acquisition] download_item (DOWNLOADED, per file — Fulfillment reports back)

[Acquisition] FolderSyncJob (active, scheduled, independent of new email arriving)
    └─▶ RemoteFileListingPort (rclone lsjson) diff on non-quiet CLAIMED DRIVE/MMF sources
        → new download_item rows (PENDING) → picked up by Fulfillment's next queue tick

[Acquisition] LinkHealthCheckJob (low-frequency, independent schedule)
    └─▶ re-verifies quiet CLAIMED sources, flags link_dead

[Admin API, driving adapter] React UI ──REST──▶ queries across Acquisition + Fulfillment ──▶ Postgres
```

Everything left of `DownloadQueueService` only ever writes DISCOVERED/CLAIMED
state — no component upstream of the queue touches the filesystem. This keeps
the "single choke point" guarantee in the Performance section actually true:
there's exactly one place bytes hit disk. The `[Context]` tags above map
directly onto the three bounded contexts detailed in the next section.

## Hexagonal architecture & bounded contexts

Honest tradeoff up front: ports/adapters and context boundaries are real
ceremony for a single-user hobby tool — they earn their keep when
infrastructure needs to be swappable or multiple people work on separate
pieces without stepping on each other. Here the concrete payoff is narrower
but real: it forces every pluggable piece (parser, claimer, downloader) to
declare an honest, minimal interface, and doing that exercise is what
surfaces the `GumroadRedeemer` bug corrected below. Worth doing for that. Not
worth going further than this — no event bus, no CQRS, no per-context
database.

### Bounded contexts

Three, structured as a modular monolith: one Spring Boot app, one Postgres
database, but each context owns its own tables exclusively — the others
reach it only through its application-service interface, never through its
JPA entities or a cross-context SQL join.

```
┌───────────────┐  ParsedItems  ┌────────────────────┐  results  ┌───────────────┐
│   Ingestion    │──────────────▶│    Acquisition      │◀──────────│  Fulfillment   │
│ (supporting)   │               │   (core domain)      │  polled  │  (supporting)  │
└───────────────┘               └────────────────────┘  by tick  └───────────────┘
                                           ▲
                                           │ read-model queries, both contexts
                                  ┌──────────────────┐
                                  │    Admin API       │
                                  │ (driving adapter)  │
                                  └──────────────────┘
```

- **Acquisition is the core domain** — it owns `DownloadSource`,
  `DownloadItem`, `ProviderSettings`, and the claim/urgency rules that are the
  actual reason this project exists. Ingestion and Fulfillment are supporting
  subdomains that conform to Acquisition's vocabulary (Acquisition upstream,
  both others downstream) — neither gets its own copy of "what a download
  item is."
- **Ingestion** turns raw email into `ParsedItem`s and hands them to
  Acquisition. It has no idea what happens after that.
- **Fulfillment** turns claimed items into bytes on disk. It has no idea
  where an item came from — it asks Acquisition "what's pending and eligible
  under current policy," fetches it, reports back.
- **Admin API isn't a fourth bounded context** — it has no domain language of
  its own. It's a driving adapter that composes read queries across
  Acquisition and Fulfillment for the React UI.

Cross-context calls are plain in-process interface calls — no message bus, no
events. Fulfillment's queue tick just asks Acquisition "give me PENDING items
eligible right now" on each tick; there's no benefit to Acquisition pushing
that Fulfillment doesn't already get a tick later anyway.

### Ingestion context

```
┌──────────────────────────── Ingestion ─────────────────────────────┐
│ in  ▶ PollMailboxUseCase (triggered by scheduler adapter)           │
│         │                                                            │
│         ▼                                                            │
│  domain: EmailMessage, ParsedItem (value objects)                    │
│          CreatorMessageParser* — domain strategies, not adapters     │
│                                                                        │
│ out ▶ MailboxPort ──────────▶ ImapMailboxAdapter (Jakarta Mail)      │
│ out ▶ ProcessedEmailRepository ─▶ Postgres (processed_email)         │
│ out ▶ AcquisitionPort ──────▶ Acquisition.RegisterParsedItemsUseCase │
└────────────────────────────────────────────────────────────────────┘
```

`CreatorMessageParser` implementations are domain strategies, not
infrastructure adapters, even though they're Spring-registered beans the same
way adapters are. The distinction matters: a *parser* encodes a business rule
("Nomnom's July email means these models exist"), while an *adapter*
translates to/from a technology (IMAP, HTTP, a filesystem). The existing
`ParserRegistry` strategy list is already the right shape — don't turn it
into a port/adapter pair, that would be the wrong tool here.

### Acquisition context (core domain)

```
┌───────────────────────────── Acquisition ─────────────────────────────┐
│ in ▶ RegisterParsedItemsUseCase      (from Ingestion)                  │
│ in ▶ RegisterDiscoveredFilesUseCase  (from FolderSyncJob, scheduled)   │
│ in ▶ ClaimSourceUseCase              (eager claim on registration)     │
│ in ▶ UpdateProviderSettingsUseCase   (from Admin API)                  │
│ in ▶ QueryOverviewUseCase            (from Admin API / Fulfillment)    │
│ in ▶ RunLinkHealthCheckUseCase       (scheduler)                       │
│ in ▶ MarkDownloadedCommand / MarkFailedCommand (from Fulfillment)      │
│        │                                                                │
│        ▼                                                                │
│  domain: DownloadSource, DownloadItem, ProviderSettings (aggregates)    │
│          ClaimStatus / ItemStatus / SourceType / DownloadPolicy (VOs)   │
│          rule: only legal transitions — DISCOVERED→CLAIMED;             │
│                PENDING→DOWNLOADED/FAILED                                 │
│                                                                            │
│ out ▶ DownloadSourceRepository, DownloadItemRepository,                 │
│       ProviderSettingsRepository ─▶ Postgres                            │
│ out ▶ ClaimPort (per SourceType) ─▶ GumroadClaimAdapter (Playwright) /  │
│       no adapter registered = implicit claim (DRIVE, MMF)               │
│ out ▶ RemoteFileListingPort ─▶ RcloneListingAdapter (`rclone lsjson` —  │
│       backs both FolderSyncJob and LinkHealthCheckJob)                  │
└──────────────────────────────────────────────────────────────────────┘
```

This is where the `SourceDownloader` split below pays off directly:
`ClaimPort` ("does owning this source require an external action?") is now a
separate capability from `SourceDownloader` ("fetch the bytes"). Under the
original single-interface sketch, `GumroadRedeemer` was registered as a
`SourceDownloader` even though its own description says it never downloads
anything — that was a real bug in the extension-point design, not just a
naming quibble, because it meant the interface's contract lied about what
implementing it means. `GumroadClaimAdapter` implementing `ClaimPort` instead
fixes that.

`FolderSyncJob` and `LinkHealthCheckJob` both live in Acquisition, not
Fulfillment — both are about discovering or verifying what Acquisition owns
(new files, dead links), the same responsibility category as parsing an
email. Neither one touches the filesystem.

### Fulfillment context

```
┌───────────────────────────── Fulfillment ─────────────────────────────┐
│ in ▶ RunDownloadQueueTickUseCase   (scheduler)                          │
│ in ▶ TriggerManualDownloadUseCase  (Admin API "download now")           │
│        │                                                                 │
│        ▼                                                                 │
│  domain: DownloadDispatchPolicy (is this item allowed to run now, given  │
│          concurrency/bandwidth/hours?), AppSettings (aggregate)          │
│                                                                            │
│ out ▶ AcquisitionPort ─▶ Acquisition.QueryOverviewUseCase (pending,      │
│       eligible items) + MarkDownloadedCommand / MarkFailedCommand        │
│ out ▶ SourceDownloader (per SourceType) ─▶ RcloneDriveDownloadAdapter    │
│       (DRIVE), MmfDownloadAdapter (MMF — TBD, manual for now)            │
│ out ▶ AppSettingsRepository ─▶ Postgres (app_settings)                   │
└──────────────────────────────────────────────────────────────────────┘
```

Fulfillment deliberately owns no copy of `DownloadItem` — Acquisition stays
the single source of truth for status, so there's no dual-write or
reconciliation problem between the two contexts. Fulfillment is closer to a
stateless execution engine than a second domain model.

### Layering: DTO / domain / persistence

Within each context, three shapes are kept distinct only where they're
genuinely different — a separate domain-model layer beyond the JPA entities
themselves would be pure ceremony for logic this thin:

- **Parser output ≠ persisted entity.** `ParsedItem` (Ingestion's domain
  value object) is not `DownloadItem` (Acquisition's JPA entity) — they
  don't even live in the same context, let alone the same class. The
  original interface sketch returning `List<DownloadItem>` from `parse()`
  conflated "what a parser extracted from text" with "a row that exists in
  Postgres," which doesn't exist yet at parse time (no id, no `source_id`,
  no status).
- **REST responses ≠ JPA entities.** The Admin API adapter never serializes
  an entity straight to JSON — a lazy Hibernate association leaking into
  Jackson is a classic own-goal, and it would silently couple the UI
  contract to schema changes. Dedicated response DTOs (e.g. an overview row
  that already joins in `source_bytes`) are assembled in the Admin API
  adapter from the two contexts' query use cases.
- **Everything else operates on entities directly** — `ClaimSourceUseCase`,
  `DownloadDispatchPolicy`, the repositories. No mapper layer between a
  context's own domain and its own persistence; the JPA entity *is* the
  domain object there, because nothing here needs a richer model underneath.

## Ingestion

- **Email-based, not browser scraping.** Patreon is configured to send DM
  notification emails; the app polls a mailbox via IMAP (Jakarta Mail).
- Each incoming email is routed to a parser based on sender address / subject.
- Poll on a fixed interval (e.g. every 5 min via `@Scheduled`) — IMAP IDLE would
  give near-instant pickup but isn't worth the added connection-management
  complexity at this mail volume.
- Idempotency: track the IMAP UID of every message seen, not just "mark as
  read" (a restart, or a flag reset by another mail client, must never cause
  reprocessing or drops). See `processed_email` in the data model.
- If no `CreatorMessageParser.supports()` matches, or a matching parser throws,
  log it to `processed_email` with the failure reason and move on — one
  malformed or unrecognized email must never stop the poll loop. This table
  doubles as the signal for "a provider changed their format" or "you need a
  new parser," surfaced in the admin UI rather than discovered by noticing a
  model never showed up.

## Extension points (the core design decision)

Two **independent** extension points, because "new provider" can mean either
a new message format, a new distribution mechanism, or both:

### 1. `CreatorMessageParser` — one per creator

```java
public interface CreatorMessageParser {
    boolean supports(String fromAddress, String subject);
    List<ParsedItem> parse(String plainTextBody, LocalDate receivedAt);
}
```

`ParsedItem` — not `DownloadItem` — is what a parser returns: model name,
source url, category, month, claim type. It's a plain value object with no
id, no `source_id`, no status, because none of those exist yet at parse time.
An ingest service maps `List<ParsedItem>` onto persisted `download_source`/
`download_item` rows, applying the `(creator, source_url)` and
`(source_id, remote_file_id)` dedup rules from the data model. See
"Layering" below for why this distinction matters and where (if anywhere)
else it applies.

Registered as `@Component` beans; Spring auto-injects all implementations into a
list (`ParserRegistry`) — adding a provider with an existing distribution
mechanism means writing one new class, nothing else changes.

Known formats:
- **NomnomParser**: bracketed/plain section headers (`[JULY MODELS]`,
  `AUGUST Extra Models:`), each header owns a run of bullet lines (`-` or `▫️`)
  followed by one trailing Google Drive URL that applies to all of them.
- **BulkamancerParser**: `------ X ------` banner-delimited sections. Recap/loyalty
  sections point at a MyMiniFactory shared library (no per-model URL — record
  model names with `sourceType=MMF`). Also handles standalone single-model DMs
  matching `Name: <drive-url>`.
- **WickedParser**: numbered `N. Model Name:\n<gumroad-url>` lines (noise-heavy
  marketing email — anchor on structure, ignore prose), plus one bulk "term"
  Google Drive folder link that supersedes the individual Gumroad links for
  actual file content. Gumroad links are used for claiming ownership, not
  downloading (see below).

### Testing new parsers

Each `CreatorMessageParser` gets fixture-based unit tests: a real (anonymized
if needed) sample email body saved per known format variant under
`src/test/resources/emails/{provider}/*.txt`, asserted against the expected
`List<ParsedItem>`. "Add a provider = one new class" should hold end-to-end
including its tests — drop the sample email in, write the parser + test,
register the bean, done.

### 2a. `ClaimPort` — secures ownership, one per source type that needs it

```java
public interface ClaimPort {
    SourceType supports(); // GUMROAD — DRIVE/MMF have no ClaimPort at all
    ClaimOutcome claim(DownloadSource source);
    // Claimed(receiptUrl) | AlreadyOwned | NeedsManual(reason) | RetryLater(reason)
}
```

- **GumroadClaimAdapter**: Playwright-driven headless browser. Gumroad
  checkout is multi-step and can't be done with a plain HTTP call. Flow and
  selectors captured from a real Wicked redemption (2026-09-25). Every class
  on these pages is a Tailwind utility, so match on role/label/text/itemprop,
  never on CSS classes:
  1. Open the product URL. The offer code is its last path segment
     (`/l/SteveAustinS/3weab9n`). **Pre-check before any click:**
     `[itemprop=price]` must have `content="0"` and the `[role=status]` box
     must say "100% off". Otherwise the code has expired, so abort without
     checkout. (Don't use `meta[product:price:amount]`; that's the
     undiscounted list price.)
  2. Click `getByRole('link', {name: 'I want this!'}).first()`. It's an
     `<a>` and appears twice. This redirects to `gumroad.com/checkout`.
  3. Wait for the discount to apply. It lands a moment *after* the checkout
     page loads, so use an auto-retrying assertion (~15s) on the value next to
     the `h4` "Total" becoming `US$0`, never a fixed sleep. Timeout =
     retryable failure, source stays DISCOVERED.
  4. Only after that, run the guards: exactly one cart line item (one
     "Remove" button; the cart persists across visits, so stale items would be
     checked out too), and the submit button reads **"Get"** (free
     checkout). Anything else aborts.
  5. Fill `getByLabel('Email address')` (its id is React-generated and
     unstable) and click `getByRole('button', {name: 'Get', exact: true})`.
  6. Success = redirect to `gumroad.com/d/<32-hex-id>` (the purchase's
     content page; the H1 is the product name). There's no toast, so the URL is
     the signal. Store that URL as the claim receipt.
  7. **Already owned** (e.g. the operator redeemed it by hand first, or a
     rescan after a reset re-discovers an old link): nothing on the product
     or checkout page hints at it, since the checkout is anonymous and only
     knows the email once it's submitted. After "Get", Gumroad opens a
     `role=dialog` titled **"You already own this"** ("You already paid for
     … Do you want to buy it again?") with **Cancel** / **Buy again**. The
     adapter reports `AlreadyOwned` and never clicks "Buy again". The source
     becomes CLAIMED with no receipt and the note "Already owned - claimed
     outside the app".

  Checkout is protected by **reCAPTCHA Enterprise** (score-based). The
  challenge `bframe` iframe sits in the DOM before any challenge — sometimes
  parked off-screen, sometimes laid out across the top of the viewport with
  `visibility: hidden` (seen after "Get" in a real checkout). So "a
  challenge is showing" means Playwright `isVisible()` *and* an on-screen box,
  never presence or position alone. A real logged-in Chrome
  passed with no challenge; a headless container may not. If the challenge
  frame becomes visible after "Get", the adapter does **not** try to solve
  or evade it. It marks the source as needing manual action and the admin UI
  shows the link for the operator to click in their own browser. Low stakes:
  Wicked's files come from the Drive term folder either way.
  This only claims the model in the Gumroad library — it does not download
  files. (This is the corrected home for what an earlier draft called
  `GumroadRedeemer` and registered as a downloader — it never downloaded
  anything, so that was the wrong interface for it.)
- DRIVE and MMF have no `ClaimPort` implementation at all — Acquisition
  treats "no claimer registered for this `SourceType`" as an implicit,
  immediate CLAIMED transition.

### 2b. `SourceDownloader` — fetches bytes, one per distribution mechanism

```java
public interface SourceDownloader {
    SourceType supports(); // DRIVE, MMF
    void fetch(DownloadItem item, Path targetDir);
}
```

- **RcloneDriveDownloadAdapter**: shells out to `rclone`, targeting shared
  folders by ID (`--drive-root-folder-id`), authenticated as the personal
  Google account the Patreon links were shared to (no per-file sharing setup
  needed).
- **MmfDownloadAdapter**: not yet designed — currently just tracked as
  MMF-sourced, manual retrieval.
- No Gumroad implementation exists or is needed: per the state machine below,
  Wicked's actual files come from its bulk Drive "term folder," never from
  Gumroad itself — Gumroad-sourced items only ever need `ClaimPort`.
  **Documented fallback, not built:** if a Gumroad-only source ever appears
  (or a Drive term folder disappears), a claimed product can be downloaded
  over plain HTTP from the `/d/<token>` receipt `GumroadClaimAdapter`
  already stores, plus `GUMROAD_EMAIL`. No browser and no Gumroad login;
  verified live on 2026-09-25:
  1. `GET /d/<token>` → 302 to `/confirm?destination=download_page&id=<token>`
     once the link has been opened before from another IP.
  2. `GET /confirm?...` → the Inertia `data-page` props carry
     `authenticity_token`.
  3. `POST /confirm-redirect` (not `/confirm`) with `authenticity_token`,
     `id`, `destination=download_page` and `email` → sets the encrypted
     `confirmed_redirect` cookie.
  4. `GET /d/<token>` → `props.content.content_items`: file name, extension,
     size and id per file.
  5. `GET /r/<token>/product_files?product_file_ids[]=…` with
     `Accept: application/json` → `{files: [{url, filename}]}`. These are
     signed `files.gumroad.com` URLs with a `verify` parameter, likely
     short-lived, so fetch them right before each file.
  6. Range requests work (206), so multi-GB files are resumable.
     `/zip/<token>` returned `{"url": null}` (no prebuilt archive), so
     per-file is the reliable path.
  Gumroad's public v2 API doesn't help here: it is seller-only. Its buyer
  API (`/mobile/*`) needs the official app's secret `mobile_token` and a
  deliberately hidden `mobile_api` OAuth scope, so it is not an option for
  an OSS tool.

New provider using existing mechanisms → one parser class.
New distribution mechanism entirely → one `SourceDownloader` implementation,
plus one `ClaimPort` implementation if ownership needs securing first.
None of the three — parser, claimer, downloader — need to know about
the others.

## Claim vs. materialize (state machine)

Redemption (owning something) and downloading (having the bytes) are separate
concerns with different urgency profiles:

```
download_source.claim_status:              DISCOVERED → CLAIMED
                                           DISCOVERED → NEEDS_MANUAL → CLAIMED (operator)
download_item.status (per file, post-claim):          PENDING → DOWNLOADED / FAILED
```

- **DISCOVERED**: source parsed/discovered, no action taken yet
  (`download_source.claim_status`).
- **CLAIMED**: ownership secured for the whole source. For Drive/MMF this is
  implicit — no `ClaimPort` is registered for those source types (see
  Extension points), so `ClaimSourceUseCase` claims them inline at
  registration. Port-backed claims (Gumroad) never run inline with mailbox
  polling — they're slow (a real browser) and must not be able to break a
  poll. `ClaimQueueJob` drives them in the background instead (every 10
  minutes, one source at a time, eagerly since Gumroad discount codes can
  expire). A successful Gumroad claim stores its `/d/<id>` purchase page as
  `claim_receipt_url`; an `AlreadyOwned` outcome is also CLAIMED, with no
  receipt and a `claim_note` saying it was claimed outside the app.
- **NEEDS_MANUAL**: the port couldn't finish on its own — a bot-check
  challenge, an expired coupon, a checkout guard tripping, or a
  `RetryLater` outcome repeating 5 times (exponential backoff from 30
  minutes). The reason is kept in `claim_note`; the admin UI lists these under
  "Needs your action" with the link, and the operator's "I claimed it"
  confirmation moves them to CLAIMED.
- **PENDING → DOWNLOADED / FAILED**: file-level, on `download_item`, only
  once the parent source is CLAIMED. Bytes actually on disk, or a recorded
  failure with `last_error`.

This matters because the two source types have opposite urgency shapes:

- **Google Drive links go dead** (folder access gets revoked with no warning) —
  so Drive-sourced items should download *eagerly*, before the link dies.
- **Gumroad, once redeemed, is safe indefinitely** — the model sits in your
  Gumroad library and can be re-downloaded anytime. No urgency to pull files
  immediately, so Gumroad-sourced items can sit at CLAIMED indefinitely.

Concretely for Wicked: redeem via Gumroad immediately on discovery (cheap,
time-boxed by discount expiry), but skip the actual file download unless/until
explicitly triggered — since Wicked models are unusually large and storage is
constrained. The redundant bulk Drive "term folder" Wicked also provides is the
actual download source when you do want the files, not the individual Gumroad
links.

## Per-provider download policy (runtime configurable)

```java
public enum DownloadPolicy { EAGER, MANUAL, DISABLED }
```

- `EAGER`: sync loop downloads automatically once an item is claimed —
  only items the app *can* download (a `SourceDownloader` exists for the
  source type). Claim-only or manual-retrieval items (Wicked's Gumroad lines,
  Bulkamancer's MMF entries) are never dispatched, not by the queue and not by
  "download now"; the overview labels them "not downloaded by the app".
- `MANUAL`: item stays at CLAIMED; download only on explicit user trigger from
  the UI (per-item or per-provider "download now").
- `DISABLED`: don't even claim; ignore the provider entirely.

Stored per-provider in Postgres (not YAML), editable live from the UI:

```sql
create table provider_settings (
    provider_id varchar primary key,   -- 'nomnom', 'bulkamancer', 'wicked'
    download_policy varchar not null,
    claim_policy varchar not null default 'AUTO', -- AUTO, MANUAL (V7)
    updated_at timestamp
);
```

Redeeming and downloading are set **independently**. `download_policy` only
governs the download queue; whether a port-backed link (Gumroad) gets
redeemed is `claim_policy`: `AUTO` lets `ClaimQueueJob` do it, `MANUAL`
sends each such source straight to NEEDS_MANUAL ("Needs your action")
without opening a browser. So Wicked can auto-redeem its Gumroad links while
its huge Drive folder only downloads on request. `DISABLED` still overrides
both, since nothing is registered at all. The UI only shows the redeem setting
for providers whose parser declares `hasRedeemableLinks()` (Wicked);
Drive-only providers have nothing to redeem.

On startup, every registered parser bean gets a default row inserted
(`ON CONFLICT DO NOTHING`) if missing — default to `MANUAL` for brand-new,
untested providers. This keeps "add a provider" a one-class change; the
settings row appears automatically.

Current expected values: Nomnom = EAGER, Bulkamancer = EAGER, Wicked =
MANUAL download + AUTO redeem.

## Handling folders that fill in over time

Nomnom (and Wicked's "still cooking" items) reuse the *same* folder link across
a month, initially near-empty. Dedup key is `(creator, driveFolderId)`, not the
message — a re-announcement of the same URL in a later email just no-ops
against `download_source`'s unique constraint.

**The folder is the source of truth for what files exist, not the email
text.** Email bullet lists are a human-readable manifest that can lag or drift
from the actual folder contents, so `download_item` rows for these sources
aren't created by the parser — they're created by a scheduled `FolderSyncJob`
that runs `rclone lsjson` against every non-quiet `CLAIMED` DRIVE/MMF source
and diffs it against known `remote_file_id`s, inserting a `PENDING` row per
new file (`model_name` taken from the filename). This runs independent of
whether a new email ever arrives — a folder that fills up with zero follow-up
emails still gets picked up on the next scheduled diff. (Sources with a clean
1:1 model↔link mapping — Bulkamancer's standalone DMs, Wicked's per-model
Gumroad lines — skip this: the parser creates their `download_item` directly,
since there's nothing to diff.)

This naturally handles re-runs at zero cost and catches late additions without
special-casing "pending" items — the diff is idempotent by construction.

A 40-day quiet window (no new files) ends *active* polling of a folder, but a
separate, lower-frequency **link health check** should continue afterward —
periodically verify the folder still resolves (catch 403/404) for some grace
period post-quiet, so a dying link is caught and logged rather than silently
discovered later as a permanent gap.

Sync should commit `download_item` status **per file**, not per source/folder
as one all-or-nothing unit — so a link dying mid-sync leaves you with whatever
was already pulled, not nothing.

## Manually added persistent Drive links

Some Drive links never arrive by email. They are persistent: one URL that
someone keeps filling with new releases. The usual shape is one top-level
folder per creator, each holding one folder per collection (typically a
monthly release). The shape isn't strict, though: real links mix in
top-level folders that aren't creators (a "Terrain Pack", a year-range
archive of one creator).

- **Adding one:** the operator adds the link in the admin UI with a name
  and a layout (`POST /api/sources` → `AddManualSourceUseCase`). The name
  takes the place of a parser's provider id. It gets its own
  `provider_settings` row (default `MANUAL`, like seeded providers) and
  becomes the link's top-level download folder. Parser provider ids are
  reserved, so a link can never silently share a provider's policy or
  folder. The source is a claimed `DRIVE` source with `claim_type = NONE`,
  exactly like an email-parsed Drive link.
- **`folder_layout`** says how `FolderSyncJob` reads a Drive folder source.
  `MODELS` (every email-parsed source): each top-level entry is one model,
  as in the section above. `COLLECTIONS`: one `rclone lsjson --max-depth 2`
  call per sync (not one per creator folder, to keep Drive API traffic
  low), then:
  - each child of a top-level folder becomes one item, with that folder's
    name in `download_item.group_name`;
  - a top-level folder whose children are all organizational (STL,
    Presupported, …) is one model's own folder and becomes a single
    ungrouped item;
  - a top-level file becomes a single ungrouped item;
  - an empty top-level folder registers nothing yet and is re-checked on
    the next sync.
- **Why "rolling" needs no new job:** identity is the Drive id
  (`remote_file_id`), same as everywhere else, and `FolderSyncJob` already
  re-diffs every claimed Drive source on its schedule. A new monthly
  release shows up as a new item on the next sync. A collection that
  rotates out of the link keeps its item: downloaded files stay put, and a
  still-pending one fails permanently on rclone's "not found" exit code.
- **Disk layout:** `<download root>/<link name>/<group>/<collection>`.
  Everything from one link stays together, and can't collide with the
  creator folders the parsers write.

## Storage constraints (10TB HDD)

- Never silently skip a download due to low space (a dead Drive link is
  unrecoverable; a full disk is not) — instead fail loudly with which items
  couldn't be downloaded, so they can be grabbed manually before links expire.
- Track `bytes_downloaded` per `download_source` for visibility into what's
  consuming space.
- Pruning/retention: start with just a threshold alert, no automated deletion.
  Deleting a creator's archived models should be a manual, confirmed UI action.

## Performance / I/O constraints (HDD, avoid blocking server I/O)

Single global download queue is the enforcement point — per-provider or
per-downloader throttling can't guarantee a global concurrency cap (e.g. two
providers each self-limiting to 1 concurrent transfer could still run
simultaneously).

```
Acquisition: download_item rows reach PENDING (post-claim)
                                       │
                                       ▼
                Fulfillment.DownloadQueueService (single choke point)
                          — enforces max concurrent downloads
                          — enforces allowed hours window
                          — enforces bandwidth limit
                          — dispatches to the right SourceDownloader
```

Runtime-configurable (Postgres-backed, not YAML — same pattern as provider
settings), editable from the same admin UI:

```sql
create table app_settings (
    id bigint primary key default 1,
    max_concurrent_downloads int not null default 1,
    bandwidth_limit_kbps int,
    io_nice boolean not null default true,
    allowed_hours_start time,
    allowed_hours_end time,
    updated_at timestamp
);
```

- `max_concurrent_downloads`: checked at dequeue time (not baked into a
  fixed-size pool/semaphore at startup), so changes apply live. On HDD, 1 is
  the likely default — concurrent transfers cause seek-thrashing.
- `bandwidth_limit_kbps`: passed straight to rclone as `--bwlimit`, read fresh
  per invocation — naturally live-updating, no caching problem.
- `io_nice`: wrap rclone process launch in `ionice -c3` (idle I/O class).
- `allowed_hours`: checked at dequeue time; a transfer already running when the
  window closes finishes rather than being killed mid-transfer.

Settings reads should be cached with a short TTL (a few seconds) to avoid
hitting Postgres on every queue tick, while still reflecting UI changes almost
immediately.

## Failure handling & retries

Distinct from the link-health check (which watches *quiet, already-claimed*
sources), this covers a download that fails *during* an active sync attempt:

- Transient failure (timeout, rclone exit code indicating a retryable error):
  back off and retry — e.g. 1m, 5m, 30m — capped at ~5 attempts, then leave the
  item `FAILED` rather than retrying forever against a possibly-dead source.
- Permanent failure (403/404): mark the item `FAILED` immediately, and flag the
  parent `download_source` for an out-of-cycle link-health check rather than
  waiting for its scheduled cadence — a 403 on one file is a strong early
  signal the whole folder/link just died.
- Either way, this is what "fail loudly" (Storage constraints, above) actually
  writes to: `download_item.last_error` + `retry_count`, surfaced in the admin
  UI's claimed-but-not-downloaded queue so a dying Drive link gets a human's
  attention before the grace period runs out.

## Data model (consolidated)

**Correction from the original draft:** the sync-per-file requirement above
means DOWNLOADED is a *file-level* fact, not a source-level one — a Drive
folder with 8 files, 5 downloaded, isn't meaningfully "CLAIMED" or
"DOWNLOADED," it's both at once depending which file you mean. So the state
machine splits across the two tables: `download_source` only ever carries
`claim_status` (DISCOVERED → CLAIMED — claiming is inherently whole-link:
one Gumroad checkout redeems the entire link, Drive access is implicit for
the whole folder), and `download_item` carries the per-file PENDING → 
DOWNLOADED/FAILED lifecycle plus the retry bookkeeping from the section above.

```sql
create table download_source (
    id uuid primary key,
    creator varchar not null,
    category varchar,             -- regular, loyalty, lootbox, keycap, extra, term
    month_label varchar,
    source_type varchar not null, -- DRIVE, MMF, GUMROAD
    source_url varchar not null,
    claim_type varchar,           -- NONE, GUMROAD
    claim_status varchar not null,-- DISCOVERED, CLAIMED
    first_seen timestamp,
    last_synced timestamp,        -- last time this source was diffed
    claimed_at timestamp,
    quiet_since timestamp,        -- set once no new files appear for 40 days
    link_dead boolean not null default false,
    folder_layout varchar not null default 'MODELS', -- MODELS, COLLECTIONS
    unique (creator, source_url)
);

create table download_item (
    id uuid primary key,
    source_id uuid references download_source(id),
    model_name varchar not null,
    remote_file_id varchar,       -- rclone/Drive file id — dedup key for re-diffing
    status varchar not null,      -- PENDING, DOWNLOADED, FAILED
    local_path varchar,
    file_size_bytes bigint,
    retry_count int not null default 0,
    last_error varchar,
    discovered_at timestamp not null,
    downloaded_at timestamp,
    group_name varchar,           -- COLLECTIONS sources: top-level folder (usually the creator)
    unique (source_id, remote_file_id)
);

-- storage visibility (Storage constraints, above) becomes a derived view,
-- not a stored counter — avoids write contention and drift on the hot path
create view source_bytes as
select source_id, sum(file_size_bytes) as bytes_downloaded
from download_item
where status = 'DOWNLOADED'
group by source_id;

create table processed_email (
    id uuid primary key,
    mailbox varchar not null,
    uid bigint not null,          -- IMAP UID: restart-safe idempotency key
    message_id varchar,
    from_address varchar,
    subject varchar,
    received_at timestamp,
    parser_matched varchar,       -- null if no parser's supports() matched
    parse_status varchar not null,-- PARSED, NO_PARSER_MATCH, PARSE_ERROR
    error_message varchar,
    processed_at timestamp not null,
    unique (mailbox, uid)
);
```

`download_source` is the sync/dedup and claim unit (one Drive folder, one MMF
library pointer, one Gumroad link); `download_item` is the file-level unit —
what actually gets downloaded, and what the overview UI counts and sizes.
`processed_email` is the ingestion audit trail: it's how "no parser matched
this email" becomes a visible admin-UI fact instead of a silent gap. On
`file_size_bytes` integrity: rely on rclone's own transfer verification
(size/hash check during the copy) rather than a separate post-hoc checksum
pass — an extra full-file hash read is exactly the kind of avoidable HDD I/O
the Performance section is trying to eliminate.

## Admin UI surfaces

- **Overview**: per-creator/month breakdown of models, status, size.
  Collections of a `COLLECTIONS` source show as "Group / Collection".
- **Add Drive link**: name, URL and layout, for persistent links that no
  email announces (see "Manually added persistent Drive links").
- **Claimed-but-not-downloaded queue**: for MANUAL-policy providers (Wicked),
  with per-item or per-provider "download now" trigger.
- **Provider settings panel**: download policy per provider.
- **Performance settings panel**: concurrency, bandwidth, I/O niceness, active
  hours window.

## Deployment topology

The app itself must not assume any particular network setup — that's a
per-deployer choice, not architecture. What follows is generic; *this*
deployment happens to sit behind Tailscale, but the repo/compose file
shouldn't encode that as a requirement.

- **Network boundary: bring your own.** The app ships with no built-in auth
  and no built-in HTTPS — the README states plainly that it's meant to sit
  behind whatever network boundary the operator already trusts (a VPN
  overlay like Tailscale/WireGuard, a reverse proxy with its own auth, or a
  plain LAN-only bind). `docker-compose.yml` exposes a configurable
  `APP_BIND_HOST`/`APP_PORT` (defaulting to `127.0.0.1`, the safe default for
  a stranger cloning the repo) rather than hardcoding any specific overlay
  network's assumptions. *This* deployment's choice is Tailscale-only, no
  app-level login — reconsider if ever shared outside a single trust group.
- **Containers**: `app` (Spring Boot, serves the built React static assets
  from the same JAR — one container, one port; headless Chromium is installed
  into the image at build time by the Playwright CLI bundled in that same JAR,
  so no separate browser sidecar is needed at single-user scale) and
  `postgres`. `rclone` isn't its own service — it's a
  CLI binary in the app image, shelled out to per transfer.
- **Volumes**:
  - `pg-data` — named volume, Postgres state.
  - HDD bind mount (host path configurable, e.g. `/mnt/hdd/patreon`) — actual
    downloaded files, laid out `{creator}/{month_label}/{model_name}/...`.
  - `rclone.conf` — mounted read-only; holds the Google OAuth refresh token
    for whichever Google account the operator configures (the account the
    Drive links were shared to). Treat this file itself as a secret
    (host-side `chmod 600`, never baked into the image, never committed).
  - No browser-state volume: the real checkout capture showed a free
    Gumroad checkout needs no login, just an email address
    (`GUMROAD_EMAIL`) — the product lands in that account's library. Each
    claim runs in a fresh browser context, so nothing persists between
    attempts (which also guarantees an empty cart).
- **Secrets**: `.env` + docker compose `env_file:` — IMAP host/user/password,
  DB credentials. A `.env.example` with placeholder values ships in the repo;
  `.env` itself is gitignored, never committed. No Gumroad password is
  stored or ever typed in by the app.

## Observability

- Structured logs per component (ingest / claim / download) to stdout,
  captured by `docker compose logs` — no separate log aggregator needed at
  single-user scale.
- The admin UI overview should surface `processed_email` failures
  (`NO_PARSER_MATCH` / `PARSE_ERROR`) alongside the existing per-creator
  breakdown — a silently-unrecognized email is exactly the kind of gap this
  table exists to make visible.
- Disk-threshold alert (Storage constraints, above): a persistent banner in
  the admin UI is the v1 delivery mechanism. A push notification (ntfy,
  Discord webhook) is a reasonable later upgrade, not needed to start.

## OSS readiness

The extension points already double as the contribution surface — a
stranger adding support for their own Patreon subscription shouldn't need to
touch core code. Two things formalize that:

- **Module boundary: core vs. providers.** Split into two Maven/Gradle
  modules: `ingest-core` (the three bounded contexts, the `CreatorMessageParser`/
  `ClaimPort`/`SourceDownloader` SPIs, the admin API, the schema) and
  `ingest-providers-default` (the `NomnomParser`/`BulkamancerParser`/
  `WickedParser` trio — these are one operator's personal subscriptions, not
  part of the core framework, even though they ship as the working default).
  A contributor adds a new creator by adding a class to the providers module
  (or their own separate module) — never by editing core. This is exactly
  the "one new class" extension story already designed, just given an
  explicit module wall so it's obvious from the repo layout, not just from
  discipline.
- **CONTRIBUTING.md** documents the concrete contribution recipe end to end:
  drop a sample (anonymized) email under
  `src/test/resources/emails/{provider}/`, implement `CreatorMessageParser`
  + its fixture test, register the bean — matching "Testing new parsers"
  above.
- **License: MIT** (`LICENSE` in repo root). Chosen for maximum ease of
  third-party contribution — no copyleft friction for someone who just wants
  to add a parser for their own subscriptions. AGPL was considered (it's the
  usual pick when the goal is stopping an unmodified SaaS wrapper) but
  doesn't fit here: this isn't a service someone would competitively rehost.
- **Config generalization** (see Deployment topology): no hardcoded network
  assumptions, `.env.example` in the repo, README states the "no built-in
  auth, bring your own network boundary" posture explicitly rather than
  leaving it implicit.

## Open / unresolved

- MyMiniFactory retrieval mechanism (Bulkamancer's recap library) not yet
  designed — likely needs its own auth flow investigation.
- **Single-file Google Drive shares aren't supported.** Real production data
  surfaced a `https://drive.google.com/file/d/<id>/view` link — a genuine,
  well-formed Drive link shape, just not a folder. `FolderSyncJob` only
  understands `/folders/<id>` and now flags anything else `link_dead`
  immediately (see "Failure handling" — deliberately, so it doesn't retry a
  deterministically-unfixable URL every 30 minutes forever), but no
  `SourceDownloader` exists for a single-file share either way. Unclear yet
  whether this recurs for real creator content (some creators may
  legitimately share one file instead of a folder) or was one instance of
  malformed input — worth revisiting once more real data is seen.
- **No way to un-flag `link_dead` once set**, from the UI, the API, or any
  use case — recovering a source that was flagged in error (e.g. the
  single-file-share case above, not a genuinely revoked folder) currently
  requires a direct database edit. Settled as *not urgent* for now since
  it's rare, but a real gap if `link_dead` false-positives turn out to be
  more common than expected.

Settled during this architecture pass: network exposure (Tailscale-only, no
app-level auth for v1) and secrets handling (`.env` + docker compose) — see
Deployment topology. Also settled during the Fulfillment build: Google
Drive access for a revoked/dead folder is treated as permanent, no
retry-with-backoff — `link_dead` sources are excluded from both the
download queue and `FolderSyncJob` outright rather than periodically
re-checked (that re-check is `LinkHealthCheckJob`, still unbuilt).
