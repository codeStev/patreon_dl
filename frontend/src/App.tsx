import { useState } from "react";
import Overview from "./Overview";
import Settings from "./Settings";

type Tab = "overview" | "settings";

export default function App() {
  const [tab, setTab] = useState<Tab>("overview");

  return (
    <main style={{ maxWidth: 720, margin: "3rem auto", fontFamily: "sans-serif" }}>
      <h1>Patreon Ingest Bot</h1>
      <nav style={{ display: "flex", gap: "1rem", borderBottom: "1px solid #d0d7de", marginBottom: "1rem" }}>
        <TabButton active={tab === "overview"} onClick={() => setTab("overview")}>
          Overview
        </TabButton>
        <TabButton active={tab === "settings"} onClick={() => setTab("settings")}>
          Settings
        </TabButton>
      </nav>
      {tab === "overview" ? <Overview /> : <Settings />}
    </main>
  );
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: string;
}) {
  return (
    <button
      onClick={onClick}
      style={{
        background: "none",
        border: "none",
        borderBottom: active ? "2px solid #0969da" : "2px solid transparent",
        color: active ? "#0969da" : "#57606a",
        padding: "0.5rem 0",
        cursor: "pointer",
        fontSize: "1rem",
      }}
    >
      {children}
    </button>
  );
}
