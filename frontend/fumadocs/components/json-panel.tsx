type JsonPanelProps = {
  title: string;
  data: unknown;
  subtitle?: string;
};

export function JsonPanel({ title, data, subtitle }: JsonPanelProps) {
  return (
    <section className="app-shell-card rounded-2xl p-5 shadow-[0_18px_60px_-45px_rgba(15,23,42,0.55)]">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold tracking-tight">{title}</h3>
          {subtitle ? (
            <p className="mt-1 text-xs text-[var(--app-text-muted)]">{subtitle}</p>
          ) : null}
        </div>
      </div>
      <pre className="overflow-x-auto rounded-xl px-4 py-3 text-xs leading-6">
        {JSON.stringify(data, null, 2)}
      </pre>
    </section>
  );
}
