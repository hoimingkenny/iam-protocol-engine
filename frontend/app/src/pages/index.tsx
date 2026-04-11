import type {ReactNode} from 'react';
import Layout from '@theme/Layout';

import LearningHome from '@site/src/components/LearningHome';

export default function Home(): ReactNode {
  return (
    <Layout
      title="IAM Protocol Engine"
      description="A premium learning site for understanding OAuth 2.0, OIDC, SAML, SCIM, MFA, and IAM architecture from first principles.">
      <main className="site-home">
        <div className="site-shell">
          <LearningHome />
        </div>
      </main>
    </Layout>
  );
}
