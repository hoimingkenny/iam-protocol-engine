import { DocsBody, DocsDescription, DocsPage, DocsTitle } from 'fumadocs-ui/layouts/docs/page';
import { FlowExplorer } from '@/components/flow-explorer';

export default function OAuthPkcePage() {
  return (
    <DocsPage>
      <DocsTitle>OAuth 2.0 PKCE Explorer</DocsTitle>
      <DocsDescription>
        Walk the authorization code flow as a series of inspectable requests,
        redirects, and token payloads.
      </DocsDescription>
      <DocsBody>
        <FlowExplorer mode="oauth" />
      </DocsBody>
    </DocsPage>
  );
}
