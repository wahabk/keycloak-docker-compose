# Keycloak docker compose

This repository provides a test environment for integrating the following:

- Keycloak
- [Clifton](https://github.com/isambard-sc/clifton)
- [Conch](https://github.com/isambard-sc/conch)

# docker compose

```shell
docker compose up -d
```

Then go to this [link](http://keycloak:8080/realms/waldur/account)

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