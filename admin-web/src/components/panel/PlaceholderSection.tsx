export function PlaceholderSection({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-3">
      <h1 className="text-xl font-semibold text-white">{title}</h1>
      <div className="max-w-3xl rounded-lg border border-amber-900/50 bg-amber-950/20 p-4 text-sm text-amber-100/90">
        {children}
      </div>
    </div>
  );
}
