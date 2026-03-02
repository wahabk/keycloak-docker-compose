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