import Link from 'next/link';
import { ArrowRight, BookOpenText, FileCode2, MoonStar } from 'lucide-react';
import { studioRoute } from '@/lib/shared';

const cards = [
  {
    title: 'Interactive Docs',
    description:
      'Jump into the studio for PKCE, OIDC login, callback inspection, and endpoint-level reference.',
    href: studioRoute,
    icon: FileCode2,
  },
  {
    title: 'Written Docs',
    description:
      'Browse the reference docs and phase overviews — Bootstrap through Demo Hardening.',
    href: '/docs',
    icon: BookOpenText,
  },
];

export default function HomePage() {
  return (
    <main className="flex flex-1 flex-col">
      <section className="app-grid flex flex-1 items-center justify-center px-6 py-20">
        <div className="mx-auto grid w-full max-w-6xl gap-10 lg:grid-cols-[1.2fr_0.8fr]">
          <div className="space-y-6">
            <div className="inline-flex items-center gap-2 rounded-full border border-[var(--app-border)] bg-[var(--app-surface)] px-4 py-2 text-xs font-semibold uppercase tracking-[0.18em] text-[var(--app-accent)]">
              <MoonStar className="size-4" />
              Day and night ready
            </div>
            <div className="space-y-4">
              <h1 className="max-w-4xl text-4xl font-semibold tracking-tight text-balance sm:text-5xl">
                A calmer docs studio for studying OAuth, OIDC, and the shape of the protocol itself.
              </h1>
              <p className="max-w-3xl text-base leading-8 text-[var(--app-text-muted)] sm:text-lg">
                A study companion for IAM protocol engineering: inspectable
                flows, local callback debugging, OpenAPI-backed endpoint views,
                phase overviews, and a quieter docs shell that works in both light and dark mode.
              </p>
            </div>
            <div className="flex flex-wrap gap-3">
              <Link
                href="/studio/oauth-pkce"
                className="inline-flex items-center gap-2 rounded-full bg-[var(--app-accent)] px-5 py-3 text-sm font-semibold text-white transition hover:bg-[var(--app-accent-strong)]"
              >
                Open PKCE Explorer
                <ArrowRight className="size-4" />
              </Link>
              <Link
                href="/docs"
                className="inline-flex items-center gap-2 rounded-full border border-[var(--app-border-strong)] bg-[var(--app-surface-strong)] px-5 py-3 text-sm font-semibold transition hover:border-[var(--app-accent)] hover:text-[var(--app-accent)]"
              >
                Open Docs
              </Link>
            </div>
          </div>

          <div className="grid gap-4">
            {cards.map(({ title, description, href, icon: Icon }) => (
              <Link
                key={title}
                href={href}
                className="app-shell-card group rounded-3xl p-6 shadow-[0_20px_60px_-45px_rgba(15,23,42,0.6)] transition hover:-translate-y-0.5 hover:border-[var(--app-accent)]"
              >
                <div className="flex items-center gap-3">
                  <div className="rounded-2xl bg-[var(--app-accent-soft)] p-3 text-[var(--app-accent)]">
                    <Icon className="size-5" />
                  </div>
                  <h2 className="text-lg font-semibold tracking-tight">{title}</h2>
                </div>
                <p className="mt-4 text-sm leading-7 text-[var(--app-text-muted)]">
                  {description}
                </p>
                <div className="mt-5 inline-flex items-center gap-2 text-sm font-semibold text-[var(--app-accent)]">
                  Open
                  <ArrowRight className="size-4 transition group-hover:translate-x-0.5" />
                </div>
              </Link>
            ))}
          </div>
        </div>
      </section>
    </main>
  );
}
