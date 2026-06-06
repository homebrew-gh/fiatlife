export function PlaceholderTab({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return (
    <div className="space-y-4">
      <div>
        <h1 className="page-title">{title}</h1>
        <p className="text-sm text-muted mt-1">{description}</p>
      </div>
      <div className="card p-6 text-center">
        <p className="text-4xl font-serif text-dollarBill mb-2" aria-hidden>
          $
        </p>
        <p className="text-muted text-sm">
          Coming soon — data already syncs from your relay on the Dashboard.
        </p>
      </div>
    </div>
  );
}
