# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and uses a simple semantic versioning scheme for project milestones and documentation/site updates.

## [Unreleased]

### Planned
- Added Phase 10 to the implementation plan for a full learning site refresh toward an AgentWay-alike course UX.
- Planned migration of the Docusaurus learning site toward a premium track-based curriculum experience with sticky navigation, lesson progression, and responsive mobile drawer behavior.

## [1.1.0] - 2026-04-10

### Added
- Added `AGENTS.md` for Codex-specific repository guidance aligned with `CLAUDE.md`.
- Added Phase 10 planning to `IMPLEMENTATION_PLAN.md` for the learning site redesign.

### Changed
- Updated the learning site navigation and chapter wiring to surface missing navbar-linked learning sections.
- Synced agent guidance so `AGENTS.md` follows the same project rules and workflow expectations as `CLAUDE.md`.

## [1.0.0] - 2026-04-10

### Added
- Completed the IAM Protocol Engine portfolio demo across OAuth 2.0, OIDC, SAML 2.0, SCIM 2.0, WebAuthn, TOTP, and Device Authorization Grant.
- Added end-to-end demo automation with `scripts/demo-e2e.sh`.
- Added architecture documentation and Mermaid sequence diagrams in `README.md`.
- Added learning site chapters for Demo Hardening and prior protocol phases.

### Changed
- Marked all implementation phases through Phase 9 as complete in project planning and repository guidance.
- Finalized the learning site content structure for the completed protocol implementation phases.

## [0.9.0] - 2026-04-09

### Added
- Completed Demo Hardening deliverables: demo script and architecture-focused README updates.

### Changed
- Improved learning site documentation coverage and fixed MDX issues in Phase 9 content.

## [0.8.0] - 2026-04-08

### Added
- Completed Modern Auth support: TOTP MFA, WebAuthn registration/authentication, and Device Authorization Grant.
- Added Phase 8 code change summary documentation.

## [0.7.0] - 2026-04-07

### Added
- Completed SAML 2.0 service provider flow and SAML to OIDC bridge integration.
- Added SCIM joiner, mover, leaver lifecycle wiring to token revocation flows.

## [0.6.0] - 2026-04-06

### Added
- Completed SCIM 2.0 user and group management support.

## [0.5.0] - 2026-04-05

### Added
- Completed the Admin UI phase for client management, login flow, and audit-log-oriented workflows.

## [0.4.0] - 2026-04-04

### Added
- Completed token lifecycle features including refresh token rotation, introspection, and revocation.

## [0.3.0] - 2026-04-03

### Added
- Completed the OIDC layer including discovery, JWKS, ID token issuance, and `/userinfo`.

## [0.2.0] - 2026-04-02

### Added
- Completed OAuth 2.0 core flows including Authorization Code with PKCE and Client Credentials.

## [0.1.0] - 2026-04-01

### Added
- Bootstrapped the multi-module project structure, infrastructure, base entities, and API gateway entry point.
