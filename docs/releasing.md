# Releasing Kubert

Kubert releases are built once for `linux/amd64` and `linux/arm64`, then pushed
to four registries by `.github/workflows/release.yml`. The workflow also pushes
the Helm chart to GHCR and creates a GitHub Release containing the packaged
chart, image references, and SHA-256 checksums.

## Release environment

Create a GitHub Actions environment named `release`. Configure its deployment
policy to allow protected version tags and add approval protection when the
repository's operating model requires it. Cloud OIDC trust should be restricted
to this repository and environment; the resulting GitHub subject is:

```text
repo:ocularminds/kubert:environment:release
```

Add these environment variables:

| Variable | Example | Purpose |
| --- | --- | --- |
| `DOCKERHUB_NAMESPACE` | `ocularminds` | Public Docker Hub namespace |
| `GAR_REGISTRY` | `europe-west1-docker.pkg.dev` | Artifact Registry hostname |
| `GAR_PROJECT_ID` | `example-project` | Google Cloud project ID |
| `GAR_REPOSITORY` | `kubert` | Public Docker repository |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | `projects/123/locations/global/workloadIdentityPools/github/providers/kubert` | GitHub OIDC provider |
| `GCP_SERVICE_ACCOUNT` | `kubert-publisher@example-project.iam.gserviceaccount.com` | Artifact Registry writer |
| `ACR_LOGIN_SERVER` | `example.azurecr.io` | Public Azure registry hostname |

Add these environment secrets:

| Secret | Purpose |
| --- | --- |
| `DOCKERHUB_USERNAME` | Docker Hub publisher account |
| `DOCKERHUB_TOKEN` | Scoped Docker Hub access token with image read/write access |
| `AZURE_CLIENT_ID` | OIDC-enabled application or managed identity client ID |
| `AZURE_TENANT_ID` | Microsoft Entra tenant ID |
| `AZURE_SUBSCRIPTION_ID` | Subscription containing the target registry |

`GITHUB_TOKEN` publishes the GitHub image and Helm chart. No long-lived Google
or Azure credential is stored: GitHub exchanges its OIDC token for short-lived
cloud credentials during the release job. The workflow validates every setting
before authenticating or publishing and fails when any registry is omitted.

## Registry access

- Create `docker.io/<DOCKERHUB_NAMESPACE>/kubert` as a public Docker Hub
  repository and use an organization access token where available.
- Give the Google service account Artifact Registry Writer only on the selected
  repository. Grant Artifact Registry Reader to `allUsers` on that repository
  when unauthenticated pulls are required.
- Give the Azure identity `AcrPush` only on the selected registry. Anonymous
  pull requires a Standard or Premium registry and makes every repository in
  that registry public, so use a dedicated Kubert registry.
- Confirm the GHCR package visibility is public after its first publication.

## Publish a version

Update both `version` and `appVersion` in `charts/kubert/Chart.yaml`, merge the
change, and create a matching protected tag from `master`:

```shell
git tag -s v0.2.0 -m "Kubert 0.2.0"
git push origin v0.2.0
```

The tag triggers the release workflow. It publishes `0.2.0`, `0.2`, and
`latest` tags and creates the GitHub Release only after every registry and the
Helm chart have accepted their artifacts.

## Verify publication

Use the references in the release's `IMAGES.txt` asset. Each registry should
resolve the version tag to the manifest digest recorded in that file:

```shell
docker buildx imagetools inspect <registry-reference>:0.2.0
helm show chart oci://ghcr.io/ocularminds/charts/kubert --version 0.2.0
```

Finally, perform an unauthenticated pull from a clean Docker configuration to
verify that each intended public registry is actually public.
