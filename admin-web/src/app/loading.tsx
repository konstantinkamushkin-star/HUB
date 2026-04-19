/** Пока грузится сегмент — без Tailwind остаётся читаемый тёмный экран (не «белый лист»). */
export default function AppLoading() {
  return (
    <div
      style={{
        minHeight: "100vh",
        backgroundColor: "#09090b",
        color: "#a1a1aa",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        fontFamily: "system-ui, sans-serif",
      }}
    >
      Загрузка…
    </div>
  );
}
