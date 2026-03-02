## `Error: Did not authenticate with any projects. `

Means the conch config doesnt match the projects presented by the JWT token.

## `Detected a split package usage`

```
2026-01-21 15:44:41,149 WARN  [io.quarkus.arc.deployment.SplitPackageProcessor] (build-25) Detected a split package usage which is considered a bad practice and should be avoided. Following packages were detected in multiple archives: 

- "uk.ac.isambard.keycloak.authentication.authenticators.browser" found in [/opt/keycloak/lib/../providers/keycloak-isambard-auth-plugin-0.1.jar, /opt/keycloak/lib/../providers/keycloak-tandc-auth-plugin-0.2.jar]
```