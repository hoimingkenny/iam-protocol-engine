'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { defaultBackendBaseUrl, defaultStudioOrigin } from '@/lib/shared';
import {
  createPkcePair,
  decodeJwtPart,
  getRfc7636PkcePair,
} from '@/lib/pkce';
import { ArrowRight, Copy, ExternalLink, RotateCcw, Sparkles } from 'lucide-react';
import { JsonPanel } from './json-panel';

type FlowMode = 'oauth' | 'oidc';

type FlowExplorerProps = {
  mode: FlowMode;
};

type StoredFlow = {
  mode: FlowMode;
  backendBaseUrl: string;
  clientId: string;
  redirectUri: string;
  scope: string;
  state: string;
  nonce?: string;
  verifier: string;
  challenge: string;
  subject: string;
};

const STORAGE_KEY = 'iam-docs-studio-flow';

function randomValue(prefix: string) {
  return `${prefix}-${Math.random().toString(36).slice(2, 10)}`;
}

export function FlowExplorer({ mode }: FlowExplorerProps) {
  const [backendBaseUrl, setBackendBaseUrl] = useState(defaultBackendBaseUrl);
  const [clientId, setClientId] = useState('test-client');
  const [redirectUri, setRedirectUri] = useState(`${defaultStudioOrigin}/studio/callback`);
  const [scope, setScope] = useState(mode === 'oidc' ? 'openid profile email' : 'profile email');
  const [subject, setSubject] = useState('user1');
  const [state, setState] = useState(() => randomValue('state'));
  const [nonce, setNonce] = useState(() => randomValue('nonce'));
  const [verifier, setVerifier] = useState('');
  const [challenge, setChallenge] = useState('');
  const [pkceStatus, setPkceStatus] = useState<'idle' | 'loading' | 'ready' | 'error'>(
    'idle',
  );
  const [pkceError, setPkceError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [discovery, setDiscovery] = useState<unknown>(null);
  const [discoveryError, setDiscoveryError] = useState<string | null>(null);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    setRedirectUri(`${window.location.origin}/studio/callback`);
  }, []);

  useEffect(() => {
    setPkceStatus('loading');
    createPkcePair()
      .then(({ verifier, challenge }) => {
        setVerifier(verifier);
        setChallenge(challenge);
        setPkceStatus('ready');
        setPkceError(null);
      })
      .catch((error: unknown) => {
        setVerifier('');
        setChallenge('');
        setPkceStatus('error');
        setPkceError(
          error instanceof Error
            ? error.message
            : 'Unable to generate a PKCE pair in this browser.',
        );
      });
  }, []);

  useEffect(() => {
    if (mode !== 'oidc') return;

    const controller = new AbortController();
    const url = `/api/oidc/discovery?baseUrl=${encodeURIComponent(backendBaseUrl)}`;

    fetch(url, { signal: controller.signal })
      .then(async (response) => {
        const payload = await response.json();
        if (!response.ok) {
          throw new Error(payload.error ?? 'Failed to load discovery document');
        }
        setDiscovery(payload);
        setDiscoveryError(null);
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setDiscovery(null);
        setDiscoveryError(
          error instanceof Error ? error.message : 'Failed to load discovery document',
        );
      });

    return () => controller.abort();
  }, [backendBaseUrl, mode]);

  const authorizeUrl = useMemo(() => {
    if (!verifier || !challenge) return '';

    const url = new URL(`${backendBaseUrl}/oauth2/authorize`);
    url.searchParams.set('client_id', clientId);
    url.searchParams.set('redirect_uri', redirectUri);
    url.searchParams.set('response_type', 'code');
    url.searchParams.set('scope', scope);
    url.searchParams.set('state', state);
    url.searchParams.set('code_challenge', challenge);
    url.searchParams.set('code_challenge_method', 'S256');
    url.searchParams.set('subject', subject);

    if (mode === 'oidc') {
      url.searchParams.set('nonce', nonce);
    }

    return url.toString();
  }, [backendBaseUrl, challenge, clientId, mode, nonce, redirectUri, scope, state, subject, verifier]);

  const tokenPreview = useMemo(() => {
    const payload = {
      grant_type: 'authorization_code',
      client_id: clientId,
      redirect_uri: redirectUri,
      code_verifier: verifier,
    };

    return payload;
  }, [clientId, redirectUri, verifier]);

  const decodedChallenge = useMemo(
    () => ({
      code_verifier: verifier,
      code_challenge: challenge,
      response_type: 'code',
      redirect_uri: redirectUri,
      mode,
    }),
    [challenge, mode, redirectUri, verifier],
  );

  async function handleGenerate() {
    setPkceStatus('loading');
    try {
      const pair = await createPkcePair();
      setVerifier(pair.verifier);
      setChallenge(pair.challenge);
      setState(randomValue('state'));
      if (mode === 'oidc') {
        setNonce(randomValue('nonce'));
      }
      setPkceStatus('ready');
      setPkceError(null);
    } catch (error: unknown) {
      setVerifier('');
      setChallenge('');
      setPkceStatus('error');
      setPkceError(
        error instanceof Error
          ? error.message
          : 'Unable to generate a PKCE pair in this browser.',
      );
    }
  }

  async function handleUseRfcVector() {
    setPkceStatus('loading');
    try {
      const pair = await getRfc7636PkcePair();
      setVerifier(pair.verifier);
      setChallenge(pair.challenge);
      setPkceStatus('ready');
      setPkceError(null);
    } catch (error: unknown) {
      setVerifier('');
      setChallenge('');
      setPkceStatus('error');
      setPkceError(
        error instanceof Error
          ? error.message
          : 'Unable to load the RFC 7636 PKCE pair in this browser.',
      );
    }
  }

  async function handleCopyUrl() {
    await navigator.clipboard.writeText(authorizeUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  function storeFlowContext() {
    const payload: StoredFlow = {
      mode,
      backendBaseUrl,
      clientId,
      redirectUri,
      scope,
      state,
      verifier,
      challenge,
      subject,
      nonce: mode === 'oidc' ? nonce : undefined,
    };

    localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  }

  function handleOpenAuthorize() {
    storeFlowContext();
    window.location.href = authorizeUrl;
  }

  const tokenPayloadExample = {
    access_token: 'eyJhbGciOiJSUzI1NiIsImtpZCI6ImlhbS1rZXktMSJ9...',
    refresh_token: '4c5f6825-rotating-refresh-token',
    id_token: mode === 'oidc' ? 'eyJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAifQ...' : undefined,
  };

  return (
    <div className="space-y-6">
      <section className="app-shell-card rounded-3xl p-6 shadow-[0_24px_80px_-55px_rgba(15,23,42,0.65)]">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div className="max-w-2xl">
            <p className="text-xs font-semibold uppercase tracking-[0.22em] text-[var(--app-accent)]">
              Interactive Explorer
            </p>
            <h2 className="mt-2 text-2xl font-semibold tracking-tight">
              {mode === 'oidc' ? 'OIDC login with discovery and token inspection' : 'Authorization Code + PKCE from request to callback'}
            </h2>
            <p className="mt-3 text-sm leading-7 text-[var(--app-text-muted)]">
              This is a study surface. It uses the repo&apos;s Phase 2 `subject`
              shortcut to skip interactive login, and it expects your client
              registration to allow the callback URI you choose.
            </p>
          </div>
          <div className="flex flex-wrap gap-3">
            <button
              type="button"
              onClick={handleGenerate}
              disabled={pkceStatus === 'loading'}
              className="inline-flex cursor-pointer items-center gap-2 rounded-full border border-[var(--app-border-strong)] bg-[var(--app-surface-strong)] px-4 py-2 text-sm font-medium transition hover:border-[var(--app-accent)] hover:text-[var(--app-accent)]"
            >
              <Sparkles className="size-4" />
              {pkceStatus === 'loading' ? 'Generating…' : 'New PKCE Pair'}
            </button>
            <button
              type="button"
              onClick={handleUseRfcVector}
              disabled={pkceStatus === 'loading'}
              className="inline-flex cursor-pointer items-center gap-2 rounded-full border border-[var(--app-border-strong)] bg-transparent px-4 py-2 text-sm font-medium transition hover:border-[var(--app-accent)] hover:text-[var(--app-accent)]"
            >
              <RotateCcw className="size-4" />
              RFC 7636 Test Vector
            </button>
          </div>
        </div>
        <div className="mt-5 rounded-2xl border border-[var(--app-border)] bg-[var(--app-surface-muted)] px-4 py-3 text-sm">
          <span className="font-semibold">PKCE status:</span>{' '}
          {pkceStatus === 'ready'
            ? 'ready'
            : pkceStatus === 'loading'
              ? 'generating'
              : pkceStatus === 'error'
                ? 'error'
                : 'idle'}
          {pkceError ? (
            <p className="mt-2 text-sm text-red-400">{pkceError}</p>
          ) : null}
        </div>
      </section>

      <section className="app-shell-card rounded-3xl p-6">
        <div className="grid gap-5 xl:grid-cols-2">
          <label className="space-y-2 text-sm">
            <span className="font-medium">Backend base URL</span>
            <input
              value={backendBaseUrl}
              onChange={(event) => setBackendBaseUrl(event.target.value)}
              className="w-full rounded-2xl border border-[var(--app-border-strong)] bg-[var(--app-surface-strong)] px-4 py-3 outline-none transition focus:border-[var(--app-accent)]"
            />
          </label>
          <label className="space-y-2 text-sm">
            <span className="font-medium">Client ID</span>
            <input
              value={clientId}
              onChange={(event) => setClientId(event.target.value)}
              className="w-full rounded-2xl border border-[var(--app-border-strong)] bg-[var(--app-surface-strong)] px-4 py-3 outline-none transition focus:border-[var(--app-accent)]"
            />
          </label>
          <label className="space-y-2 text-sm xl:col-span-2">
            <span className="font-medium">Redirect URI</span>
            <input
              value={redirectUri}
              onChange={(event) => setRedirectUri(event.target.value)}
              className="w-full rounded-2xl border border-[var(--app-border-strong)] bg-[var(--app-surface-strong)] px-4 py-3 outline-none transition focus:border-[var(--app-accent)]"
            />
          </label>
          <label className="space-y-2 text-sm">
            <span className="font-medium">Scope</span>
            <input
              value={scope}
              onChange={(event) => setScope(event.target.value)}
              className="w-full rounded-2xl border border-[var(--app-border-strong)] bg-[var(--app-surface-strong)] px-4 py-3 outline-none transition focus:border-[var(--app-accent)]"
            />
          </label>
          <label className="space-y-2 text-sm">
            <span className="font-medium">Subject shortcut</span>
            <input
              value={subject}
              onChange={(event) => setSubject(event.target.value)}
              className="w-full rounded-2xl border border-[var(--app-border-strong)] bg-[var(--app-surface-strong)] px-4 py-3 outline-none transition focus:border-[var(--app-accent)]"
            />
          </label>
          <label className="space-y-2 text-sm">
            <span className="font-medium">State</span>
            <input
              value={state}
              onChange={(event) => setState(event.target.value)}
              className="w-full rounded-2xl border border-[var(--app-border-strong)] bg-[var(--app-surface-strong)] px-4 py-3 font-mono outline-none transition focus:border-[var(--app-accent)]"
            />
          </label>
          {mode === 'oidc' ? (
            <label className="space-y-2 text-sm">
              <span className="font-medium">Nonce</span>
              <input
                value={nonce}
                onChange={(event) => setNonce(event.target.value)}
                className="w-full rounded-2xl border border-[var(--app-border-strong)] bg-[var(--app-surface-strong)] px-4 py-3 font-mono outline-none transition focus:border-[var(--app-accent)]"
              />
            </label>
          ) : null}
        </div>

        <div className="mt-6 flex flex-col gap-3 border-t border-[var(--app-border)] pt-6 lg:flex-row lg:items-center lg:justify-between">
          <section className="app-shell-card rounded-2xl p-5 shadow-[0_18px_60px_-45px_rgba(15,23,42,0.55)]">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h3 className="text-sm font-semibold tracking-tight">Callback Shortcut</h3>
                <p className="mt-1 text-xs text-[var(--app-text-muted)]">
                  Open the local callback inspector before or after the redirect
                  if you want to inspect `code`, `state`, and the token exchange.
                </p>
              </div>
              <Link
                href="/studio/callback"
                className="inline-flex cursor-pointer items-center justify-center gap-2 rounded-full border border-[var(--app-border-strong)] px-4 py-2 text-sm font-medium transition hover:border-[var(--app-accent)] hover:text-[var(--app-accent)]"
              >
                Open callback inspector
                <ExternalLink className="size-4" />
              </Link>
            </div>
          </section>
          <div className="flex flex-col gap-3 sm:flex-row">
            <button
              type="button"
              onClick={handleCopyUrl}
              disabled={!authorizeUrl}
              className="inline-flex w-full cursor-pointer items-center justify-center gap-2 rounded-full bg-[var(--app-surface-strong)] px-4 py-2.5 text-sm font-medium transition hover:text-[var(--app-accent)] disabled:cursor-not-allowed disabled:opacity-50 sm:w-auto"
            >
              <Copy className="size-4" />
              {copied ? 'Copied' : 'Copy authorize URL'}
            </button>
            <button
              type="button"
              onClick={handleOpenAuthorize}
              disabled={!authorizeUrl || pkceStatus !== 'ready'}
              className="inline-flex w-full cursor-pointer items-center justify-center gap-2 rounded-full bg-[var(--app-accent)] px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-[var(--app-accent-strong)] disabled:cursor-not-allowed disabled:opacity-50 sm:w-auto"
            >
              Open authorize request
              <ArrowRight className="size-4" />
            </button>
          </div>
        </div>
      </section>

      <div className="grid gap-6 xl:grid-cols-2">
        <div className="space-y-6">
          <JsonPanel
            title="PKCE State"
            subtitle="Verifier and challenge stored before redirect"
            data={decodedChallenge}
          />
          <JsonPanel
            title="Authorize URL"
            subtitle="What the browser will open"
            data={{ authorizeUrl }}
          />
          <JsonPanel
            title="Token Request Preview"
            subtitle="The callback page will POST this via the local proxy route"
            data={tokenPreview}
          />
          <JsonPanel
            title="Token Shape Preview"
            subtitle="Expected fields for a successful code exchange"
            data={{
              ...tokenPayloadExample,
              decoded_id_token:
                tokenPayloadExample.id_token
                  ? decodeJwtPart(tokenPayloadExample.id_token, 1)
                  : null,
            }}
          />
          {mode === 'oidc' ? (
            discovery ? (
              <JsonPanel
                title="OIDC Discovery"
                subtitle="Fetched through the local proxy route"
                data={discovery}
              />
            ) : (
              <JsonPanel
                title="OIDC Discovery"
                subtitle={discoveryError ?? 'Waiting for backend metadata'}
                data={{
                  issuer: backendBaseUrl,
                  status: discoveryError ? 'error' : 'loading',
                }}
              />
            )
          ) : null}
        </div>
        <section className="app-shell-card rounded-3xl p-6">
          <div className="grid gap-4 md:grid-cols-3">
            <div className="rounded-2xl border border-[var(--app-border)] bg-[var(--app-surface-muted)] p-4">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--app-text-muted)]">
                Step 1
              </p>
              <h3 className="mt-2 text-sm font-semibold">Build authorize request</h3>
              <p className="mt-2 text-sm text-[var(--app-text-muted)]">
                Create a PKCE pair, choose the callback URI, and attach the
                Phase 2 `subject` shortcut so the backend can issue the code.
              </p>
            </div>
            <div className="rounded-2xl border border-[var(--app-border)] bg-[var(--app-surface-muted)] p-4">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--app-text-muted)]">
                Step 2
              </p>
              <h3 className="mt-2 text-sm font-semibold">Inspect callback</h3>
              <p className="mt-2 text-sm text-[var(--app-text-muted)]">
                The callback page reads `code`, `state`, and any returned
                errors, then uses the stored verifier for token exchange.
              </p>
            </div>
            <div className="rounded-2xl border border-[var(--app-border)] bg-[var(--app-surface-muted)] p-4">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--app-text-muted)]">
                Step 3
              </p>
              <h3 className="mt-2 text-sm font-semibold">Decode tokens</h3>
              <p className="mt-2 text-sm text-[var(--app-text-muted)]">
                Review response payloads, decode JWT claims, and optionally call
                UserInfo on the OIDC page.
              </p>
            </div>
          </div>
          <div className="mt-6 rounded-2xl border border-[var(--app-border)] bg-[var(--app-surface-muted)] p-5">
            <h3 className="text-sm font-semibold">How this view is meant to read</h3>
            <p className="mt-2 text-sm leading-7 text-[var(--app-text-muted)]">
              The controls above are the primary surface. The JSON panels are
              reference material you glance at while stepping through the flow,
              not a second main column competing for attention.
            </p>
          </div>
        </section>
      </div>
    </div>
  );
}

export function getStoredFlow(): StoredFlow | null {
  if (typeof window === 'undefined') return null;
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;

  try {
    return JSON.parse(raw) as StoredFlow;
  } catch {
    return null;
  }
}
