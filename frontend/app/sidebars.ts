import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  introduction: [
    'Introduction/Why-This-Project',
  ],

  bootstrap: [
    'Bootstrap/Overview',
    'Bootstrap/Maven-Modules',
    'Bootstrap/Docker-Compose',
    'Bootstrap/JPA-Entities',
    'Bootstrap/API-Gateway',
    'Bootstrap/Tests',
  ],

  oauth2: [
    'OAuth2/Overview',
    'OAuth2/PKCE',
    'OAuth2/Authorize-Endpoint',
    'OAuth2/Token-Endpoint',
    'OAuth2/Client-Credentials',
    'OAuth2/Demo-Resource',
  ],

  oidc: [
    'OIDC/Overview',
    'OIDC/Discovery',
    'OIDC/JWKS',
    'OIDC/ID-Token',
    'OIDC/UserInfo',
  ],

  tokenLifecycle: [
    'Token-Lifecycle/Overview',
    'Token-Lifecycle/Refresh-Rotation',
    'Token-Lifecycle/Introspection',
    'Token-Lifecycle/Revocation',
  ],

  adminUi: [
    'Admin-UI/Overview',
    'Admin-UI/Login-Flow',
    'Admin-UI/Admin-API',
  ],

  scim: [
    'SCIM/Overview',
    'SCIM/Users',
    'SCIM/Groups',
  ],

  saml: [
    'SAML/Overview',
    'SAML/SP-Metadata',
    'SAML/AuthnRequest',
    'SAML/ACS',
    'SAML/SAML-OIDC-Bridge',
  ],

  mfa: [
    'MFA/Overview',
    'MFA/TOTP',
    'MFA/WebAuthn',
    'MFA/Device-Flow',
  ],

  reference: [
    'Reference/System-Architecture',
    'Reference/Learning-Notes',
    'Reference/Spec',
    'Reference/Implementation-Plan',
  ],
};

export default sidebars;
