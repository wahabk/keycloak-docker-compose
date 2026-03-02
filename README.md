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

# Making Your Docker Network Reachable In MacOS

```shell
# Install via Homebrew
$ brew install chipmk/tap/docker-mac-net-connect

# Run the service and register it to launch at boot
$ sudo brew services start chipmk/tap/docker-mac-net-connect
```