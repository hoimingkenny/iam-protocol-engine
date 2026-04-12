// @ts-nocheck
import * as __fd_glob_42 from "../content/docs/saml/sp-metadata.mdx?collection=docs"
import * as __fd_glob_41 from "../content/docs/saml/saml-oidc-bridge.mdx?collection=docs"
import * as __fd_glob_40 from "../content/docs/saml/overview.mdx?collection=docs"
import * as __fd_glob_39 from "../content/docs/saml/authn-request.mdx?collection=docs"
import * as __fd_glob_38 from "../content/docs/saml/acs.mdx?collection=docs"
import * as __fd_glob_37 from "../content/docs/scim/users.mdx?collection=docs"
import * as __fd_glob_36 from "../content/docs/scim/overview.mdx?collection=docs"
import * as __fd_glob_35 from "../content/docs/scim/groups.mdx?collection=docs"
import * as __fd_glob_34 from "../content/docs/token-lifecycle/revocation.mdx?collection=docs"
import * as __fd_glob_33 from "../content/docs/token-lifecycle/refresh-rotation.mdx?collection=docs"
import * as __fd_glob_32 from "../content/docs/token-lifecycle/overview.mdx?collection=docs"
import * as __fd_glob_31 from "../content/docs/token-lifecycle/introspection.mdx?collection=docs"
import * as __fd_glob_30 from "../content/docs/introduction/why-this-project.mdx?collection=docs"
import * as __fd_glob_29 from "../content/docs/oidc/userinfo.mdx?collection=docs"
import * as __fd_glob_28 from "../content/docs/oidc/overview.mdx?collection=docs"
import * as __fd_glob_27 from "../content/docs/oidc/jwks.mdx?collection=docs"
import * as __fd_glob_26 from "../content/docs/oidc/id-token.mdx?collection=docs"
import * as __fd_glob_25 from "../content/docs/oidc/discovery.mdx?collection=docs"
import * as __fd_glob_24 from "../content/docs/demo-hardening/overview.mdx?collection=docs"
import * as __fd_glob_23 from "../content/docs/demo-hardening/demo-script.mdx?collection=docs"
import * as __fd_glob_22 from "../content/docs/demo-hardening/architecture.mdx?collection=docs"
import * as __fd_glob_21 from "../content/docs/mfa/webauthn.mdx?collection=docs"
import * as __fd_glob_20 from "../content/docs/mfa/totp.mdx?collection=docs"
import * as __fd_glob_19 from "../content/docs/mfa/overview.mdx?collection=docs"
import * as __fd_glob_18 from "../content/docs/mfa/device-flow.mdx?collection=docs"
import * as __fd_glob_17 from "../content/docs/oauth2/token-endpoint.mdx?collection=docs"
import * as __fd_glob_16 from "../content/docs/oauth2/pkce.mdx?collection=docs"
import * as __fd_glob_15 from "../content/docs/oauth2/overview.mdx?collection=docs"
import * as __fd_glob_14 from "../content/docs/oauth2/demo-resource.mdx?collection=docs"
import * as __fd_glob_13 from "../content/docs/oauth2/client-credentials.mdx?collection=docs"
import * as __fd_glob_12 from "../content/docs/oauth2/authorize-endpoint.mdx?collection=docs"
import * as __fd_glob_11 from "../content/docs/bootstrap/tests.mdx?collection=docs"
import * as __fd_glob_10 from "../content/docs/bootstrap/overview.mdx?collection=docs"
import * as __fd_glob_9 from "../content/docs/bootstrap/maven-modules.mdx?collection=docs"
import * as __fd_glob_8 from "../content/docs/bootstrap/jpa-entities.mdx?collection=docs"
import * as __fd_glob_7 from "../content/docs/bootstrap/docker-compose.mdx?collection=docs"
import * as __fd_glob_6 from "../content/docs/bootstrap/api-gateway.mdx?collection=docs"
import * as __fd_glob_5 from "../content/docs/admin-ui/overview.mdx?collection=docs"
import * as __fd_glob_4 from "../content/docs/admin-ui/login-flow.mdx?collection=docs"
import * as __fd_glob_3 from "../content/docs/admin-ui/admin-api.mdx?collection=docs"
import * as __fd_glob_2 from "../content/docs/reference-links.mdx?collection=docs"
import * as __fd_glob_1 from "../content/docs/notes-and-experiments.mdx?collection=docs"
import * as __fd_glob_0 from "../content/docs/index.mdx?collection=docs"
import { server } from 'fumadocs-mdx/runtime/server';
import type * as Config from '../source.config';

const create = server<typeof Config, import("fumadocs-mdx/runtime/types").InternalTypeConfig & {
  DocData: {
  }
}>({"doc":{"passthroughs":["extractedReferences"]}});

export const docs = await create.docs("docs", "content/docs", {}, {"index.mdx": __fd_glob_0, "notes-and-experiments.mdx": __fd_glob_1, "reference-links.mdx": __fd_glob_2, "admin-ui/admin-api.mdx": __fd_glob_3, "admin-ui/login-flow.mdx": __fd_glob_4, "admin-ui/overview.mdx": __fd_glob_5, "bootstrap/api-gateway.mdx": __fd_glob_6, "bootstrap/docker-compose.mdx": __fd_glob_7, "bootstrap/jpa-entities.mdx": __fd_glob_8, "bootstrap/maven-modules.mdx": __fd_glob_9, "bootstrap/overview.mdx": __fd_glob_10, "bootstrap/tests.mdx": __fd_glob_11, "oauth2/authorize-endpoint.mdx": __fd_glob_12, "oauth2/client-credentials.mdx": __fd_glob_13, "oauth2/demo-resource.mdx": __fd_glob_14, "oauth2/overview.mdx": __fd_glob_15, "oauth2/pkce.mdx": __fd_glob_16, "oauth2/token-endpoint.mdx": __fd_glob_17, "mfa/device-flow.mdx": __fd_glob_18, "mfa/overview.mdx": __fd_glob_19, "mfa/totp.mdx": __fd_glob_20, "mfa/webauthn.mdx": __fd_glob_21, "demo-hardening/architecture.mdx": __fd_glob_22, "demo-hardening/demo-script.mdx": __fd_glob_23, "demo-hardening/overview.mdx": __fd_glob_24, "oidc/discovery.mdx": __fd_glob_25, "oidc/id-token.mdx": __fd_glob_26, "oidc/jwks.mdx": __fd_glob_27, "oidc/overview.mdx": __fd_glob_28, "oidc/userinfo.mdx": __fd_glob_29, "introduction/why-this-project.mdx": __fd_glob_30, "token-lifecycle/introspection.mdx": __fd_glob_31, "token-lifecycle/overview.mdx": __fd_glob_32, "token-lifecycle/refresh-rotation.mdx": __fd_glob_33, "token-lifecycle/revocation.mdx": __fd_glob_34, "scim/groups.mdx": __fd_glob_35, "scim/overview.mdx": __fd_glob_36, "scim/users.mdx": __fd_glob_37, "saml/acs.mdx": __fd_glob_38, "saml/authn-request.mdx": __fd_glob_39, "saml/overview.mdx": __fd_glob_40, "saml/saml-oidc-bridge.mdx": __fd_glob_41, "saml/sp-metadata.mdx": __fd_glob_42, });