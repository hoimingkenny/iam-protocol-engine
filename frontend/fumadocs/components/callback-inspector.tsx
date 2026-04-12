'use client';

import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { decodeJwtPart } from '@/lib/pkce';
import { getStoredFlow } from './flow-explorer';
import { JsonPanel } from './json-panel';

type TokenResponse = {
  access_token?: string;
  refresh_token?: string;
  id_token?: string;
  scope?: string;
  token_type?: string;
  expires_in?: number;
  error?: string;
  error_description?: string;
  [key: string]: unknown;
};

export function CallbackInspector() {
  const params = useSearchParams();
  const [tokenResponse, setTokenResponse] = useState<TokenResponse | null>(null);
  const [userinfo, setUserinfo] = useState<unknown>(null);
  const [status, setStatus] = useState<'idle' | 'loading' | 'loaded' | 'error'>(
    'idle',
  );
  const [message, setMessage] = useState<string>(
    'Waiting for callback parameters or a manual token exchange.',
  );

  const callbackPayload = useMemo(
    () => ({
      code: params.get('code'),
      state: params.get('state'),
      iss: params.get('iss'),
      error: params.get('error'),
      error_description: params.get('error_description'),
    }),
    [params],
  );

  const flow = useMemo(() => getStoredFlow(), []);

  useEffect(() => {
    const code = params.get('code');
    const error = params.get('error');

    if (error) {
      setStatus('error');
      setMessage(`Authorization error: ${error}`);
      return;
    }

    if (!code || !flow) return;
    const activeFlow = flow;

    let cancelled = false;

    async function exchange() {
      setStatus('loading');
      setMessage('Exchanging the authorization code through the local proxy...');

      const response = await fetch('/api/oauth/token', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          backendBaseUrl: activeFlow.backendBaseUrl,
          clientId: activeFlow.clientId,
          redirectUri: activeFlow.redirectUri,
          codeVerifier: activeFlow.verifier,
          code,
        }),
      });

      const payload = (await response.json()) as TokenResponse;
      if (cancelled) return;

      setTokenResponse(payload);

      if (!response.ok || payload.error) {
        setStatus('error');
        setMessage(payload.error_description ?? payload.error ?? 'Token exchange failed.');
        return;
      }

      setStatus('loaded');
      setMessage('Token exchange succeeded. Inspect the payloads below.');
    }

    exchange().catch((error: unknown) => {
      if (cancelled) return;
      setStatus('error');
      setMessage(error instanceof Error ? error.message : 'Token exchange failed.');
    });

    return () => {
      cancelled = true;
    };
  }, [flow, params]);

  async function handleUserinfo() {
    if (!tokenResponse?.access_token || !flow) return;

    const response = await fetch('/api/oidc/userinfo', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        backendBaseUrl: flow.backendBaseUrl,
        accessToken: tokenResponse.access_token,
      }),
    });

    const payload = await response.json();
    setUserinfo(payload);
  }

  const decodedTokens = useMemo(() => {
    if (!tokenResponse) return null;

    return {
      access_token_header: tokenResponse.access_token
        ? decodeJwtPart(tokenResponse.access_token, 0)
        : null,
      access_token_payload: tokenResponse.access_token
        ? decodeJwtPart(tokenResponse.access_token, 1)
        : null,
      id_token_header: tokenResponse.id_token ? decodeJwtPart(tokenResponse.id_token, 0) : null,
      id_token_payload: tokenResponse.id_token ? decodeJwtPart(tokenResponse.id_token, 1) : null,
    };
  }, [tokenResponse]);

  return (
    <div className="space-y-6">
      <section className="app-shell-card rounded-3xl p-6">
        <p className="text-xs font-semibold uppercase tracking-[0.22em] text-[var(--app-accent)]">
          Callback Inspector
        </p>
        <h2 className="mt-2 text-2xl font-semibold tracking-tight">
          Inspect returned query params and exchange the auth code
        </h2>
        <p className="mt-3 max-w-2xl text-sm leading-7 text-[var(--app-text-muted)]">
          This page becomes most useful when the client is registered with
          `http://localhost:3000/studio/callback`. If the redirect URI is not
          registered, the backend will reject the authorize request before you
          get here.
        </p>
        <div className="mt-4 rounded-2xl border border-[var(--app-border)] bg-[var(--app-surface-muted)] px-4 py-3 text-sm">
          <span className="font-semibold">Status:</span> {status} • {message}
        </div>
        {tokenResponse?.access_token && flow?.mode === 'oidc' ? (
          <button
            type="button"
            onClick={handleUserinfo}
            className="mt-4 inline-flex cursor-pointer items-center rounded-full bg-[var(--app-accent)] px-4 py-2 text-sm font-semibold text-white transition hover:bg-[var(--app-accent-strong)]"
          >
            Fetch UserInfo
          </button>
        ) : null}
      </section>

      <div className="grid gap-6 xl:grid-cols-2">
        <JsonPanel
          title="Callback Query"
          subtitle="What the browser returned to the local callback route"
          data={callbackPayload}
        />
        <JsonPanel
          title="Stored Flow Context"
          subtitle="Saved before redirecting to /oauth2/authorize"
          data={flow}
        />
        <JsonPanel
          title="Token Response"
          subtitle="Returned by /api/oauth/token"
          data={tokenResponse}
        />
        <JsonPanel
          title="Decoded Tokens"
          subtitle="JWT headers and payloads when available"
          data={decodedTokens}
        />
        {userinfo ? (
          <JsonPanel
            title="UserInfo Response"
            subtitle="Fetched with the returned access token"
            data={userinfo}
          />
        ) : null}
      </div>
    </div>
  );
}
