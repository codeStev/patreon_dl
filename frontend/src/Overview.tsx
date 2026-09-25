import { useEffect, useState } from "react";

type Item = {
  id: string;
  modelName: string | null;
  status: "PENDING" | "DOWNLOADED" | "FAILED";
  fileSizeBytes: number | null;
  retryCount: number;
  lastError: string | null;
};

type Source = {
  id: string;
  creator: string;
  category: string | null;
  monthLabel: string | null;
  sourceType: "DRIVE" | "GUMROAD" | "MMF";
  sourceUrl: string;
  claimType: string | null;
  claimStatus: "DISCOVERED" | "NEEDS_MANUAL" | "CLAIMED";
  claimNote: string | null;
  claimReceiptUrl: string | null;
  linkDead: boolean;
  firstSeen: string | null;
  items: Item[];
};

type LoadState = "loading" | "loaded" | "error";

const CLAIM_COLORS: Record<Source["claimStatus"], string> = {
  DISCOVERED: "#9a6700",
  NEEDS_MANUAL: "#cf222e",
  CLAIMED: "#1a7f37",
};

const ITEM_COLORS: Record<Item["status"], string> = {
  PENDING: "#57606a",
  DOWNLOADED: "#1a7f37",
  FAILED: "#cf222e",
};

export default function Overview() {
  const [sources, setSources] = useState<Source[]>([]);
  const [state, setState] = useState<LoadState>("loading");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  function refresh() {
    fetch("/api/sources")
      .then((res) => {
        if (!res.ok) throw new Error(`GET failed: ${res.status}`);
        return res.json() as Promise<Source[]>;
      })
      .then((data) => {
        setSources(data);
        setState("loaded");
      })
      .catch((err) => {
        setErrorMessage(String(err));
        setState("error");
      });
  }

  useEffect(refresh, []);

  if (state === "loading") {
    return <p>Loading…</p>;
  }
  if (state === "error") {
    return <p role="alert">Error: {errorMessage}</p>;
  }
  if (sources.length === 0) {
    return <p>Nothing discovered yet.</p>;
  }

  const needsManual = sources.filter((s) => s.claimStatus === "NEEDS_MANUAL");

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
      {needsManual.length > 0 && (
        <section
          style={{ border: "1px solid #cf222e", borderRadius: 6, padding: "0.75rem 1rem", background: "#fff5f5" }}
        >
          <strong>Needs your action — claim by hand ({needsManual.length})</strong>
          <p style={{ margin: "0.25rem 0 0.5rem", fontSize: "0.85em", color: "#57606a" }}>
            Open each link in your own browser, complete the free checkout, then confirm here.
          </p>
          <ul style={{ margin: 0, paddingLeft: "1.2rem" }}>
            {needsManual.map((source) => (
              <li key={source.id} style={{ marginBottom: "0.35rem" }}>
                <a href={source.sourceUrl} target="_blank" rel="noreferrer">
                  {source.items[0]?.modelName ?? source.sourceUrl}
                </a>
                <MarkClaimedButton sourceId={source.id} onMarked={refresh} />
                {source.claimNote && (
                  <div style={{ fontSize: "0.85em", color: "#57606a" }}>{source.claimNote}</div>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}
      {sources.map((source) => (
        <article
          key={source.id}
          style={{ border: "1px solid #d0d7de", borderRadius: 6, padding: "0.75rem 1rem" }}
        >
          <div style={{ display: "flex", alignItems: "baseline", gap: "0.5rem", flexWrap: "wrap" }}>
            <strong>{source.creator}</strong>
            {(source.category || source.monthLabel) && (
              <span style={{ color: "#57606a" }}>
                {[source.monthLabel, source.category].filter(Boolean).join(" · ")}
              </span>
            )}
            <span style={{ fontSize: "0.85em", color: "#57606a" }}>{source.sourceType}</span>
            <span style={{ fontSize: "0.85em", color: CLAIM_COLORS[source.claimStatus] }}>
              {source.claimStatus}
            </span>
            {source.linkDead && (
              <span style={{ fontSize: "0.85em", color: "#cf222e" }}>LINK DEAD</span>
            )}
          </div>
          <div style={{ marginTop: "0.25rem" }}>
            <a href={source.sourceUrl} target="_blank" rel="noreferrer" style={{ fontSize: "0.85em" }}>
              {source.sourceUrl}
            </a>
            {source.claimReceiptUrl && (
              <>
                {" · "}
                <a href={source.claimReceiptUrl} target="_blank" rel="noreferrer" style={{ fontSize: "0.85em" }}>
                  receipt
                </a>
              </>
            )}
          </div>
          {source.claimStatus === "CLAIMED" && source.claimNote && (
            <div style={{ fontSize: "0.85em", color: "#57606a" }}>{source.claimNote}</div>
          )}
          {source.items.length > 0 ? (
            <ul style={{ marginTop: "0.5rem", paddingLeft: "1.2rem" }}>
              {source.items.map((item) => (
                <li key={item.id}>
                  {item.modelName ?? <em>(unnamed)</em>}
                  {" — "}
                  <span style={{ color: ITEM_COLORS[item.status] }}>{item.status}</span>
                  {item.status === "FAILED" && item.lastError && (
                    <span style={{ color: "#cf222e" }}> ({item.lastError})</span>
                  )}
                  {(item.status === "PENDING" || item.status === "FAILED") && (
                    <DownloadNowButton itemId={item.id} onDispatched={refresh} />
                  )}
                </li>
              ))}
            </ul>
          ) : (
            <p style={{ marginTop: "0.5rem", color: "#57606a", fontSize: "0.85em" }}>
              No individual items yet — this is a whole-folder registration.
            </p>
          )}
        </article>
      ))}
    </div>
  );
}

function MarkClaimedButton({ sourceId, onMarked }: { sourceId: string; onMarked: () => void }) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function mark() {
    setPending(true);
    setError(null);
    fetch(`/api/sources/${sourceId}/mark-claimed`, { method: "POST" })
      .then(async (res) => {
        if (!res.ok) {
          const body = await res.json().catch(() => null);
          throw new Error(body?.message ?? `Request failed: ${res.status}`);
        }
        onMarked();
      })
      .catch((err) => setError(err instanceof Error ? err.message : String(err)))
      .finally(() => setPending(false));
  }

  return (
    <>
      <button onClick={mark} disabled={pending} style={{ marginLeft: "0.5rem", fontSize: "0.85em" }}>
        {pending ? "Saving…" : "I claimed it"}
      </button>
      {error && <span style={{ color: "#cf222e", fontSize: "0.85em" }}> {error}</span>}
    </>
  );
}

function DownloadNowButton({ itemId, onDispatched }: { itemId: string; onDispatched: () => void }) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function trigger() {
    setPending(true);
    setError(null);
    fetch(`/api/download-items/${itemId}/download-now`, { method: "POST" })
      .then(async (res) => {
        if (!res.ok) {
          const body = await res.json().catch(() => null);
          throw new Error(body?.message ?? `Request failed: ${res.status}`);
        }
        onDispatched();
      })
      .catch((err) => setError(err instanceof Error ? err.message : String(err)))
      .finally(() => setPending(false));
  }

  return (
    <>
      <button onClick={trigger} disabled={pending} style={{ marginLeft: "0.5rem", fontSize: "0.85em" }}>
        {pending ? "Starting…" : "Download now"}
      </button>
      {error && <span style={{ color: "#cf222e", fontSize: "0.85em" }}> {error}</span>}
    </>
  );
}
