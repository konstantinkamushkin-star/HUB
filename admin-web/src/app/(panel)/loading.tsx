export default function PanelLoading() {
  return (
    <div
      className="flex min-h-screen items-center justify-center bg-zinc-950 text-zinc-400"
      style={{
        minHeight: "100vh",
        backgroundColor: "#09090b",
        color: "#a1a1aa",
        fontFamily: "system-ui, sans-serif",
      }}
    >
      Загрузка панели…
    </div>
  );
}
