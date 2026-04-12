import { createAPIPage } from 'fumadocs-openapi/ui';
import { DocsBody, DocsDescription, DocsPage, DocsTitle } from 'fumadocs-ui/layouts/docs/page';
import { openApiServer } from '@/lib/openapi';

const APIPage = createAPIPage(openApiServer, {
  playground: {
    enabled: false,
  },
});

export default function ApiReferencePage() {
  return (
    <DocsPage>
      <DocsTitle>API Reference</DocsTitle>
      <DocsDescription>
        A selected OpenAPI slice covering the endpoints used by the interactive
        OAuth and OIDC flows.
      </DocsDescription>
      <DocsBody>
        <p>
          This page intentionally covers only the endpoints the studio uses on
          day one: authorize, token exchange, discovery, JWKS, and UserInfo.
        </p>
        <APIPage
          document="iam"
          showTitle={false}
          operations={[
            { path: '/oauth2/authorize', method: 'get' },
            { path: '/oauth2/token', method: 'post' },
            { path: '/.well-known/openid-configuration', method: 'get' },
            { path: '/.well-known/jwks.json', method: 'get' },
            { path: '/userinfo', method: 'get' },
          ]}
        />
      </DocsBody>
    </DocsPage>
  );
}
