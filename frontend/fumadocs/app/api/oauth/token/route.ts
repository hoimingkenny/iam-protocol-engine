import { NextRequest, NextResponse } from 'next/server';
import { defaultBackendBaseUrl } from '@/lib/shared';

type RequestBody = {
  backendBaseUrl?: string;
  code?: string;
  redirectUri?: string;
  clientId?: string;
  codeVerifier?: string;
};

export async function POST(request: NextRequest) {
  const body = (await request.json()) as RequestBody;

  if (!body.code || !body.redirectUri || !body.clientId || !body.codeVerifier) {
    return NextResponse.json(
      { error: 'invalid_request', error_description: 'Missing token exchange fields.' },
      { status: 400 },
    );
  }

  const backendBaseUrl = body.backendBaseUrl || defaultBackendBaseUrl;
  const response = await fetch(`${backendBaseUrl}/oauth2/token`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      code: body.code,
      redirect_uri: body.redirectUri,
      client_id: body.clientId,
      code_verifier: body.codeVerifier,
    }),
    cache: 'no-store',
  });

  const text = await response.text();

  try {
    return NextResponse.json(JSON.parse(text), { status: response.status });
  } catch {
    return NextResponse.json(
      { error: 'upstream_error', error_description: text },
      { status: response.status },
    );
  }
}
