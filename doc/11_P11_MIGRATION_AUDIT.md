# Phase 11 — Docusaurus → Fumadocs Migration Audit

**Phase:** Task 37
**Date:** 2026-04-12
**Scope:** Static content migration from Docusaurus (`frontend/app/docs/`) to Fumadocs (`frontend/fumadocs/content/docs/`)

---

## Migration Decision Summary

| Source File | Destination | Decision | Reason |
|-------------|-------------|----------|--------|
| **Reference docs** | | | |
| `Reference/Spec.md` | `reference/spec.mdx` | **Migrate** | Canonical spec — high-value reference, static content |
| `Reference/System-Architecture.md` | `reference/system-architecture.mdx` | **Migrate** | Architecture overview, static reference |
| `Reference/Learning-Notes.md` | `reference/learning-notes.mdx` | **Migrate** | Interview notes, static narrative |
| `Reference/Implementation-Plan.md` | — | **Do not migrate** | Already in repo root; large; not docs content |
| **Phase overview pages** | | | |
| `01-Bootstrap/00-Overview.md` | `phases/01-bootstrap-overview.mdx` | **Migrate** | Phase narrative summary |
| `02-OAuth2/00-Overview.md` | `phases/02-oauth2-overview.mdx` | **Migrate** | Phase narrative summary |
| `03-OIDC/00-Overview.md` | `phases/03-oidc-overview.mdx` | **Migrate** | Phase narrative summary |
| `04-Token-Lifecycle/00-Overview.md` | `phases/04-token-lifecycle-overview.mdx` | **Migrate** | Phase narrative summary |
| `05-Admin-UI/00-Overview.md` | `phases/05-admin-ui-overview.mdx` | **Migrate** | Phase narrative summary |
| `06-SCIM/00-Overview.md` | `phases/06-scim-overview.mdx` | **Migrate** | Phase narrative summary |
| `07-SAML/00-Overview.md` | `phases/07-saml-overview.mdx` | **Migrate** | Phase narrative summary |
| `08-MFA/00-Overview.md` | `phases/08-mfa-overview.mdx` | **Migrate** | Phase narrative summary |
| `09-Demo-Hardening/00-Overview.md` | `phases/09-demo-hardening-overview.mdx` | **Migrate** | Phase narrative summary |
| **Deep-dive protocol pages** | | | |
| `02-OAuth2/01-PKCE.md` | — | **Do not migrate** | Covered by interactive PKCE explorer in Task 34 |
| `02-OAuth2/02-Authorize-Endpoint.md` | — | **Do not migrate** | Covered by interactive flow lab (Task 34) |
| `02-OAuth2/03-Token-Endpoint.md` | — | **Do not migrate** | Covered by interactive flow lab (Task 34) |
| `02-OAuth2/04-Client-Credentials.md` | — | **Do not migrate** | Covered by demo-resource flow (Task 34) |
| `02-OAuth2/05-Demo-Resource.md` | — | **Do not migrate** | Covered by API reference (Task 35) |
| `03-OIDC/01-Discovery.md` | — | **Do not migrate** | Covered by OIDC explorer (Task 34) |
| `03-OIDC/02-JWKS.md` | — | **Do not migrate** | Covered by OIDC explorer (Task 34) |
| `03-OIDC/03-ID-Token.md` | — | **Do not migrate** | Covered by OIDC explorer (Task 34) |
| `03-OIDC/04-UserInfo.md` | — | **Do not migrate** | Covered by OIDC explorer (Task 34) |
| `04-Token-Lifecycle/01-Refresh-Rotation.md` | — | **Do not migrate** | Narrative deep-dive; interactive not needed |
| `04-Token-Lifecycle/02-Introspection.md` | — | **Do not migrate** | Covered by API reference (Task 35) |
| `04-Token-Lifecycle/03-Revocation.md` | — | **Do not migrate** | Covered by API reference (Task 35) |
| `05-Admin-UI/01-Login-Flow.md` | — | **Do not migrate** | Narrative only; no interactive value |
| `05-Admin-UI/02-Admin-API.md` | — | **Do not migrate** | Covered by API reference (Task 35) |
| `06-SCIM/01-Users.md` | — | **Do not migrate** | Covered by API reference (Task 35) |
| `06-SCIM/02-Groups.md` | — | **Do not migrate** | Covered by API reference (Task 35) |
| `07-SAML/01-SP-Metadata.md` | — | **Do not migrate** | Static reference; Docusaurus sufficient |
| `07-SAML/02-AuthnRequest.md` | — | **Do not migrate** | Static reference; Docusaurus sufficient |
| `07-SAML/03-ACS.md` | — | **Do not migrate** | Static reference; Docusaurus sufficient |
| `07-SAML/04-SAML-OIDC-Bridge.md` | — | **Do not migrate** | Static reference; Docusaurus sufficient |
| `08-MFA/01-TOTP.md` | — | **Do not migrate** | Static reference; Docusaurus sufficient |
| `08-MFA/02-WebAuthn.md` | — | **Do not migrate** | Static reference; Docusaurus sufficient |
| `08-MFA/03-Device-Flow.md` | — | **Do not migrate** | Covered by API reference (Task 35) |
| `09-Demo-Hardening/01-Demo-Script.md` | — | **Do not migrate** | Docusaurus sufficient; demo script is the canonical form |
| `09-Demo-Hardening/02-Architecture.md` | — | **Do not migrate** | Docusaurus sufficient; System-Architecture.md covers this |
| `00-Introduction/01-Why-This-Project.md` | — | **Do not migrate** | Docusaurus sufficient |
| **Bootstrap deep-dives** | | | |
| `01-Bootstrap/01-Maven-Modules.md` | — | **Do not migrate** | Static reference; Docusaurus sufficient |
| `01-Bootstrap/02-Docker-Compose.md` | — | **Do not migrate** | Static reference; Docusaurus sufficient |
| `01-Bootstrap/03-JPA-Entities.md` | — | **Do not migrate** | Static reference; Docusaurus sufficient |
| `01-Bootstrap/04-API-Gateway.md` | — | **Do not migrate** | Static reference; Docusaurus sufficient |
| `01-Bootstrap/05-Tests.md` | — | **Do not migrate** | Static reference; Docusaurus sufficient |

