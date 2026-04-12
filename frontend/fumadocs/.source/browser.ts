// @ts-nocheck
import { browser } from 'fumadocs-mdx/runtime/browser';
import type * as Config from '../source.config';

const create = browser<typeof Config, import("fumadocs-mdx/runtime/types").InternalTypeConfig & {
  DocData: {
  }
}>();
const browserCollections = {
  docs: create.doc("docs", {"index.mdx": () => import("../content/docs/index.mdx?collection=docs"), "notes-and-experiments.mdx": () => import("../content/docs/notes-and-experiments.mdx?collection=docs"), "reference-links.mdx": () => import("../content/docs/reference-links.mdx?collection=docs"), }),
};
export default browserCollections;