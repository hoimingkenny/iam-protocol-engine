# Docstash

Interactive docs workspace for the IAM Protocol Engine, built with Next.js and Fumadocs.

This app sits alongside the existing Docusaurus site in `frontend/app`. It does not replace it.

## Purpose

Use this app for:

- OAuth 2.0 PKCE flow exploration
- OIDC login and token inspection
- callback debugging
- selected OpenAPI-backed endpoint reference
- light and dark mode study sessions

Use the Docusaurus site for the broader written documentation and long-form walkthroughs.

## Requirements

- Node.js `>=22`
- npm
- IAM backend running locally at `http://localhost:8080` unless overridden

## Local Development

```bash
cd frontend/fumadocs
npm install
npm run dev
```

Open `http://localhost:3000`.

## Important Local Assumptions

- Default backend base URL: `http://localhost:8080`
- Default client ID: `test-client`
- Default OIDC scope: `openid profile email`
- Default test subject: `user1`

To receive the auth code on the local callback page, register:

```txt
http://localhost:3000/studio/callback
```

on the client you use for the flow.

## Build

```bash
npm run build
```
