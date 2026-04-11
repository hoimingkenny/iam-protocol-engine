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
          sidebarCollapsible: true,
          sidebarCollapsed: false,
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
      defaultMode: 'light',
      disableSwitch: true,
      respectPrefersColorScheme: false,
    },
    navbar: {
      title: 'IAM Learn',
      items: [
        {
          to: '/',
          label: 'Home',
          position: 'left',
        },
        {
          type: 'docSidebar',
          sidebarId: 'curriculum',
          position: 'left',
          label: 'Curriculum',
        },
        {
          to: '/Reference/System-Architecture',
          label: 'Reference',
          position: 'left',
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
      additionalLanguages: ['java', 'sql', 'powershell', 'yaml', 'json', 'bash'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
