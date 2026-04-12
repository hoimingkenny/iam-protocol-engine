import { NextRequest, NextResponse } from 'next/server';
import { defaultBackendBaseUrl } from '@/lib/shared';

type RequestBody = {
  backendBaseUrl?: string;
  accessToken?: string;
};

export async function POST(request: NextRequest) {
  const body = (await request.json()) as RequestBody;

  if (!body.accessToken) {
    return NextResponse.json(
      { error: 'invalid_request', error_description: 'Missing access token.' },
      { status: 400 },
    );
  }

  const backendBaseUrl = body.backendBaseUrl || defaultBackendBaseUrl;
  const response = await fetch(`${backendBaseUrl}/userinfo`, {
    headers: {
      Authorization: `Bearer ${body.accessToken}`,
    },
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
