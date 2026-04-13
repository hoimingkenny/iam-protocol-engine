export const appName = 'Docstash';
export const docsRoute = '/docs';
export const studioRoute = '/studio';
export const defaultBackendBaseUrl =
  process.env.NEXT_PUBLIC_IAM_BASE_URL ?? 'http://localhost:8080';
export const defaultStudioOrigin =
  process.env.NEXT_PUBLIC_STUDIO_ORIGIN ?? 'http://localhost:3000';

export const gitConfig = {
  user: 'hoimingkenny',
  repo: 'iam-protocol-engine',
  branch: 'main',
};
