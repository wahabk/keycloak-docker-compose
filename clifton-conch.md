
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
