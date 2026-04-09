---
title: Phase 5 — Admin UI
sidebar_position: 1
description: What Phase 5 builds — React Admin UI, login flow, and admin API.
---

# Phase 5 — Admin UI

## What Was Built

Phase 5 adds the React Admin UI and a login flow built on top of the OAuth 2.0 infrastructure from Phases 2–4.

## Architecture

```
Browser (Admin UI)
    │
    ├─ GET /login              → LoginPage (username/password form)
    ├─ POST /login              → LoginController (authenticate, issue login_token)
    ├─ GET /oauth2/authorize    → redirect with login_token (PKCE flow)
    ├─ GET /callback           → CallbackPage (exchange code → tokens)
    └─ GET /admin/*            → AdminController (Bearer token required)
            │
            ├─ /admin/clients   → OAuth client list
            ├─ /admin/audit     → Audit event log
            └─ /admin/users     → User list (placeholder for Phase 6)
```

## Modules Changed

| Module | What Changed |
|--------|--------------|
| `oauth-oidc` | `LoginController`, `AuthorizeController` (login_token), `AdminController` |
| `frontend/admin/` | React + Vite + MUI app (NEW) |

## What's New

**Login Flow:** Users authenticate via `POST /login`, receive a `login_token` (Redis, 10min TTL), and are redirected through the standard OAuth 2.0 PKCE flow with the `login_token` as the authenticated identity.

**Admin API:** Three endpoints (`/admin/clients`, `/admin/audit`, `/admin/users`) return data for the admin console. All require a valid Bearer token.

**React Admin UI:** Located in `frontend/admin/`. MUI dark theme, sidebar navigation, real data from the Admin API.

## Test Accounts

| Username | Password |
|----------|----------|
| `admin` | `admin123` |
| `user1` | `user1pass` |
| `alice` | `alicepass` |

## Start the Frontend

```bash
cd frontend/admin
npm install
npm run dev   # → http://localhost:5173
```

Backend must be running at `http://localhost:8080`.
