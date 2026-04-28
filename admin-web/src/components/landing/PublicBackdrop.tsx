/** Фон для публичных страниц лендинга. */
export function PublicBackdrop() {
  return (
    <div
      aria-hidden
      className="pointer-events-none absolute inset-0 z-0 overflow-hidden"
    >
      <div className="absolute -left-28 -top-20 h-72 w-72 rounded-full bg-sky-300/30 blur-3xl" />
      <div className="absolute right-0 top-1/4 h-72 w-72 rounded-full bg-cyan-300/25 blur-3xl" />
      <div className="absolute bottom-0 left-1/3 h-80 w-80 rounded-full bg-blue-200/25 blur-3xl" />
    </div>
  );
}
