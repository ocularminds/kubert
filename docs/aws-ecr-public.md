# Amazon ECR Public mirror

Blazra publishes to Amazon ECR Public only when all three AWS release variables
are configured. Authentication uses GitHub OIDC and temporary credentials; do
not create IAM access keys for the workflow.

Amazon ECR Public API and authentication operations use `us-east-1`, regardless
of where consumers run the image.

## 1. Create the public repository

Authenticate the AWS CLI to the account that will own the public image, then
create the repository:

```shell
aws ecr-public create-repository \
  --region us-east-1 \
  --repository-name blazra
```

Record the 12-digit AWS account ID and the active registry alias:

```shell
aws sts get-caller-identity --query Account --output text
aws ecr-public describe-registries \
  --region us-east-1 \
  --query 'registries[0].aliases[?primaryRegistryAlias].name | [0]' \
  --output text
```

The resulting image path is `public.ecr.aws/<alias>/blazra`.

## 2. Configure GitHub OIDC trust

Add `https://token.actions.githubusercontent.com` as an IAM OIDC provider with
audience `sts.amazonaws.com` if the account does not already have it.

Create an IAM role for the release workflow with this trust policy, replacing
`<account-id>`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<account-id>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": "repo:ocularminds/blazra:environment:release"
        }
      }
    }
  ]
}
```

Attach this least-privilege permissions policy, again replacing
`<account-id>`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "GetAuthorizationToken",
      "Effect": "Allow",
      "Action": [
        "ecr-public:GetAuthorizationToken",
        "sts:GetServiceBearerToken"
      ],
      "Resource": "*"
    },
    {
      "Sid": "PushBlazra",
      "Effect": "Allow",
      "Action": [
        "ecr-public:BatchCheckLayerAvailability",
        "ecr-public:CompleteLayerUpload",
        "ecr-public:InitiateLayerUpload",
        "ecr-public:PutImage",
        "ecr-public:UploadLayerPart"
      ],
      "Resource": "arn:aws:ecr-public::<account-id>:repository/blazra"
    }
  ]
}
```

The role needs no repository creation, deletion, or policy-management
permissions.

## 3. Configure the release environment

Set these variables on the existing GitHub `release` environment:

```shell
gh variable set AWS_ACCOUNT_ID \
  --repo ocularminds/blazra \
  --env release \
  --body '<account-id>'
gh variable set AWS_ROLE_TO_ASSUME \
  --repo ocularminds/blazra \
  --env release \
  --body 'arn:aws:iam::<account-id>:role/<role-name>'
gh variable set ECR_PUBLIC_REGISTRY_ALIAS \
  --repo ocularminds/blazra \
  --env release \
  --body '<registry-alias>'
```

No AWS repository secrets are required. Release validation rejects incomplete
AWS configuration, malformed account or role values, unexpected image paths,
and aliases outside the ECR Public naming rules.

## 4. Verify a release

After publishing, confirm anonymous access and both target platforms:

```shell
docker manifest inspect public.ecr.aws/<alias>/blazra:0.3.1
```

The ECR Public tag must resolve to the manifest digest recorded in the GitHub
Release's `IMAGES.txt` asset.

See the official AWS documentation for
[public registry aliases](https://docs.aws.amazon.com/AmazonECR/latest/public/public-registries.html),
[pushing public images](https://docs.aws.amazon.com/AmazonECR/latest/public/docker-push-ecr-image.html),
and [ECR pricing](https://aws.amazon.com/ecr/pricing/).
