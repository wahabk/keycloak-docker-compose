# Keycloak auth plugin

## Dependencies

* JDK
* maven

## Extensions

All keycloak extensions should be placed in subdirectories of this directory,
e.g. `keycloak-isambard-auth-plugin`.

We also depend on https://github.com/p2-inc/keycloak-magic-link.git, 
so please "git clone https://github.com/p2-inc/keycloak-magic-link.git" 
into this directory as well

## Building the extensions

You can build each extension by changing into that extension's directory
and running `mvn clean install`. For example:

```
cd keycloak-isambard-auth-plugin
mvn clean install
```

This will place the JAR file into the `target` directory of the extension.

## Testing

In the directory above is the Dockerfile that builds the Keycloak image
used by Isambard. Simple change up a directory and run:

```
docker build -t keycloak:latest .
```

You can then run this image in test mode using

```
docker run -p 8080:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin keycloak:latest start-dev
```

Then navigate to `http://localhost:8080` and login with the admin credentials
(`admin`/`admin`).

## Deploying

Once it is working this is then the production image that you can deploy
to the AWS k8s cluster. Simply push this to the ECR using an appropriate
version number, and then update the helm chart to use the appropriate
version.
