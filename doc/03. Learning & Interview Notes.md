---
title: IAM Protocol Engine - Learning & Interview Notes
tags:
  - project
  - iam
  - learning
  - interview
  - ldap
status: active
updated: 2026-04-05
---

# IAM Protocol Engine - Learning & Interview Notes

> [!abstract] Why this matters
> This note turns the project into learning leverage: what each protocol is for, where it appears in real life, and what interview answers this project unlocks.

---

## What I Am Actually Learning

- how OAuth 2.0 works at the endpoint and security-control level
- how OIDC adds identity on top of OAuth
- how SAML federation works in enterprise reality
- how SCIM provisioning works at the HTTP and lifecycle level
- how LDAP still matters in enterprise directory and hybrid identity environments
- how identity products like Entra ID use these protocols in real enterprise deployments
- how MFA and passwordless flows plug into the login journey
- how these protocols combine in one enterprise architecture

---

## Protocol Vs Product

> [!important]
> `Entra ID` is not a protocol. It is a product and identity platform.

Use this distinction:

- **Protocols / standards:** `OAuth 2.0`, `OIDC`, `SAML`, `SCIM`, `LDAP`
- **Products / platforms:** `Entra ID`, `Okta`, `Ping`, `Keycloak`, `SailPoint`

Why this matters:

- protocols define how systems communicate
- products implement, expose, or orchestrate those protocols
- interviewers often expect you to distinguish the standard from the vendor platform

In this project, `Entra ID` should be treated as:

- a real-world IdP
- a federation target
- a demo integration platform
- evidence of enterprise realism

not as a protocol module

---

## Why These Four Always Appear Together

`OAuth 2.0`, `OIDC`, `SAML 2.0`, and `SCIM 2.0` usually appear together because they cover the full enterprise identity flow:

- `SCIM` provisions and updates the account
- `SAML` or `OIDC` signs the user in through federation or SSO
- `OIDC` gives the application identity claims about the user
- `OAuth 2.0` gives access tokens for protected APIs

In real life, IAM teams are usually responsible for all of these together, not one in isolation.

---

## Where These Protocols Show Up In Real Life

### OAuth 2.0

Use it when an app or service needs controlled API access.

Examples:
- SPA or web app calling backend APIs
- service-to-service integration
- CLI or device login through device flow
- API gateway validating token state

### OIDC

Use it when you need authentication plus identity claims.

Examples:
- modern workforce SSO
- "Sign in with Entra ID"
- ID tokens for web applications
- discovery and JWKS-based trust

### SAML

Use it when federation happens in enterprise-heavy or legacy environments.

Examples:
- employee SSO into enterprise SaaS
- bank and legal environments with older app estates
- partner or organization-to-organization federation
- SAML upstream, OIDC downstream bridge patterns

### SCIM

Use it when identity platforms provision users and groups into applications.

Examples:
- SailPoint provisioning joiners
- mover updates with SCIM `PATCH`
- leaver deactivation
- group synchronization

### LDAP

Use it when enterprise identity still depends on directory lookup, group membership, and legacy integration patterns.

Examples:
- Active Directory-backed user and group lookups
- application authorization based on directory groups
- hybrid identity environments where modern SSO still depends on directory context
- older enterprise applications that still integrate through LDAP rather than modern federation standards

### Combined Reality

In a typical enterprise:

1. `LDAP` or the enterprise directory provides user and group context
2. `SCIM` provisions the account into target systems
3. `SAML` or `OIDC` authenticates the user, often through a product like `Entra ID`
4. `OAuth 2.0` issues tokens for APIs
5. `WebAuthn` or `TOTP` strengthens login with MFA

---

## Why This Project Is Strong For Interviews

Most candidates can say they configured a vendor product. Far fewer can say they built:

- the `/authorize` endpoint
- PKCE validation
- token introspection and revocation
- SAML assertion validation
- SCIM lifecycle APIs
- LDAP directory integration and group-context lookup
- a SAML-to-OIDC bridge

That changes the interview from "which buttons did you click?" to "what security and protocol decisions did you make?"

---

## Interview Moments This Unlocks

- *"Explain PKCE and why it matters"*  
  You can explain `code_verifier`, S256 hashing, and auth code interception risk.

- *"What is the difference between an access token and an ID token?"*  
  You can answer in terms of audience, claims, and validation path.

- *"How does federation work between two organisations?"*  
  You can walk through SAML AuthnRequest, signed assertions, condition checks, and claim mapping.

- *"How does SailPoint provision a user to a target application?"*  
  You can explain SCIM CRUD, `PATCH`, filtering, activation, and deprovisioning.

- *"Where does LDAP still matter if we already have SAML and OIDC?"*  
  You can explain directory-backed identity stores, group lookup, legacy enterprise integration, and hybrid identity estates.

- *"Is Entra ID a protocol?"*  
  You can clearly explain that Entra ID is a platform that uses protocols such as OIDC, OAuth 2.0, and SAML.

- *"How would you secure machine-to-machine access?"*  
  You can explain Client Credentials, scopes, token validation, and revocation strategy.

- *"How does passwordless fit into enterprise identity?"*  
  You can explain WebAuthn registration, assertion verification, and step-up authentication.

---

## CV / Portfolio Positioning

### CV Bullet

> Built a portfolio-grade enterprise IAM platform demo in Java / Spring Boot with a React admin console, implementing OAuth 2.0, OIDC, SAML 2.0 federation, SCIM 2.0 provisioning, LDAP-integrated directory context, and modern MFA flows.

### What This Signals

- protocol depth
- enterprise architecture thinking
- security awareness
- practical understanding of identity lifecycle and enterprise directory context
- ability to bridge legacy and modern IAM patterns

---

## How To Talk About This Project

### Short Version

I built a custom IAM protocol engine so I could understand OAuth, OIDC, SAML, SCIM, and LDAP-backed enterprise identity patterns at the protocol and integration level rather than only through vendor configuration.

### Stronger Architect Version

I built a modular IAM platform that issues OIDC tokens, validates SAML assertions, exposes SCIM lifecycle APIs, integrates with LDAP-style directory context, and supports MFA and device flows, so I can explain not just how IAM products are configured, but how their protocol behavior actually works under the hood.

---

## Companion Project

> [!tip] Forge
> [[Forge - IAM Protocol Conformance Suite]] validates this project against the RFC surface it implements. Together they show both implementation depth and verification depth.

---

## Related

- [[Projects/Personal/IAM Protocol Engine]]
- [[Projects/Personal/IAM Protocol Engine/01. Product Brief]]
- [[Projects/Personal/IAM Protocol Engine/02. System Architecture]]
