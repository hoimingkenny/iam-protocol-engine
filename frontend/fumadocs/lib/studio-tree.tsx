import type { Root } from 'fumadocs-core/page-tree';
import {
  ArrowRightLeft,
  BookMarked,
  FileJson2,
  ShieldCheck,
} from 'lucide-react';

export const studioTree: Root = {
  name: 'Interactive Docs',
  children: [
    {
      type: 'page',
      name: 'Studio Overview',
      url: '/studio',
      icon: <BookMarked className="size-4" />,
    },
    {
      type: 'page',
      name: 'OAuth 2.0 PKCE',
      url: '/studio/oauth-pkce',
      icon: <ArrowRightLeft className="size-4" />,
    },
    {
      type: 'page',
      name: 'OIDC Login',
      url: '/studio/oidc-login',
      icon: <ShieldCheck className="size-4" />,
    },
    {
      type: 'page',
      name: 'Callback Inspector',
      url: '/studio/callback',
      icon: <ArrowRightLeft className="size-4" />,
    },
    {
      type: 'page',
      name: 'API Reference',
      url: '/studio/api-reference',
      icon: <FileJson2 className="size-4" />,
    },
  ],
};
