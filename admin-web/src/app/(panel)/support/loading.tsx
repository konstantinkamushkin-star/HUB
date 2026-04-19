/** Пока грузится клиентский модуль — видимый тёмный экран без Tailwind. */
export default function SupportLoading() {
  return (
    <div
      style={{
        minHeight: "40vh",
        backgroundColor: "#09090b",
        color: "#a1a1aa",
        fontFamily: "system-ui, sans-serif",
        padding: 24,
      }}
    >
      Загрузка раздела поддержки…
    </div>
  );
}
