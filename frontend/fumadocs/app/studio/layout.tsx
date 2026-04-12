import { DocsLayout } from 'fumadocs-ui/layouts/docs';
import { baseOptions } from '@/lib/layout.shared';
import { studioTree } from '@/lib/studio-tree';

export default function StudioLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <DocsLayout tree={studioTree} {...baseOptions()}>
      {children}
    </DocsLayout>
  );
}
