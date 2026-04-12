import { Suspense } from 'react';
import { DocsBody, DocsDescription, DocsPage, DocsTitle } from 'fumadocs-ui/layouts/docs/page';
import { CallbackInspector } from '@/components/callback-inspector';

export default function CallbackPage() {
  return (
    <DocsPage>
      <DocsTitle>Callback Inspector</DocsTitle>
      <DocsDescription>
        Read the returned query parameters, replay the token exchange through
        the local proxy, and inspect the resulting JWT claims.
      </DocsDescription>
      <DocsBody>
        <Suspense fallback={<p>Loading callback state…</p>}>
          <CallbackInspector />
        </Suspense>
      </DocsBody>
    </DocsPage>
  );
}
