import { createOpenAPI } from 'fumadocs-openapi/server';
import { defaultBackendBaseUrl } from './shared';

const schema = {
  openapi: '3.2.0',
  info: {
    title: 'IAM Protocol Engine Core Auth API',
    version: '1.0.0',
    description:
      'Selected endpoints used by the Interactive Docs Studio for OAuth 2.0, OIDC discovery, and user identity inspection.',
  },
  servers: [
    {
      url: defaultBackendBaseUrl,
      description: 'Local Spring Boot backend',
    },
  ],
  paths: {
    '/oauth2/authorize': {
      get: {
        summary: 'Start Authorization Code + PKCE',
        description:
          'Validates client, redirect URI, and PKCE parameters, then redirects with an authorization code.',
        parameters: [
          {
            in: 'query',
            name: 'client_id',
            required: true,
            schema: { type: 'string' },
          },
          {
            in: 'query',
            name: 'redirect_uri',
            required: true,
            schema: { type: 'string', format: 'uri' },
          },
          {
            in: 'query',
            name: 'response_type',
            required: true,
            schema: { type: 'string', enum: ['code'] },
          },
          {
            in: 'query',
            name: 'scope',
            schema: { type: 'string' },
          },
          {
            in: 'query',
            name: 'state',
            schema: { type: 'string' },
          },
          {
            in: 'query',
            name: 'code_challenge',
            required: true,
            schema: { type: 'string' },
          },
          {
            in: 'query',
            name: 'code_challenge_method',
            required: true,
            schema: { type: 'string', enum: ['S256'] },
          },
          {
            in: 'query',
            name: 'subject',
            schema: { type: 'string' },
            description:
              'Phase 2 demo shortcut for a pre-authenticated user. Useful for local study flows.',
          },
          {
            in: 'query',
            name: 'nonce',
            schema: { type: 'string' },
            description: 'OIDC only.',
          },
        ],
        responses: {
          '302': {
            description: 'Redirects to the registered callback URL with code and state.',
          },
          '400': {
            description: 'Validation error such as redirect_uri mismatch or missing PKCE.',
          },
        },
      },
    },
    '/oauth2/token': {
      post: {
        summary: 'Exchange auth code for tokens',
        description:
          'Validates the authorization code, redirect URI, and PKCE verifier, then returns tokens.',
        requestBody: {
          required: true,
          content: {
            'application/x-www-form-urlencoded': {
              schema: {
                type: 'object',
                required: [
                  'grant_type',
                  'code',
                  'redirect_uri',
                  'client_id',
                  'code_verifier',
                ],
                properties: {
                  grant_type: {
                    type: 'string',
                    enum: ['authorization_code'],
                  },
                  code: { type: 'string' },
                  redirect_uri: { type: 'string', format: 'uri' },
                  client_id: { type: 'string' },
                  code_verifier: { type: 'string' },
                },
              },
            },
          },
        },
        responses: {
          '200': {
            description: 'Access token, and optionally refresh and ID tokens.',
            content: {
              'application/json': {
                schema: {
                  type: 'object',
                  properties: {
                    access_token: { type: 'string' },
                    token_type: { type: 'string', example: 'Bearer' },
                    expires_in: { type: 'integer' },
                    refresh_token: { type: 'string' },
                    id_token: { type: 'string' },
                    scope: { type: 'string' },
                  },
                },
              },
            },
          },
          '400': {
            description: 'Error response such as invalid_grant or invalid_client.',
          },
        },
      },
    },
    '/.well-known/openid-configuration': {
      get: {
        summary: 'OIDC discovery document',
        responses: {
          '200': {
            description: 'Issuer metadata, endpoint URLs, and supported capabilities.',
          },
        },
      },
    },
    '/.well-known/jwks.json': {
      get: {
        summary: 'JSON Web Key Set',
        responses: {
          '200': {
            description: 'Public signing keys used to validate JWT signatures.',
          },
        },
      },
    },
    '/userinfo': {
      get: {
        summary: 'OIDC UserInfo',
        security: [{ bearerAuth: [] }],
        responses: {
          '200': {
            description: 'User claims associated with the access token.',
          },
          '401': {
            description: 'Missing or invalid bearer token.',
          },
        },
      },
    },
  },
  components: {
    securitySchemes: {
      bearerAuth: {
        type: 'http',
        scheme: 'bearer',
      },
    },
  },
};

export const openApiServer = createOpenAPI({
  input: async () => ({
    iam: schema as any,
  }),
});
