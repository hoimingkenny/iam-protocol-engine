const RFC7636_VERIFIER = 'dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk';

function getWebCrypto(): Crypto {
  const webCrypto = globalThis.crypto;

  if (!webCrypto?.subtle || !webCrypto.getRandomValues) {
    throw new Error(
      'Web Crypto is unavailable in this browser context. Try reloading over http://localhost or using a newer browser build.',
    );
  }

  return webCrypto;
}

function toBase64Url(bytes: Uint8Array): string {
  const base64 = btoa(String.fromCharCode(...bytes));
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

export async function deriveCodeChallenge(verifier: string): Promise<string> {
  const webCrypto = getWebCrypto();
  const buffer = await webCrypto.subtle.digest(
    'SHA-256',
    new TextEncoder().encode(verifier),
  );

  return toBase64Url(new Uint8Array(buffer));
}

export function generateCodeVerifier(length = 64): string {
  const webCrypto = getWebCrypto();
  const alphabet =
    'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
  const bytes = webCrypto.getRandomValues(new Uint8Array(length));

  return Array.from(bytes, (value) => alphabet[value % alphabet.length]).join('');
}

export async function createPkcePair() {
  const verifier = generateCodeVerifier();
  const challenge = await deriveCodeChallenge(verifier);

  return { verifier, challenge };
}

export async function getRfc7636PkcePair() {
  const challenge = await deriveCodeChallenge(RFC7636_VERIFIER);

  return {
    verifier: RFC7636_VERIFIER,
    challenge,
  };
}

export function decodeJwtPart(token: string, index: number) {
  const part = token.split('.')[index];
  if (!part) return null;

  const normalized = part.replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalized + '='.repeat((4 - (normalized.length % 4 || 4)) % 4);

  try {
    return JSON.parse(atob(padded));
  } catch {
    return null;
  }
}
