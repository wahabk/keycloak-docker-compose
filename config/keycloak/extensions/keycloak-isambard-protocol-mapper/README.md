# Isambard Protocol Mapper

This is based on:

- Keycloak default mappers https://github.com/keycloak/keycloak/tree/main/services/src/main/java/org/keycloak/protocol/oidc/mappers
- OIDC Device Authorization Grant Spec https://datatracker.ietf.org/doc/html/rfc8628
- moh-external-api-protocol-mapper https://github.com/bcgov/moh-external-api-protocol-mapper?tab=readme-ov-file

## Build

The build instructions are identical to the other plugins, see the README.md in the parent directory.

## Adding the Protocol Mapper

1. Under the isambard realm go to `Client scopes > platforms > Mappers`
2. Click `Add mapper > By configuration > Isambard Protocol Mapper`
3. Add the information
   1. Name "Clifton Isambard Protocol Mapper"
   2. Waldur API URL 
   3. Waldur API Key
   4. Check ON to `Add to ID token, Add to access token, Add to userinfo`

## Verifying

1. Under the isambard realm go to `Clients > clifton > Client scopes`
2. Under the `Evaluate` tab check that `Clifton Isambard Protocol Mapper` is present
3. Optionally select a user from the seaarch bar and click on "Generated access token" on the right hand side