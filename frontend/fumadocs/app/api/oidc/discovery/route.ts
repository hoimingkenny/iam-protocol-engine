import { NextRequest, NextResponse } from 'next/server';
import { defaultBackendBaseUrl } from '@/lib/shared';

export async function GET(request: NextRequest) {
  const baseUrl =
    request.nextUrl.searchParams.get('baseUrl') || defaultBackendBaseUrl;
  const response = await fetch(`${baseUrl}/.well-known/openid-configuration`, {
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