---

## What Was Migrated

### Reference Section
- `Spec.md` — canonical module responsibilities, API design, code style, success criteria
- `System-Architecture.md` — full technical brief with FRs, flows, API table, NFRs
- `Learning-Notes.md` — interview positioning, protocol vs product distinction

### Phase Overviews
- All 9 phase `00-Overview.md` pages (Bootstrap through Demo Hardening)

---

## What Was NOT Migrated (and why)

**Interactive content** remains in Fumadocs studio pages (Tasks 34/35):
- PKCE flow explorer → `/studio/oauth-pkce`
- OIDC login explorer → `/studio/oidc-login`
- Callback inspector → `/studio/callback`
- API reference → `/studio/api-reference`

**Deep-dive protocol pages** stay in Docusaurus:
- These are covered by the interactive studio pages
- The narrative-only pages (e.g., Refresh Rotation, Login Flow) don't benefit from Next.js rendering

**Canonical reference pages** stay in Docusaurus:
- Demo script walkthrough — the script itself is the canonical form
- Architecture reference — System-Architecture.md covers it and was migrated

---

## Cross-link Plan

| From | To | Type |
|------|----|------|
| Fumadocs `index.mdx` | Migrated phase overviews | Internal links |
| Fumadocs `reference-links.mdx` | Migrated reference docs | Updated card links |
| Docusaurus `Reference/Spec.md` | Fumadocs `reference/spec.mdx` | Not added (Spec.md stays canonical in Docusaurus) |

---

## Verification

- [ ] `cd frontend/fumadocs && npm run build` succeeds
- [ ] All 12 migrated pages accessible via Fumadocs sidebar
- [ ] Docusaurus docs in `frontend/app/docs/` are untouched
