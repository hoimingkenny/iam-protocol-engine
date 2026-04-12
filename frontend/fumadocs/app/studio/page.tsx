import Link from 'next/link';
import { DocsBody, DocsDescription, DocsPage, DocsTitle } from 'fumadocs-ui/layouts/docs/page';

const sections = [
  {
    title: 'OAuth 2.0 PKCE',
    href: '/studio/oauth-pkce',
    description:
      'Generate a verifier/challenge pair, build the authorize request, and inspect the callback plus token exchange.',
  },
  {
    title: 'OIDC Login',
    href: '/studio/oidc-login',
    description:
      'Layer discovery, nonce handling, ID token inspection, and UserInfo fetching onto the same authorization code flow.',
  },
  {
    title: 'API Reference',
    href: '/studio/api-reference',
    description:
      'See the selected OpenAPI-backed endpoints used by the studio without leaving the docs shell.',
  },
];

export default function StudioOverviewPage() {
  return (
    <DocsPage
      toc={[
        { title: 'Explorers', url: '#explorers', depth: 2 },
        { title: 'Notes', url: '#notes', depth: 2 },
      ]}
    >
      <DocsTitle>Interactive Docs Studio</DocsTitle>
      <DocsDescription>
        A study-focused surface for inspecting auth flows, callback parameters,
        and endpoint behavior against the existing Spring backend.
      </DocsDescription>
      <DocsBody>
        <h2 id="explorers">Explorers</h2>
        <div className="grid gap-4 md:grid-cols-3">
          {sections.map((section) => (
            <Link
              key={section.title}
              href={section.href}
              className="app-shell-card rounded-3xl p-5 transition hover:border-[var(--app-accent)] hover:text-[var(--app-accent)]"
            >
              <h3 className="text-base font-semibold">{section.title}</h3>
              <p className="mt-3 text-sm leading-7 text-[var(--app-text-muted)]">
                {section.description}
              </p>
            </Link>
          ))}
        </div>

        <h2 id="notes">Notes</h2>
        <p>
          The explorer pages assume the backend is running on
          <code> http://localhost:8080</code>. They use the repo&apos;s Phase 2
          <code> subject=user1</code> shortcut on the authorize endpoint, which
          means you can study the protocol surface without going through the
          Admin UI login flow first.
        </p>
        <p>
          For the callback page to complete a local code exchange, register
          <code> http://localhost:3000/studio/callback</code> on your chosen
          public client.
        </p>
      </DocsBody>
    </DocsPage>
  );
}
