# Reusable Learning Site UI/UX Brief

## Purpose

Use this brief when restyling a docs-heavy learning site into a premium developer education platform.

This version reflects what actually worked in this repository during Phase 10:

- keep the docs platform
- redesign the shell
- make navigation curriculum-first
- keep the reading experience calm and technical
- avoid over-designed UI patterns that fight the content

This is intended to be reusable for future learning-site work.

---

## Product Goal

The site should immediately answer 4 questions for the learner:

1. where am I?
2. which track or chapter am I in?
3. what comes next?
4. how do I move through the full curriculum?

The site should feel like a structured learning system, not a blog and not a marketing page.

---

## Reference Style

Use AgentWay Learn only as a structural inspiration.

Take inspiration from:

- product shell
- curriculum visibility
- lesson progression
- docs-first reading flow
- calm educational UX

Do not copy:

- branding
- wording
- layouts exactly
- icons or illustrations
- colors exactly

The result should feel original and production-ready.

---

## Platform Guidance

### Preferred Approach

If the content already lives in Docusaurus, keep Docusaurus.

Do not migrate to a new framework unless there is a separate approved architecture decision.

For this style of project, Docusaurus is usually enough if you customize:

- navbar
- sidebar
- lesson shell
- table of contents
- typography
- table and code block styling

### Implementation Pattern

- use `src/theme/` overrides when needed
- use reusable React components in `src/components/`
- keep tokens in CSS variables
- use global docs-shell styling in `src/css/custom.css`
- preserve existing docs routes unless there is a strong IA reason to change them

---

## Design Direction

### Style Language

- Swiss modern editorial
- minimal premium SaaS
- technical documentation clarity

### Mood

- calm
- trustworthy
- intelligent
- structured
- focused

### Avoid

- purple-heavy AI startup styling
- loud gradients
- glassmorphism
- over-rounded card UI
- big shadows
- playful badges
- pill-like inline code everywhere
- sidebar rows that look like buttons unless they truly are buttons

---

## Core Layout

Use a 4-zone learning shell:

1. sticky top navbar
2. sticky left curriculum sidebar
3. main reading column
4. optional right-side TOC on large screens

### Layout Rules

- light theme first
- content width around 760px to 800px
- keep a wide outer shell, but a restrained reading column
- sticky regions must never overlap content
- mobile layout must collapse to a single readable column without horizontal scroll

---

## Design Tokens

Use CSS variables.

```css
--bg: #F8FAFC;
--surface: #FFFFFF;
--surface-muted: #F1F5F9;
--text: #020617;
--text-muted: #475569;
--border: #E2E8F0;
--accent: #2563EB;
--accent-strong: #1D4ED8;
--accent-soft: #DBEAFE;
--success: #059669;
--warning: #D97706;
```

### Spacing

Use this rhythm:

- 4
- 8
- 12
- 16
- 24
- 32
- 48
- 64

### Radius

- subtle only
- prefer `8px` to `12px`
- avoid over-rounding on docs UI

### Elevation

- thin borders first
- very soft shadows only where needed
- avoid floating card stacks in navigation

---

## Typography

### Font Roles

- headings: `DM Sans` or `Satoshi`
- body and UI: `Public Sans`
- mono/meta/code: `IBM Plex Mono`

### Rules

- one sidebar should feel like one font system
- do not mix mono-style category labels with sans lesson rows unless there is a very strong reason
- hierarchy should come primarily from:
  - indentation
  - color
  - spacing
  - subtle weight shifts
- not from wildly different sizes or type personalities

### Reading Rules

- H1 should be compact and confident
- paragraph text should be calm and readable
- long-form docs should feel editorial, not blog-like

---

## Information Architecture

Organize the curriculum into named tracks.

Recommended structure:

- Preparation
- Foundations
- Systems
- Advanced
- Specials

### IA Rules

- stable lesson IDs should be visible
- sequence should be obvious
- advanced topics should be separated clearly
- docs content should remain reachable
- overview pages should be part of the sequence, not hidden behind odd navigation behavior

### Sidebar Rule Learned From Implementation

Do not make a sidebar category behave as both:

- a collapsible section
- and a clickable overview link

That causes confusing caret behavior.

