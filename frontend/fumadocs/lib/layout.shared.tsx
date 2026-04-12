import type { BaseLayoutProps } from 'fumadocs-ui/layouts/shared';
import { appName, gitConfig, studioRoute } from './shared';
import { BookOpenText, Compass, ExternalLink, FileCode2 } from 'lucide-react';

export function baseOptions(): BaseLayoutProps {
  return {
    nav: {
      title: appName,
      transparentMode: 'top',
    },
    githubUrl: `https://github.com/${gitConfig.user}/${gitConfig.repo}`,
    themeSwitch: {
      enabled: true,
      mode: 'light-dark-system',
    },
    searchToggle: {
      enabled: true,
    },
    links: [
      {
        text: 'Overview',
        url: '/',
        icon: <Compass className="size-4" />,
      },
      {
        text: 'Docs',
        url: '/docs',
        icon: <BookOpenText className="size-4" />,
      },
      {
        text: 'Studio',
        url: studioRoute,
        active: 'nested-url',
        icon: <FileCode2 className="size-4" />,
      },
      {
        type: 'icon',
        url: `https://github.com/${gitConfig.user}/${gitConfig.repo}`,
        text: 'GitHub',
        label: 'GitHub repository',
        icon: <ExternalLink className="size-4" />,
      },
    ],
  };
}
