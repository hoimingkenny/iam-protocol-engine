import { DocsBody, DocsDescription, DocsPage, DocsTitle } from 'fumadocs-ui/layouts/docs/page';
import { FlowExplorer } from '@/components/flow-explorer';

export default function OidcLoginPage() {
  return (
    <DocsPage>
      <DocsTitle>OIDC Login Explorer</DocsTitle>
      <DocsDescription>
        Add discovery, nonce handling, ID token decoding, and UserInfo lookup
        to the same PKCE-based authorization code flow.
      </DocsDescription>
      <DocsBody>
        <FlowExplorer mode="oidc" />
      </DocsBody>
    </DocsPage>
  );
}