Instead:

- keep categories purely collapsible
- place the overview doc as the first child item inside the section

---

## Sidebar Guidance

The sidebar is the most important orientation tool in the product.

### Functional Role

It should feel like:

- a syllabus
- a track map
- a structured learning rail

It should not feel like:

- a file tree
- a stack of buttons
- a generic docs menu

### Visual Rules

- no borders around every item
- avoid pill/button outlines
- use neutral hover and active fills
- keep active state calm in light mode
- active state should feel like a highlighted row, not a selected button
- use dark text on soft neutral background for active items in light mode
- keep chevrons understated

### Typography Rules

- use the same font family for track rows, section rows, and lesson rows
- keep sizes nearly consistent
- use color and indentation for hierarchy
- avoid all-caps utility styling unless it is extremely restrained

### State Rules

Support:

- default
- hover
- active/current
- focus
- completed
- preview/locked

Do not rely on color alone for completed or preview states.

### Active State Recommendation

For light mode, prefer something like:

- soft neutral background
- dark text
- no border
- minimal focus ring

Avoid bright blue active pills for the sidebar unless the whole system is intentionally more saturated.

---

## Navbar Guidance

### Requirements

- sticky top
- 64px height
- semi-opaque white background
- subtle blur
- bottom border instead of heavy shadow

Include:

- brand
- main navigation
- search trigger
- language switcher or placeholder
- account/sign-in CTA if useful

### Behavior

- should feel infrastructural, not promotional
- search trigger should resemble a docs command entry
- keep hover states quiet and precise

---

## Lesson Page Guidance

### Top-of-Page Structure

- strong H1
- one-sentence summary
- metadata row
- optional info capsule strip

Suggested metadata:

- lesson ID
- duration
- lesson type
- prerequisites
- related exercise

### Content Behavior

- headings should create scan rhythm
- code blocks should be visually clean and actually syntax-highlighted
- callouts should be restrained
- tables should feel editorial, not default markdown output

---

## Code Block Rules

### Required

- syntax highlighting must be enabled for the real languages used in the docs
- for a Java-heavy project, explicitly load Java Prism support

### Visual Rules

- code blocks should have:
  - clean border
  - subtle radius
  - quiet shadow or no shadow
  - balanced padding

### Inline Code Rules

- inline code in prose can have a subtle background
- inline code inside tables should usually be flattened

Do not render technical identifiers in tables as rounded pills.

That looks noisy and breaks reading flow.

---

## Table Rules

Tables often make or break docs polish.

### Do

- keep borders subtle
- use a restrained header background
- preserve readability for technical names
- flatten inline code inside cells

### Avoid

- strong grid lines everywhere
- tag-like code chips inside cells
- bulky row height

---

## Motion

- subtle only
- prefer 180ms to 220ms transitions
- animate background, color, opacity, and minimal shadow
- respect `prefers-reduced-motion`

Avoid:

- bounce
- flashy entrances
- decorative motion in docs navigation

---

## Accessibility

Required:

- keyboard navigation for navbar, sidebar, TOC, and lesson navigation
- visible but calm focus states
- no color-only status encoding
- no sticky overlap
- no horizontal scroll on small screens

---

## Component Checklist

If building a full learning shell, create or restyle:

- `GlobalHeader`
- `SearchTrigger`
- `LanguageSwitcher`
- `LearnSidebar`
- `TrackSection`
- `LessonNavItem`
- `LessonBadge`
- `LessonHeader`
- `LessonMetaBar`
- `InfoCapsuleRow`
- `ContentCallout`
- `PrevNextFooter`
- `ProgressCTA`
- `MobileCourseDrawer`
- `RightToc`

Not every project needs all of them on day one, but this is the target system.

---

## Reusable Acceptance Criteria

The design is successful if:

- the site feels like a premium docs-based learning platform
- the learner always knows where they are
- progression is obvious
- the sidebar feels like a syllabus, not a file explorer
- the reading experience is calm and structured
- code and tables feel intentional, not default markdown output
- the design feels original while clearly learning from the strengths of AgentWay Learn

---

## Final Rule

Return implemented code, not just visual commentary.

If the site already has working docs, preserve content and improve the shell first.
