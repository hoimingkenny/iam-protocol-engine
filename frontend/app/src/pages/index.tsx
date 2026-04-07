import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';

import styles from './index.module.css';

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className={clsx('hero hero--primary', styles.heroBanner)}>
      <div className="container">
        <Heading as="h1" className="hero__title">
          {siteConfig.title}
        </Heading>
        <p className="hero__subtitle">{siteConfig.tagline}</p>
        <div className={styles.buttons}>
          <Link
            className="button button--secondary button--lg"
            to="/Introduction/Why-This-Project">
            Start Learning
          </Link>
        </div>
      </div>
    </header>
  );
}

export default function Home(): ReactNode {
  const {siteConfig} = useDocusaurusContext();
  return (
    <Layout
      title={siteConfig.title}
      description="Portfolio-grade enterprise IAM platform demo — OAuth 2.0, OIDC, SAML, SCIM, WebAuthn, TOTP">
      <HomepageHeader />
      <main>
        <section className={styles.features}>
          <div className="container">
            <div className="row">
              <div className="col">
                <h2>9 Phases</h2>
                <p>From bootstrap to full OAuth 2.0, OIDC, SAML, SCIM, and MFA.</p>
              </div>
              <div className="col">
                <h2>RFC-Level Depth</h2>
                <p>Every endpoint, token, and assertion built by hand — not configured.</p>
              </div>
              <div className="col">
                <h2>Java + Spring Boot</h2>
                <p>Enterprise-grade stack with PostgreSQL and Redis.</p>
              </div>
            </div>
          </div>
        </section>
      </main>
    </Layout>
  );
}
