---
title: Learning & Interview Notes
sidebar_position: 2
description: What each protocol is for, where it appears in real life, and what interview answers this project unlocks.
---

# Learning & Interview Notes

> This note turns the project into learning leverage: what each protocol is for, where it appears in real life, and what interview answers this project unlocks.

## What You Are Actually Learning

- How OAuth 2.0 works at the endpoint and security-control level
- How OIDC adds identity on top of OAuth
- How SAML federation works in enterprise reality
- How SCIM provisioning works at the HTTP and lifecycle level
- How MFA and passwordless flows plug into the login journey
- How these protocols combine in one enterprise architecture

---

## Protocol vs Product

> Entra ID is not a protocol. It is a product and identity platform.

Use this distinction:

- **Protocols / standards:** `OAuth 2.0`, `OIDC`, `SAML`, `SCIM`, `LDAP`
- **Products / platforms:** `Entra ID`, `Okta`, `Ping`, `Keycloak`, `SailPoint`

Interviews often expect you to distinguish the standard from the vendor platform.

---

## Why These Four Always Appear Together

`OAuth 2.0`, `OIDC`, `SAML 2.0`, and `SCIM 2.0` usually appear together because they cover the full enterprise identity flow:

- `SCIM` provisions and updates the account
- `SAML` or `OIDC` signs the user in through federation or SSO
- `OIDC` gives the application identity claims about the user
- `OAuth 2.0` gives access tokens for protected APIs

---

## Interview Moments This Unlocks

**"Explain PKCE and why it matters"**
You can explain `code_verifier`, S256 hashing, and auth code interception risk.

**"What is the difference between an access token and an ID token?"**
You can answer in terms of audience, claims, and validation path.

**"How does federation work between two organisations?"**
You can walk through SAML AuthnRequest, signed assertions, condition checks, and claim mapping.

**"How does SailPoint provision a user to a target application?"**
You can explain SCIM CRUD, `PATCH`, filtering, activation, and deprovisioning.

**"Is Entra ID a protocol?"**
You can clearly explain that Entra ID is a platform that uses protocols such as OIDC, OAuth 2.0, and SAML.

**"How would you secure machine-to-machine access?"**
You can explain Client Credentials, scopes, token validation, and revocation strategy.

**"How does passwordless fit into enterprise identity?"**
You can explain WebAuthn registration, assertion verification, and step-up authentication.

---

## CV / Portfolio Positioning

> Built a portfolio-grade enterprise IAM platform demo in Java / Spring Boot with a React admin console, implementing OAuth 2.0, OIDC, SAML 2.0 federation, SCIM 2.0 provisioning, and modern MFA flows.

### What This Signals

- Protocol depth
- Enterprise architecture thinking
- Security awareness
- Practical understanding of identity lifecycle
- Ability to bridge legacy and modern IAM patterns

---

## How To Talk About This Project

### Short Version

I built a custom IAM protocol engine so I could understand OAuth, OIDC, SAML, SCIM, and LDAP-backed enterprise identity patterns at the protocol and integration level rather than only through vendor configuration.

### Stronger Architect Version

I built a modular IAM platform that issues OIDC tokens, validates SAML assertions, exposes SCIM lifecycle APIs, integrates with LDAP-style directory context, and supports MFA and device flows, so I can explain not just how IAM products are configured, but how their protocol behavior actually works under the hood.
