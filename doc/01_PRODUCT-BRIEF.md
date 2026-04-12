---
title: IAM Protocol Engine - Product Brief
tags:
  - project
  - iam
  - product
status: active
updated: 2026-04-05
---

# IAM Protocol Engine - Product Brief

> [!abstract] One-Line Pitch
> A portfolio-grade enterprise IAM platform demo that shows how modern authentication, federation, and provisioning actually work in real life.

---

## Snapshot

| Field | Value |
|------|-------|
| Outcome | A polished demo platform with OAuth/OIDC auth, SAML federation, SCIM provisioning, and an admin UI |
| Primary user | Enterprise IAM architects, hiring managers, and technical interviewers |
| Owner | Kenny |
| Status | active |
| Success metric | End-to-end flows work and can be explained clearly in a demo or interview |

---

## Problem

Most IAM portfolio projects prove configuration skill, not protocol understanding. A typical project shows Keycloak or another vendor product configured for login, but it does not show why the protocol behaves that way or what security decisions matter underneath.

This project fixes that gap by building the protocol surface directly and making the decisions visible.

---

## Why This Is Worth Building

- It creates a much stronger signal than a CRUD app with SSO attached
- It directly supports IAM architect and senior engineer interviews
- It turns protocol knowledge into something demonstrable, not just theoretical
- It connects closely to real enterprise work in banking, SaaS, and IGA environments

---

## Why Not Just Use Keycloak

> [!question] "Keycloak already does all of this. Why build it?"

This is the right question — and the answer is the entire point of the project.

| Dimension | Using Keycloak | Building This Project |
|-----------|---------------|----------------------|
| What you learn | Admin UI, realm config, client settings | `/authorize` logic, PKCE validation, token signing, SAML assertion handling |
| What you can explain | "I configured a client in Keycloak" | "I built the endpoint, here is why PKCE prevents auth code interception" |
| Protocol depth | Hidden behind the product | Fully visible and deliberately built |
| Interview signal | Configuration skill | Protocol and security decision-making |
| Debugging skill | Reading Keycloak logs | Understanding what went wrong at the RFC level |
| Differentiator | Every IAM candidate has done this | Very few candidates have done this |

Keycloak is a production-grade platform used in real enterprise environments. That is exactly why configuring it is table stakes — not a differentiator.

> [!important] The point is not to replace Keycloak
> The point is to understand what Keycloak is actually doing underneath. This project builds the protocol surface so the behaviour is no longer a black box.

Keycloak is also used in this project — as a backup local IdP for SAML testing when Entra ID is unavailable. That is the right use of it: a reference implementation and test peer, not a crutch that hides the work.

---

## What The Product Actually Is

This is a single demo platform with:

- user login and token issuance with `OAuth 2.0` and `OIDC`
- enterprise federation with `SAML 2.0`
- account provisioning with `SCIM 2.0`
- admin and audit views through a React UI
- a protected demo API to prove the tokens are usable

It is not trying to replace Okta, Entra ID, Ping, or SailPoint. It is trying to expose the protocol mechanics those platforms rely on.

---

## Real-World Use Cases

### OAuth 2.0

- web app to API authorization
- service-to-service API access
- CLI or device login through device flow

### OIDC

- workforce sign-in for modern web apps
- identity claims via ID token
- federation into apps that expect modern token-based login

### SAML

- workforce SSO into legacy enterprise applications
- federation between organizations
- enterprise IdP to app login where OIDC is not the upstream protocol

### SCIM

- joiner / mover / leaver automation
- user and group provisioning from IGA tools like SailPoint
- SaaS account lifecycle synchronization

### Combined Enterprise Flow

In a real enterprise:

1. `SCIM` provisions the account
2. `SAML` or `OIDC` signs the user in
3. `OAuth 2.0` issues access tokens for APIs
4. `WebAuthn` or `TOTP` adds MFA

---

## Users & Stakeholders

- Primary user: Enterprise IAM teams integrating authentication, federation, and provisioning
- Secondary user: Security architects reviewing protocol and integration design
- Stakeholders: Kenny, hiring managers, architects, compliance reviewers

---

## In Scope

- OAuth 2.0 Authorization Code + PKCE
- OIDC ID tokens, discovery, JWKS, and userinfo
- SAML 2.0 SP-initiated SSO and SAML-to-OIDC bridge
- SCIM 2.0 lifecycle APIs
- one admin console and one protected demo API
- demo-ready end-to-end flows

## Out Of Scope

- full commercial IAM replacement
- multi-tenant enterprise ops platform
- huge connector catalog
- broad social login support

---

## Product Success

This project succeeds if a reviewer can see:

- protocol depth
- enterprise realism
- security awareness
- implementation-level understanding

---

## Related

- [[Projects/Personal/IAM Protocol Engine]]
- [[Projects/Personal/IAM Protocol Engine/02. System Architecture]]
- [[Projects/Personal/IAM Protocol Engine/03. Learning & Interview Notes]]

