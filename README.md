# Patreon Ingest Bot

A self-hosted tool that watches an inbox for Patreon creator DM
notifications, figures out what was shared (a Google Drive folder, a
Gumroad code, a MyMiniFactory link, ...), and tracks ownership so files can
be downloaded later — without you manually clicking through email after
email every time a creator posts.

It's built to support multiple creators with completely different message
formats and distribution mechanisms out of the box, and to make adding a
new one a small, self-contained change rather than a rewrite.

**Status: early.** The core ingestion pipeline (read an email → parse it →
persist what was found → claim ownership) is implemented and tested
end-to-end for one real creator format. Downloading the actual files,
claiming Gumroad codes, and most of the admin UI are not built yet — see
[Current status](#current-status) below for the honest breakdown.

## How it works, briefly

The full architecture — including *why* it's shaped this way — lives in
[`patreon-ingest-bot-design.md`](patreon-ingest-bot-design.md). The short
version: three responsibilities, kept independent so a change to one never
requires touching the others.

```
IMAP mailbox → parse email → register what was found → claim ownership → (eventually) download files
   Ingestion         Ingestion              Acquisition        Acquisition            Fulfillment
```

- **Ingestion** reads the mailbox and turns an email into structured data.
  Each creator gets its own parser (a `CreatorMessageParser`) — the message
  format is the *only* thing that differs between creators, so that's the
  *only* thing a new parser needs to know about.
- **Acquisition** is the core domain: it owns what's been discovered, what's
  been claimed, and the state machine between the two. Claiming a Gumroad
  code and having Drive access automatically granted are different
  mechanisms (a `ClaimPort`), kept separate from *how a file is later
  downloaded* (a `SourceDownloader`) — a new distribution mechanism doesn't
  require touching parsing, and a new creator using an existing mechanism
  doesn't require touching downloading.
- **Fulfillment** turns a claimed item into bytes on disk. Not implemented
  yet.

## Project structure

```
ingest-core/                the three bounded contexts, persistence, the
                             CreatorMessageParser/ClaimPort/SourceDownloader
                             extension points - the framework, not any one
                             creator's specifics
ingest-providers-default/   parsers for specific creators (currently: one,
                             Nomnom) - this is where a new creator's parser
                             goes, see "Adding a new creator" below
app/                        the runnable Spring Boot application: wires
                             everything together, the scheduler, the REST API
frontend/                   React admin UI (currently: one settings page)
deploy/combined/            Dockerfile + docker-compose.yml for running the
                             published image
```

## Running it

The published image bundles the backend and the built frontend into one
container — no separate frontend/backend deployment.

```bash
cd deploy/combined
cp .env.example .env
# edit .env: at minimum set REGISTRY_HOST, and IMAP_* if you want email
# polling active (leaving IMAP_HOST empty just disables it, the app still
# runs fine without it)
docker compose up -d
```

Ships with **no built-in authentication or HTTPS** — it's meant to sit
behind a network boundary you already trust (a VPN like Tailscale/WireGuard,
a reverse proxy with its own auth, or plain LAN-only). The default bind is
`127.0.0.1` for exactly this reason; widen it only once you know how it's
being exposed.

## Setting up downloads

File downloads (Fulfillment) go through [`rclone`](https://rclone.org),
which needs a one-time interactive login to the personal Google account your
Patreon links were shared to. This step is done **on your own machine, not
by the container** — it's an OAuth login, not something to automate:

```bash
# Install rclone if you don't have it: https://rclone.org/install/
rclone config
# Choose "New remote", name it "gdrive" (or whatever you set
# RCLONE_REMOTE_NAME to), type "drive" (Google Drive), and follow the
# prompts - it opens a browser for you to authorize your Google account.
```

This writes an `rclone.conf` (by default `~/.config/rclone/rclone.conf`).
Point `RCLONE_CONFIG_PATH` in `.env` at that file, and `DOWNLOAD_PATH` at
where you actually want files to land (ideally your real HDD mount point,
not wherever `docker compose` happens to run from):

```
DOWNLOAD_PATH=/mnt/my-hdd/patreon-downloads
RCLONE_CONFIG_PATH=/home/you/.config/rclone/rclone.conf
RCLONE_REMOTE_NAME=gdrive
```

`RCLONE_CONFIG_PATH` must already exist as a **file** before the first
`docker compose up` — an absent path gets bind-mounted as an empty
directory instead, which fails confusingly rather than clearly. If you
haven't run `rclone config` yet, leave email polling running without it;
nothing else in the app depends on it existing.

`rclone config` creates that file mode `0600` (owner-only) - the container
runs as a non-root user that won't share your host UID, so it can't read it
as-is. Loosen the permissions after creating it:
```bash
chmod 644 /home/you/.config/rclone/rclone.conf
```
Symptom if you skip this: `FolderSyncJob`/downloads fail with `permission
denied` reading `/config/rclone.conf` in the container logs, even though
the mount itself looks correct.

The same UID mismatch applies to `DOWNLOAD_PATH` itself — Docker
auto-creates a missing bind-mount host directory owned by root, which the
container's non-root user can traverse into but not write to:
```bash
sudo chown -R 1001:1001 /path/to/your/download/path
```
(`1001` matches the fixed non-root uid the runtime image's `spring` user is
created with). Symptom if you skip this: downloads fail with `Could not
create target directory ...` in the logs, retried on the normal backoff
schedule until they eventually give up.

The default `max_concurrent_downloads=1` (editable later from Settings in
the UI) is deliberately conservative — both to avoid HDD seek-thrashing and
to avoid Google flagging the account for too many concurrent Drive API
requests. Raise it only if you know what you're doing.

## Developing

Requires JDK 25, Docker (the test suite uses Testcontainers against a real
Postgres — no mocked database), and Node 26+ for the frontend.

```bash
./gradlew build          # backend: build + full test suite
cd frontend && npm install && npm run dev   # frontend dev server, proxies
                                             # /api to localhost:8080
```

Run the backend locally with `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD`
pointed at any Postgres 18 instance and `./gradlew :app:bootRun`.

### Adding a new creator

If the new creator uses a distribution mechanism that's already supported
(right now: Google Drive, implicitly claimed):

1. Implement `CreatorMessageParser` in `ingest-providers-default` — see
   `NomnomParser` for the shape. `providerId()` is the stable id used
   everywhere else (settings, dedup); `supports()` matches the email;
   `parse()` turns the body into `ParsedItem`s.
2. Add fixture emails under
   `ingest-providers-default/src/test/resources/emails/{provider}/*.txt`
   and a test asserting the parsed output, following `NomnomParserTest`.
3. Register it as a `@Component` — that's it. A `provider_settings` row
   defaulting to `MANUAL` appears automatically on next startup (see
   `ProviderSettingsSeeder`); no database edit required.

A genuinely new distribution mechanism (not Drive/Gumroad/MyMiniFactory)
additionally needs a `SourceDownloader`, and a `ClaimPort` if ownership
needs an explicit action to secure (Drive doesn't; Gumroad will).

## Current status

Implemented and tested: Ingestion (real IMAP polling with UID-based
incremental fetch, dynamic user-configurable interval, all three creator
parsers — Nomnom, Bulkamancer, Wicked — built against real sample emails),
Acquisition (claim state machine, dedup, provider-policy enforcement
including `DISABLED`, `FolderSyncJob` for living Drive folders), Fulfillment
(the download queue itself: concurrency/allowed-hours dispatch policy,
retry/backoff, the `rclone`-backed Drive downloader, manual "download now"
trigger), and an Admin API + React UI covering settings for both polling and
the download queue plus a live overview of every discovered source and item.

Not yet built: Gumroad's real claim flow (`GumroadClaimAdapter` — Wicked
sources stay `DISCOVERED` until this exists), `MmfDownloadAdapter` (MMF
stays manual-retrieval), and `LinkHealthCheckJob` (the periodic re-check of
already-quiet sources — a hard failure during an active download attempt
already flags a source `link_dead` immediately; this job would additionally
catch a link dying silently with no download attempt in flight). See the
design doc's "Open / unresolved" section for what's genuinely undecided
versus just not-yet-implemented.

## License

MIT — see [`LICENSE`](LICENSE). Contributions welcome; the parser extension
point above is deliberately the easiest place to add value without needing
to understand the rest of the system.
