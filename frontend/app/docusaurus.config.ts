import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'IAM Protocol Engine',
  tagline: 'RFC-level IAM from scratch',
  favicon: 'img/favicon.ico',

  future: {
    v4: true,
  },

  url: 'https://hoimingkenny.github.io',
  baseUrl: '/iam-protocol-engine/',
  organizationName: 'hoimingkenny',
  projectName: 'iam-protocol-engine',

  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          routeBasePath: '/',
          showLastUpdateTime: true,
          editUrl: 'https://github.com/hoimingkenny/iam-protocol-engine/edit/main/frontend/app/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    image: 'img/docusaurus-social-card.jpg',
    colorMode: {
      defaultMode: 'dark',
      respectPrefersColorScheme: false,
    },
    navbar: {
      title: 'IAM Protocol Engine',
      style: 'dark',
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'introduction',
          position: 'left',
          label: 'Introduction',
        },
        {
          type: 'docSidebar',
          sidebarId: 'bootstrap',
          position: 'left',
          label: 'Bootstrap',
        },
        {
          type: 'docSidebar',
          sidebarId: 'oauth2',
          position: 'left',
          label: 'OAuth 2.0',
        },
        {
          type: 'docSidebar',
          sidebarId: 'oidc',
          position: 'left',
          label: 'OIDC',
        },
        {
          type: 'docSidebar',
          sidebarId: 'reference',
          position: 'left',
          label: 'Reference',
        },
        {
          href: 'https://github.com/hoimingkenny/iam-protocol-engine',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      copyright: `Copyright © ${new Date().getFullYear()} IAM Protocol Engine. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
