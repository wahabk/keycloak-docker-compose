# Setting up AWS STS with local keycloak

# Admin set up 

1. Start localstack

```shell
docker run -d --name localstack \
    >   -p 4566:4566 \
    >   -e SERVICES=s3,sts,iam \
    >   localstack/localstack
```

2. Create an S3 bucket in localstack named datamover-s3-bucket-demo-1

```ssh
aws --endpoint-url=http://localhost:4566 s3 mb s3://datamover-s3-bucket-demo-1
aws --endpoint-url=http://localhost:4566 s3 cp example/Nyan_cat_250px_frame.PNG s3://datamover-s3-bucket-demo-1
```

1. Create `user` bucket named e.g. `akawafidev` and copy example file

```ssh
aws --endpoint-url=http://localhost:4566 s3 mb s3://akawafidev
aws --endpoint-url=http://localhost:4566 s3 cp example/Nyan_cat_250px_frame.PNG s3://akawafidev
```

4. Create client sts-s3-client in keycloak-dev with the following instructions:

```
A. Create the client
Go to your isambard realm → Clients → Create client
Client type: OpenID Connect
Client ID: sts-s3-client
Click Next

B. Capability config

Turn everything OFF
Turn OAuth 2.0 Device Authorization Grant ON
Click Next → Save

C. Create Keycloak Mapper

Go to Clients → sts-s3-client → Client scopes → sts-s3-client-dedicated
Click Configure a new mapper → User Attribute
Set:

Name: custom-sub
User Attribute: short_name (or whatever attribute holds the value you want)
Token Claim Name: sub
Claim JSON Type: String
Add to ID token: ON
Add to access token: ON
```

4. Add local keycloak IDP:

```shell
$ aws --endpoint-url=http://localhost:4566 iam create-open-id-connect-provider \
  --url http://keycloak:8080/realms/waldur \
  --client-id-list sts-s3-client \
  --thumbprint-list "0000000000000000000000000000000000000000"
```

5. Add trust role policy

```shell
$ aws --endpoint-url=http://localhost:4566 iam create-role \
  --role-name KeycloakS3ReadRole \
  --assume-role-policy-document file://example/trust-policy.json
{
    "Role": {
        "Path": "/",
        "RoleName": "KeycloakS3ReadRole",
        "RoleId": "AROAQAAAAAAAB2TF5Q6QB",
        "Arn": "arn:aws:iam::000000000000:role/KeycloakS3ReadRole",
        "CreateDate": "2026-03-02T15:42:20.816220+00:00",
        "AssumeRolePolicyDocument": {
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Effect": "Allow",
                    "Principal": {
                        "Federated": "arn:aws:iam::000000000000:oidc-provider/keycloak:8080/realms/waldur"
                    },
                    "Action": "sts:AssumeRoleWithWebIdentity",
                    "Condition": {
                        "StringEquals": {
                            "keycloak:8080/realms/waldur:aud": "sts-s3-client"
                        }
                    }
                }
            ]
        }
    }
}
```

6. Add read policy

```shell
$ aws --endpoint-url=http://localhost:4566 iam put-role-policy \
  --role-name KeycloakS3ReadRole \
  --policy-name S3ReadOnlyPerUser \
  --policy-document file://example/s3-read-policy.json
```

7. User flow

```shell
# Initiate login
curl -X POST "http://keycloak:8080/realms/waldur/protocol/openid-connect/auth/device" \
  -d "client_id=sts-s3-client"

# Poll for Token
curl -X POST "http://keycloak:8080/realms/waldur/protocol/openid-connect/token" \
  -d "grant_type=urn:ietf:params:oauth:grant-type:device_code" \
  -d "client_id=sts-s3-client" \
  -d "device_code=<FROM_ABOVE>"

# TIP: input access token in jwt.io to read it
export ACCESS_TOKEN="<FROM_ABOVE>"

# After authenticating, poll for token and save the id_token, then:
aws --endpoint-url=http://localhost:4566 sts assume-role-with-web-identity \
  --role-arn arn:aws:iam::000000000000:role/KeycloakS3ReadRole \
  --role-session-name test-session \
  --web-identity-token "${ACCESS_TOKEN}"

export AWS_ACCESS_KEY_ID=""
export AWS_SECRET_ACCESS_KEY=""
export AWS_SESSION_TOKEN=""

aws --endpoint-url=http://localhost:4566 s3 ls s3://akawafidev/
```