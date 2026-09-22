import { useEffect, useState } from "react";

type IngestionSettings = {
  pollIntervalSeconds: number;
};

type FulfillmentSettings = {
  maxConcurrentDownloads: number;
  bandwidthLimitKbps: number | null;
  ioNice: boolean;
  allowedHoursStart: string | null;
  allowedHoursEnd: string | null;
  renameSpacesToUnderscores: boolean;
};

type Status = "loading" | "idle" | "saving" | "saved" | "error";

const MIN_POLL_INTERVAL_SECONDS = 60;

async function extractErrorMessage(res: Response): Promise<string> {
  try {
    const body = await res.json();
    return body.message ?? body.error ?? `Request failed: ${res.status}`;
  } catch {
    return `Request failed: ${res.status}`;
  }
}

// <input type="time"> only ever produces/accepts "HH:mm" - the API's
// LocalTime fields serialize with seconds ("HH:mm:ss").
function toTimeInputValue(apiValue: string | null): string {
  return apiValue ? apiValue.slice(0, 5) : "";
}

function toApiTime(inputValue: string): string | null {
  return inputValue ? `${inputValue}:00` : null;
}

export default function Settings() {
  return (
    <>
      <MailboxPollingSettings />
      <DownloadQueueSettings />
      <DangerZone />
    </>
  );
}

function MailboxPollingSettings() {
  const [pollIntervalSeconds, setPollIntervalSeconds] = useState<number | null>(null);
  const [status, setStatus] = useState<Status>("loading");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    fetch("/api/ingestion-settings")
      .then((res) => {
        if (!res.ok) throw new Error(`GET failed: ${res.status}`);
        return res.json() as Promise<IngestionSettings>;
      })
      .then((settings) => {
        setPollIntervalSeconds(settings.pollIntervalSeconds);
        setStatus("idle");
      })
      .catch((err) => {
        setErrorMessage(String(err));
        setStatus("error");
      });
  }, []);

  function save() {
    if (pollIntervalSeconds === null) return;
    setStatus("saving");
    setErrorMessage(null);
    fetch("/api/ingestion-settings", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ pollIntervalSeconds }),
    })
      .then(async (res) => {
        if (!res.ok) {
          throw new Error(await extractErrorMessage(res));
        }
        return res.json() as Promise<IngestionSettings>;
      })
      .then((settings) => {
        setPollIntervalSeconds(settings.pollIntervalSeconds);
        setStatus("saved");
      })
      .catch((err) => {
        setErrorMessage(err instanceof Error ? err.message : String(err));
        setStatus("error");
      });
  }

  return (
    <section>
      <h2>Mailbox polling</h2>
      {status === "loading" && <p>Loading current settings…</p>}
      {pollIntervalSeconds !== null && (
        <>
          <label htmlFor="poll-interval">
            Poll interval (seconds, minimum {MIN_POLL_INTERVAL_SECONDS})
          </label>
          <div style={{ display: "flex", gap: "0.5rem", marginTop: "0.5rem" }}>
            <input
              id="poll-interval"
              type="number"
              min={MIN_POLL_INTERVAL_SECONDS}
              value={pollIntervalSeconds}
              onChange={(e) => setPollIntervalSeconds(Number(e.target.value))}
            />
            <button onClick={save} disabled={status === "saving"}>
              {status === "saving" ? "Saving…" : "Save"}
            </button>
          </div>
          <p>
            Takes effect starting from the next scheduled poll, not
            retroactively on one already in flight.
          </p>
        </>
      )}
      {status === "saved" && <p>Saved.</p>}
      {status === "error" && <p role="alert">Error: {errorMessage}</p>}
    </section>
  );
}

