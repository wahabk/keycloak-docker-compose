# Keycloak docker compose

This repository provides a test environment for integrating the following:

- Keycloak
- [Clifton](https://github.com/isambard-sc/clifton)
- [Conch](https://github.com/isambard-sc/conch)

# docker compose

```shell
docker compose up -d
```

> [!IMPORTANT]
> You must alias `hostname` to `keycloak` on your local machine so that your browser sees the same URI as the containers.

Then go to this link: http://keycloak:8080/realms/waldur/account)

# Set up conch SSH signing key and clifton auth:

Generate SSH signing key for conch:

```
cd config/conch/
ssh-keygen -q -t ed25519 -f ssh_signing_key -C '' -N ''
```

Configure keycloak:

1. Add `projects` and `short_name` to User profile in Realm Settings
2. Add `clifton` as client
3. Add `platforms` client scope
   1. Add `projects` and `short_name` as User Attribute mappers to `platforms`

Then sign in with clifton using:

```
$ clifton auth --config-file=config/clifton/config.toml
```

### `Error: Did not authenticate with any projects. `

Means the conch config doesnt match the projects presented by the JWT token.

### `Detected a split package usage`

```
2026-01-21 15:44:41,149 WARN  [io.quarkus.arc.deployment.SplitPackageProcessor] (build-25) Detected a split package usage which is considered a bad practice and should be avoided. Following packages were detected in multiple archives: 

- "uk.ac.isambard.keycloak.authentication.authenticators.browser" found in [/opt/keycloak/lib/../providers/keycloak-isambard-auth-plugin-0.1.jar, /opt/keycloak/lib/../providers/keycloak-tandc-auth-plugin-0.2.jar]
```

# Isambard Protocol Mapper

This is based on:

- Keycloak default mappers https://github.com/keycloak/keycloak/tree/main/services/src/main/java/org/keycloak/protocol/oidc/mappers
- OIDC Device Authorization Grant Spec https://datatracker.ietf.org/doc/html/rfc8628
- moh-external-api-protocol-mapper https://github.com/bcgov/moh-external-api-protocol-mapper?tab=readme-ov-file

# Running with Podman

I have also generated a minimal podman kube play manifest for testing:

## Start keycloak

Launch https://www.keycloak.org/getting-started/getting-started-podman 

```shell
podman run -d -p 127.0.0.1:8080:8080 -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin quay.io/keycloak/keycloak:26.4.2 start-dev
```

You can then access at [this link](http://127.0.0.1:8080)

## Start keycloak with Postgres

```shell
podman kube play keycloak-stack.yaml
```

You can then access at [this link](http://127.0.0.1:8080)

## Check logs and Postgres

```shell
podman logs keycloak_server
podman logs keycloak_postgresql_db
podman exec -it keycloak-stack-postgresql psql -U user -d keycloak_db
keycloak_db-> \c keycloak_db
keycloak_db-> \dt
```

# Making Your Docker Network Reachable In MacOS

```shell
# Install via Homebrew
$ brew install chipmk/tap/docker-mac-net-connect

# Run the service and register it to launch at boot
$ sudo brew services start chipmk/tap/docker-mac-net-connect
```