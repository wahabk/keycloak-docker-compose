# Isambard Group Selector

Lets a user pick exactly one active Waldur group/project, reflected in the `groups`
claim (`BricsProjectGroupMapper`) and `projects` claim (`IsambardProtocolMapper`),
scoped per client application. Works even when the user already has an active
Keycloak SSO session — no re-authentication required. See
`../../RequiredAction.md` for the research behind this design.

## Build

The build instructions are identical to the other plugins, see the README.md in the
parent `extensions` directory.

## Enabling the Required Action

1. Under the isambard realm go to `Authentication > Required actions`.
2. Find `Select Active Group` and toggle it `Enabled`.
3. Leave `Set as default action` off — the action decides for itself (via
   `evaluateTriggers`) whether a user needs to pick a group, based on how many
   Waldur projects they have and whether they've already chosen one for the
   client they're logging into.
4. Click into the action's settings and set `Waldur API URL` and `Waldur API
   Key`. The required action calls Waldur itself (same as `IsambardAuthenticator`
   and `IsambardProtocolMapper`) rather than relying on the cached `projects`
   user attribute, since it runs *before* the protocol mapper refreshes that
   cache for this login — and before the authenticator too, when an existing
   SSO session is reused. Without these set, it falls back to the cache, which
   may be stale.

Enabling here is realm-wide — Keycloak has no per-client toggle for required
actions — so by itself this would prompt every client's users. To restrict it
to specific clients, see below.

## Restricting to specific clients

Under `Clients > <client> > Advanced > Attributes`, add:

```properties
group-selector.enabled = true
```

Only clients with this attribute set to `true` will ever trigger the picker
(via `evaluateTriggers`) or have their `groups`/`projects` claims filtered by
the mappers. Every other client keeps seeing the user's full project list,
unchanged — even though the required action is enabled realm-wide.

Note this only gates the *automatic* trigger. A client can still open the
picker on demand via `kc_action=select-active-group` (see below) regardless
of this attribute, since that's an explicit request from that client.

## "Switch active group" from an application

Because this required action supports Application Initiated Actions, a
downstream app can let the user change their active group at any time, without
logging out, by redirecting the browser to:

```text
https://keycloak.isambard.ac.uk/realms/isambard/protocol/openid-connect/auth
  ?client_id=<your-client-id>
  &redirect_uri=<your-redirect-uri>
  &response_type=code
  &scope=openid
  &kc_action=select-active-group
```

Keycloak reuses the existing SSO session, shows only the group picker, and
redirects back with `kc_action_status=success|cancelled|error`. Exchange the
resulting code as usual to get a token reflecting the newly selected group.

## Verifying

1. Under `Clients > <client> > Client scopes > Evaluate`, pick a user with
   more than one Waldur project and check the generated access token's
   `groups`/`projects` claims reflect only the selected one.
2. Confirm a user with a single project never sees the picker.