function DownloadQueueSettings() {
  const [settings, setSettings] = useState<FulfillmentSettings | null>(null);
  const [status, setStatus] = useState<Status>("loading");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    fetch("/api/fulfillment-settings")
      .then((res) => {
        if (!res.ok) throw new Error(`GET failed: ${res.status}`);
        return res.json() as Promise<FulfillmentSettings>;
      })
      .then((data) => {
        setSettings(data);
        setStatus("idle");
      })
      .catch((err) => {
        setErrorMessage(String(err));
        setStatus("error");
      });
  }, []);

  function save() {
    if (settings === null) return;
    setStatus("saving");
    setErrorMessage(null);
    fetch("/api/fulfillment-settings", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(settings),
    })
      .then(async (res) => {
        if (!res.ok) {
          throw new Error(await extractErrorMessage(res));
        }
        return res.json() as Promise<FulfillmentSettings>;
      })
      .then((data) => {
        setSettings(data);
        setStatus("saved");
      })
      .catch((err) => {
        setErrorMessage(err instanceof Error ? err.message : String(err));
        setStatus("error");
      });
  }

  return (
    <section style={{ marginTop: "2rem" }}>
      <h2>Download queue</h2>
      {status === "loading" && <p>Loading current settings…</p>}
      {settings !== null && (
        <>
          <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem", maxWidth: 360 }}>
            <label>
              Max concurrent downloads
              <input
                type="number"
                min={1}
                value={settings.maxConcurrentDownloads}
                onChange={(e) =>
                  setSettings({ ...settings, maxConcurrentDownloads: Number(e.target.value) })
                }
                style={{ display: "block", marginTop: "0.25rem" }}
              />
            </label>
            <p style={{ margin: 0, fontSize: "0.85em", color: "#57606a" }}>
              Keep this low (1 is the safe default) - both to avoid HDD
              seek-thrashing and to avoid getting your Google Drive account
              rate-limited.
            </p>

            <label>
              Bandwidth limit (KB/s, blank = unlimited)
              <input
                type="number"
                min={1}
                value={settings.bandwidthLimitKbps ?? ""}
                onChange={(e) =>
                  setSettings({
                    ...settings,
                    bandwidthLimitKbps: e.target.value === "" ? null : Number(e.target.value),
                  })
                }
                style={{ display: "block", marginTop: "0.25rem" }}
              />
            </label>

            <label style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
              <input
                type="checkbox"
                checked={settings.ioNice}
                onChange={(e) => setSettings({ ...settings, ioNice: e.target.checked })}
              />
              Use low I/O priority (ionice) for downloads
            </label>

            <label style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
              <input
                type="checkbox"
                checked={settings.renameSpacesToUnderscores}
                onChange={(e) =>
                  setSettings({ ...settings, renameSpacesToUnderscores: e.target.checked })
                }
              />
              Rename downloaded files/folders: replace spaces with underscores
            </label>

            <div>
              <span>Allowed hours (blank = no restriction)</span>
              <div style={{ display: "flex", gap: "0.5rem", marginTop: "0.25rem", alignItems: "center" }}>
                <input
                  type="time"
                  value={toTimeInputValue(settings.allowedHoursStart)}
                  onChange={(e) =>
                    setSettings({ ...settings, allowedHoursStart: toApiTime(e.target.value) })
                  }
                />
                <span>to</span>
                <input
                  type="time"
                  value={toTimeInputValue(settings.allowedHoursEnd)}
                  onChange={(e) =>
                    setSettings({ ...settings, allowedHoursEnd: toApiTime(e.target.value) })
                  }
                />
              </div>
            </div>

            <button onClick={save} disabled={status === "saving"} style={{ alignSelf: "flex-start" }}>
              {status === "saving" ? "Saving…" : "Save"}
            </button>
          </div>
          <p style={{ fontSize: "0.85em", color: "#57606a" }}>
            Applies from the next queue check onward, not retroactively to a
            download already in flight.
          </p>
        </>
      )}
      {status === "saved" && <p>Saved.</p>}
      {status === "error" && <p role="alert">Error: {errorMessage}</p>}
    </section>
  );
}

function DangerZone() {
  const [status, setStatus] = useState<"idle" | "resetting" | "done" | "error">("idle");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  function resetTracking() {
    if (
      !confirm(
        "Reset ingest tracking? This forces a full mailbox rescan on the next " +
          "poll - already-registered sources/items and download history are " +
          "untouched. Use this if you changed a parser and need it to see " +
          "emails it already looked at."
      )
    ) {
      return;
    }
    setStatus("resetting");
    setErrorMessage(null);
    fetch("/api/ingestion/reset-tracking", { method: "POST" })
      .then((res) => {
        if (!res.ok) throw new Error(`Request failed: ${res.status}`);
        setStatus("done");
      })
      .catch((err) => {
        setErrorMessage(err instanceof Error ? err.message : String(err));
        setStatus("error");
      });
  }

  return (
    <section style={{ marginTop: "2rem", border: "1px solid #cf222e", borderRadius: 6, padding: "1rem" }}>
      <h2 style={{ marginTop: 0, color: "#cf222e" }}>Danger zone</h2>
      <p style={{ fontSize: "0.9em" }}>
        Clears the mailbox's processed-email tracking so the next poll
        rescans everything from scratch. Does not touch discovered sources,
        items, or what's already been downloaded.
      </p>
      <button onClick={resetTracking} disabled={status === "resetting"}>
        {status === "resetting" ? "Resetting…" : "Reset ingest tracking"}
      </button>
      {status === "done" && <p>Done - the next poll will rescan the whole mailbox.</p>}
      {status === "error" && <p role="alert">Error: {errorMessage}</p>}
    </section>
  );
}
