import { source } from '@/lib/source';
import { DocsLayout } from 'fumadocs-ui/layouts/docs';
import { baseOptions } from '@/lib/layout.shared';

const pageTree = {
  name: 'docs',
  children: [
    {
      type: 'separator' as const,
      name: 'Introduction',
    },
    {
      type: 'page' as const,
      name: 'Overview',
      url: '/docs/introduction/why-this-project',
    },
    {
      type: 'folder' as const,
      name: 'Bootstrap',
      children: [
        {
          type: 'page' as const,
          name: 'Maven Modules',
          url: '/docs/bootstrap/maven-modules',
        },
        {
          type: 'page' as const,
          name: 'Docker Compose',
          url: '/docs/bootstrap/docker-compose',
        },
        {
          type: 'page' as const,
          name: 'JPA Entities',
          url: '/docs/bootstrap/jpa-entities',
        },
        {
          type: 'page' as const,
          name: 'API Gateway',
          url: '/docs/bootstrap/api-gateway',
        },
        {
          type: 'page' as const,
          name: 'Tests',
          url: '/docs/bootstrap/tests',
        },
      ],
    },
    {
      type: 'separator' as const,
      name: 'Protocol',
    },
    {
      type: 'folder' as const,
      name: 'OAuth 2.0 Core',
      children: [
        {
          type: 'page' as const,
          name: 'PKCE',
          url: '/docs/oauth2/pkce',
        },
        {
          type: 'page' as const,
          name: '/authorize Endpoint',
          url: '/docs/oauth2/authorize-endpoint',
        },
        {
          type: 'page' as const,
          name: '/token Endpoint',
          url: '/docs/oauth2/token-endpoint',
        },
        {
          type: 'page' as const,
          name: 'Client Credentials',
          url: '/docs/oauth2/client-credentials',
        },
        {
          type: 'page' as const,
          name: 'Demo Resource',
          url: '/docs/oauth2/demo-resource',
        },
      ],
    },
    {
      type: 'folder' as const,
      name: 'OIDC Layer',
      children: [
        {
          type: 'page' as const,
          name: 'Discovery',
          url: '/docs/oidc/discovery',
        },
        {
          type: 'page' as const,
          name: 'JWKS',
          url: '/docs/oidc/jwks',
        },
        {
          type: 'page' as const,
          name: 'ID Token',
          url: '/docs/oidc/id-token',
        },
        {
          type: 'page' as const,
          name: 'UserInfo',
          url: '/docs/oidc/userinfo',
        },
      ],
    },
    {
      type: 'folder' as const,
      name: 'Token Lifecycle',
      children: [
        {
          type: 'page' as const,
          name: 'Refresh Rotation',
          url: '/docs/token-lifecycle/refresh-rotation',
        },
        {
          type: 'page' as const,
          name: 'Introspection',
          url: '/docs/token-lifecycle/introspection',
        },
        {
          type: 'page' as const,
          name: 'Revocation',
          url: '/docs/token-lifecycle/revocation',
        },
      ],
    },
    {
      type: 'folder' as const,
      name: 'SCIM 2.0',
      children: [
        {
          type: 'page' as const,
          name: 'Overview',
          url: '/docs/scim/overview',
        },
        {
          type: 'page' as const,
          name: 'SailPoint Connector',
          url: '/docs/scim/sailpoint-connector',
        },
        {
          type: 'page' as const,
          name: 'Users',
          url: '/docs/scim/users',
        },
        {
          type: 'page' as const,
          name: 'Groups',
          url: '/docs/scim/groups',
        },
      ],
    },
    {
      type: 'folder' as const,
      name: 'SAML 2.0',
      children: [
        {
          type: 'page' as const,
          name: 'SP Metadata',
          url: '/docs/saml/sp-metadata',
        },
        {
          type: 'page' as const,
          name: 'AuthnRequest',
          url: '/docs/saml/authn-request',
        },
        {
          type: 'page' as const,
          name: 'ACS',
          url: '/docs/saml/acs',
        },
        {
          type: 'page' as const,
          name: 'SAML-OIDC Bridge',
          url: '/docs/saml/saml-oidc-bridge',
        },
      ],
    },
    {
      type: 'folder' as const,
      name: 'MFA',
      children: [
        {
          type: 'page' as const,
          name: 'TOTP',
          url: '/docs/mfa/totp',
        },
        {
          type: 'page' as const,
          name: 'WebAuthn',
          url: '/docs/mfa/webauthn',
        },
        {
          type: 'page' as const,
          name: 'Device Flow',
          url: '/docs/mfa/device-flow',
        },
      ],
    },
    {
      type: 'separator' as const,
      name: 'Other',
    },
    {
      type: 'folder' as const,
      name: 'Admin UI',
      children: [
        {
          type: 'page' as const,
          name: 'Login Flow',
          url: '/docs/admin-ui/login-flow',
        },
        {
          type: 'page' as const,
          name: 'Admin API',
          url: '/docs/admin-ui/admin-api',
        },
      ],
    },
    {
      type: 'folder' as const,
      name: 'Demo Hardening',
      children: [
        {
          type: 'page' as const,
          name: 'Demo Script',
          url: '/docs/demo-hardening/demo-script',
        },
        {
          type: 'page' as const,
          name: 'Architecture',
          url: '/docs/demo-hardening/architecture',
        },
      ],
    },
    {
      type: 'folder' as const,
      name: 'Reference',
      children: [
        {
          type: 'page' as const,
          name: 'Specification',
          url: '/docs/reference/spec',
        },
        {
          type: 'page' as const,
          name: 'System Architecture',
          url: '/docs/reference/system-architecture',
        },
        {
          type: 'page' as const,
          name: 'Learning Notes',
          url: '/docs/reference/learning-notes',
        },
        {
          type: 'page' as const,
          name: 'Implementation Plan',
          url: '/docs/reference/implementation-plan',
        },
      ],
    },
    {
      type: 'separator' as const,
      name: 'Links',
    },
    {
      type: 'page' as const,
      name: 'Reference Links',
      url: '/docs/reference-links',
    },
    {
      type: 'page' as const,
      name: 'Notes & Experiments',
      url: '/docs/notes-and-experiments',
    },
  ],
} satisfies Parameters<typeof DocsLayout>[0] extends { tree: infer T } ? T : never;

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <DocsLayout tree={pageTree as any} {...baseOptions()}>
      {children}
    </DocsLayout>
  );
}
