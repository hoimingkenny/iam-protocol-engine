import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  curriculum: [
    {
      type: 'category',
      label: 'Track 0  Preparation',
      items: ['Introduction/Why-This-Project'],
    },
    {
      type: 'category',
      label: 'Track 1  Foundations',
      items: [
        {
          type: 'category',
          label: 'P1  Bootstrap',
          items: [
            'Bootstrap/Overview',
            'Bootstrap/Maven-Modules',
            'Bootstrap/Docker-Compose',
            'Bootstrap/JPA-Entities',
            'Bootstrap/API-Gateway',
            'Bootstrap/Tests',
          ],
        },
        {
          type: 'category',
          label: 'P2  OAuth 2.0 Core',
          items: [
            'OAuth2/Overview',
            'OAuth2/PKCE',
            'OAuth2/Authorize-Endpoint',
            'OAuth2/Token-Endpoint',
            'OAuth2/Client-Credentials',
            'OAuth2/Demo-Resource',
          ],
        },
        {
          type: 'category',
          label: 'P3  OIDC Layer',
          items: [
            'OIDC/Overview',
            'OIDC/Discovery',
            'OIDC/JWKS',
            'OIDC/ID-Token',
            'OIDC/UserInfo',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: 'Track 2  Systems',
      items: [
        {
          type: 'category',
          label: 'P4  Token Lifecycle',
          items: [
            'Token-Lifecycle/Overview',
            'Token-Lifecycle/Refresh-Rotation',
            'Token-Lifecycle/Introspection',
            'Token-Lifecycle/Revocation',
          ],
        },
        {
          type: 'category',
          label: 'P5  Admin UI',
          items: [
            'Admin-UI/Overview',
            'Admin-UI/Login-Flow',
            'Admin-UI/Admin-API',
          ],
        },
        {
          type: 'category',
          label: 'P6  SCIM 2.0',
          items: [
            'SCIM/Overview',
            'SCIM/Users',
            'SCIM/Groups',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: 'Track 3  Advanced',
      items: [
        {
          type: 'category',
          label: 'P7  SAML 2.0',
          items: [
            'SAML/Overview',
            'SAML/SP-Metadata',
            'SAML/AuthnRequest',
            'SAML/ACS',
            'SAML/SAML-OIDC-Bridge',
          ],
        },
        {
          type: 'category',
          label: 'P8  Modern Auth',
          items: [
            'MFA/Overview',
            'MFA/TOTP',
            'MFA/WebAuthn',
            'MFA/Device-Flow',
          ],
        },
        {
          type: 'category',
          label: 'P9  Demo Hardening',
          items: [
            'Demo-Hardening/Overview',
            'Demo-Hardening/Demo-Script',
            'Demo-Hardening/Architecture',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: 'Specials  Reference',
      items: [
        'Reference/System-Architecture',
        'Reference/Learning-Notes',
        'Reference/Spec',
      ],
    },
  ],
};

export default sidebars;
