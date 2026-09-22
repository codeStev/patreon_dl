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

Implemented and tested: Ingestion (real IMAP polling, dynamic
user-configurable interval, the Nomnom parser), Acquisition (claim state
machine, dedup, provider settings seeding), a minimal Admin API + React page
for the poll interval, and CI/CD publishing the combined image.

Not yet built: Fulfillment (the actual download queue and file transfers),
Gumroad's real claim flow, the Bulkamancer/Wicked parsers, and the rest of
the admin UI (overview, per-provider settings, manual download triggers).
See the design doc's "Open / unresolved" section for what's genuinely
undecided versus just not-yet-implemented.

## License

MIT — see [`LICENSE`](LICENSE). Contributions welcome; the parser extension
point above is deliberately the easiest place to add value without needing
to understand the rest of the system.
