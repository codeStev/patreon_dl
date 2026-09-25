import { useEffect, useState } from "react";

type Item = {
  id: string;
  groupName: string | null;
  modelName: string | null;
  status: "PENDING" | "DOWNLOADED" | "FAILED";
  downloadable: boolean;
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
  folderLayout: "MODELS" | "COLLECTIONS";
  addedManually: boolean;
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
    return (
      <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
        <AddDriveLinkForm onAdded={refresh} />
        <p>Nothing discovered yet.</p>
      </div>
    );
  }

  const needsManual = sources.filter((s) => s.claimStatus === "NEEDS_MANUAL");

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
      <AddDriveLinkForm onAdded={refresh} />
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
            {source.addedManually && <RemoveSourceButton sourceId={source.id} onRemoved={refresh} />}
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
                  {item.groupName && <span style={{ color: "#57606a" }}>{item.groupName} / </span>}
                  {item.modelName ?? <em>(unnamed)</em>}
                  {" — "}
                  {item.downloadable ? (
                    <>
                      <span style={{ color: ITEM_COLORS[item.status] }}>{item.status}</span>
                      {item.status === "FAILED" && item.lastError && (
                        <span style={{ color: "#cf222e" }}> ({item.lastError})</span>
                      )}
                      {(item.status === "PENDING" || item.status === "FAILED") && (
                        <DownloadNowButton itemId={item.id} onDispatched={refresh} />
                      )}
                    </>
                  ) : (
                    <span style={{ color: "#57606a", fontSize: "0.85em" }}>
                      not downloaded by the app — {source.sourceType === "GUMROAD"
                        ? "redeem only, files come from the creator's Drive folder"
                        : "retrieve it manually"}
                    </span>
                  )}
                </li>
              ))}
            </ul>
          ) : (
            <p style={{ marginTop: "0.5rem", color: "#57606a", fontSize: "0.85em" }}>
              {source.folderLayout === "COLLECTIONS"
                ? "No collections found yet — they appear after the next folder sync."
                : "No individual items yet — this is a whole-folder registration."}
            </p>
          )}
        </article>
      ))}
    </div>
  );
}

// For Drive links no email announces - typically a persistent link that
// keeps getting new releases. The name becomes the link's own entry in
// Settings (download policy) and its top-level download folder.
function AddDriveLinkForm({ onAdded }: { onAdded: () => void }) {
  const [name, setName] = useState("");
  const [url, setUrl] = useState("");
  const [layout, setLayout] = useState<Source["folderLayout"]>("COLLECTIONS");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function submit(event: React.FormEvent) {
    event.preventDefault();
    setPending(true);
    setError(null);
    fetch("/api/sources", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name, url, layout }),
    })
      .then(async (res) => {
        if (!res.ok) {
          const body = await res.json().catch(() => null);
          throw new Error(body?.message ?? `Request failed: ${res.status}`);
        }
        setUrl("");
        onAdded();
      })
      .catch((err) => setError(err instanceof Error ? err.message : String(err)))
      .finally(() => setPending(false));
  }

  return (
    <form
      onSubmit={submit}
      style={{ border: "1px solid #d0d7de", borderRadius: 6, padding: "0.75rem 1rem" }}
    >
      <strong>Add Drive link</strong>
      <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap", marginTop: "0.5rem" }}>
        <input
          aria-label="Name"
          placeholder="Name, e.g. my-archive"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />
        <input
          aria-label="Drive link"
          placeholder="https://drive.google.com/drive/folders/…"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          required
          style={{ flex: "1 1 20rem" }}
        />
        <select
          aria-label="Layout"
          value={layout}
          onChange={(e) => setLayout(e.target.value as Source["folderLayout"])}
        >
          <option value="COLLECTIONS">One item per collection</option>
          <option value="MODELS">One item per top-level entry</option>
        </select>
        <button type="submit" disabled={pending}>
          {pending ? "Adding…" : "Add"}
        </button>
      </div>
      <p style={{ margin: "0.35rem 0 0", fontSize: "0.85em", color: "#57606a" }}>
        Items appear after the next folder sync. Downloads follow the name's policy in Settings (Manual until changed).
      </p>
      {error && <p role="alert" style={{ margin: "0.35rem 0 0", color: "#cf222e", fontSize: "0.85em" }}>{error}</p>}
    </form>
  );
}

// Two clicks instead of a browser confirm dialog. Only the app forgets the
// link - files it already downloaded stay on disk.
function RemoveSourceButton({ sourceId, onRemoved }: { sourceId: string; onRemoved: () => void }) {
  const [confirming, setConfirming] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function remove() {
    setPending(true);
    setError(null);
    fetch(`/api/sources/${sourceId}`, { method: "DELETE" })
      .then(async (res) => {
        if (!res.ok) {
          const body = await res.json().catch(() => null);
          throw new Error(body?.message ?? `Request failed: ${res.status}`);
        }
        onRemoved();
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : String(err));
        setConfirming(false);
      })
      .finally(() => setPending(false));
  }

  return (
    <span style={{ marginLeft: "auto", fontSize: "0.85em" }}>
      {confirming ? (
        <>
          <span style={{ color: "#57606a" }}>Remove link? Downloaded files stay on disk. </span>
          <button onClick={remove} disabled={pending} style={{ color: "#cf222e" }}>
            {pending ? "Removing…" : "Remove"}
          </button>{" "}
          <button onClick={() => setConfirming(false)} disabled={pending}>
            Cancel
          </button>
        </>
      ) : (
        <button onClick={() => setConfirming(true)}>Remove</button>
      )}
      {error && <span style={{ color: "#cf222e" }}> {error}</span>}
    </span>
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
