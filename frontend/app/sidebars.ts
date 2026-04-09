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

  reference: [
    'Reference/System-Architecture',
    'Reference/Learning-Notes',
    'Reference/Spec',
    'Reference/Implementation-Plan',
  ],
};

export default sidebars;
