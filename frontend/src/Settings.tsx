import { useEffect, useState } from "react";

type IngestionSettings = {
  pollIntervalSeconds: number;
};

type Status = "loading" | "idle" | "saving" | "saved" | "error";

const MIN_POLL_INTERVAL_SECONDS = 60;

export default function Settings() {
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

  async function extractErrorMessage(res: Response): Promise<string> {
    try {
      const body = await res.json();
      return body.message ?? body.error ?? `Request failed: ${res.status}`;
    } catch {
      return `Request failed: ${res.status}`;
    }
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
