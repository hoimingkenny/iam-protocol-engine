---
title: IAM Protocol Engine - Documentation UI Playbook
tags:
  - docs
  - docusaurus
  - frontend
  - troubleshooting
status: active
updated: 2026-04-12
---

# IAM Protocol Engine - Documentation UI Playbook

> [!abstract] Purpose
> This note records documentation UI mistakes, fixes, and preferred implementation approaches so we can move faster and avoid repeating the same problems.

---

## Working Principles

- prefer native Docusaurus features before custom theme overrides
- use small CSS adjustments before building custom layout components
- verify docs changes with `npm run build`
- when an asset or MDX feature behaves strangely, check Docusaurus pathing and base URL rules first

---

## Issue: Mermaid Flow Chart Was Too Small To Read

### Problem

- inline Mermaid rendered, but the sequence diagram was too dense and too small for comfortable reading

### Decision

- switch from inline Mermaid rendering to an image asset for that specific flow chart
- use `SVG`, not PNG, so the diagram stays sharp when enlarged

### Implementation

1. store Mermaid source as a file:
   - `frontend/app/static/img/oauth2-pkce-flow.mmd`
2. generate an SVG:
   - `npx -y @mermaid-js/mermaid-cli@11.4.2 -i static/img/oauth2-pkce-flow.mmd -o static/img/oauth2-pkce-flow.svg -b transparent -s 2`
3. embed the SVG in the doc page
4. make the image clickable so it opens in a new tab for zooming

### Why This Approach

- `SVG` scales cleanly
- easier to zoom than inline Mermaid
- avoids fighting Mermaid layout constraints inside the article column

---

## Issue: SVG Image Rendered As Broken Image

### Problem

- the image showed as broken in the doc page

### Root Cause

- the page used a hardcoded path like `/img/oauth2-pkce-flow.svg`
- Docusaurus site is served with a base URL, so hardcoded root-relative paths are fragile

### Fix

- use Docusaurus base URL handling in MDX:

```mdx
import useBaseUrl from '@docusaurus/useBaseUrl';

<a href={useBaseUrl('/img/oauth2-pkce-flow.svg')} target="_blank" rel="noreferrer">
  <img
    src={useBaseUrl('/img/oauth2-pkce-flow.svg')}
    alt="OAuth 2.0 Authorization Code plus PKCE flow"
  />
</a>
```

### Rule To Remember

- in Docusaurus MDX, prefer `useBaseUrl()` for static assets when using JSX/HTML props
- do not assume `/img/...` will work in deployed environments

---

## Issue: Sidebar And TOC Behavior

### What Docusaurus Supports Natively

- `themeConfig.docs.sidebar.hideable`
- `themeConfig.docs.sidebar.autoCollapseCategories`

### What We Learned

- native Docusaurus support is good for sidebar show/hide
- a desktop TOC hide/show control is not a simple built-in config
- an icon-rail collapsed sidebar like some reference sites would require custom theme overrides

### Preferred Approach

- stay close to native Docusaurus behavior unless there is a strong UX reason not to
- avoid heavy custom shell/layout rewrites for docs unless the design benefit is clearly worth the maintenance cost

---

## Issue: Content Overlapping TOC

### Problem

- content area looked wider after sidebar changes, but a flow-chart container could overlap the TOC rail

### Root Cause

- the diagram container used viewport-based width instead of column-based width

### Fix

- constrain the diagram container to the article column:

```css
.diagram-card {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}
```

### Rule To Remember

- inside docs content, prefer container-relative sizing over viewport-relative sizing
- especially important when a right-side TOC is present

---

## Current Styling Preferences

### Reading Layout

- centered content column
- slightly wider article width than default for easier reading
- preserve TOC space on the right

### Code Blocks

Preferred style:

- generous inner padding
- soft rounded border
- light, calm background
- filename/title area with breathing room

This preference is intentional and should be preserved unless a future redesign replaces the overall docs visual system.

---

## Default Troubleshooting Workflow For Docs Issues

1. reproduce the issue in the docs page
2. check whether Docusaurus already supports it natively
3. inspect current config before adding custom code
4. keep changes local:
   - doc page content
   - small CSS adjustment
   - config change
   - theme override only if necessary
5. build with:

```bash
cd frontend/app
npm run build
```

6. if the issue is asset-related, check:
   - file exists in `static/`
   - base URL handling
   - generated output path

---

## Short Rules

- prefer native Docusaurus first
- prefer `SVG` for technical diagrams
- prefer `useBaseUrl()` for MDX asset paths
- prefer container-based width inside docs content
- verify every docs change with a production build
