# Releasing Blazra

Blazra releases are built once for `linux/amd64` and `linux/arm64`, then pushed
to GHCR and `docker.io/speedoo/blazra` by `.github/workflows/release.yml`. Google
Artifact Registry and Azure Container Registry are optional mirrors. The
workflow also pushes the Helm chart to GHCR and creates a GitHub Release with
the packaged chart, image references, and SHA-256 checksums. The same manifest
is pushed to the legacy Kubert GHCR and Docker Hub paths during migration.

## Release environment

Create a GitHub Actions environment named `release`. Configure its deployment
policy to allow protected version tags and add approval protection when the
repository's operating model requires it. Cloud OIDC trust should be restricted
to this repository and environment; the resulting GitHub subject is:

```text
repo:ocularminds/blazra:environment:release
```

Add these optional environment variables to enable the cloud mirrors:

| Variable | Example | Purpose |
| --- | --- | --- |
| `GAR_REGISTRY` | `europe-west1-docker.pkg.dev` | Artifact Registry hostname |
| `GAR_PROJECT_ID` | `example-project` | Google Cloud project ID |
| `GAR_REPOSITORY` | `blazra` | Public Docker repository |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | `projects/123/locations/global/workloadIdentityPools/github/providers/blazra` | GitHub OIDC provider |
| `GCP_SERVICE_ACCOUNT` | `blazra-publisher@example-project.iam.gserviceaccount.com` | Artifact Registry writer |
| `ACR_LOGIN_SERVER` | `example.azurecr.io` | Public Azure registry hostname |

Add the Docker Hub values as repository or `release` environment secrets. Add
the Azure values only when enabling its mirror:

| Secret | Purpose |
| --- | --- |
| `DOCKERHUB_USER` | Docker Hub publisher account |
| `DOCKERHUB_TOKEN` | Scoped Docker Hub access token with image read/write access |
| `AZURE_CLIENT_ID` | OIDC-enabled application or managed identity client ID |
| `AZURE_TENANT_ID` | Microsoft Entra tenant ID |
| `AZURE_SUBSCRIPTION_ID` | Subscription containing the target registry |

`GITHUB_TOKEN` publishes the GitHub image and Helm chart. No long-lived Google
or Azure credential is stored: GitHub exchanges its OIDC token for short-lived
cloud credentials during the release job. The workflow requires Docker Hub and
validates each optional cloud group as an all-or-nothing configuration before
authenticating or publishing.

## Registry access

- Rename the GitHub repository to `ocularminds/blazra` before creating the
  release tag. Update Google and Azure federated credentials to allow the new
  `repo:ocularminds/blazra:environment:release` subject.
- Create `docker.io/speedoo/blazra` as a public Docker Hub
  repository and use an organization access token where available. Keep
  `docker.io/speedoo/kubert` writable while compatibility tags are published.
- Give the Google service account Artifact Registry Writer only on the selected
  repository. Grant Artifact Registry Reader to `allUsers` on that repository
  when unauthenticated pulls are required.
- Give the Azure identity `AcrPush` only on the selected registry. Anonymous
  pull requires a Standard or Premium registry and makes every repository in
  that registry public, so use a dedicated Blazra registry.
- Confirm the GHCR package visibility is public after its first publication.
  Keep `ghcr.io/ocularminds/kubert` public as the compatibility alias.

## Publish a version

Update `version` in `build.gradle` plus `version` and `appVersion` in
`charts/blazra/Chart.yaml`, merge the change, and create a matching protected
tag from `master`:

```shell
git tag -s v0.3.0 -m "Blazra 0.3.0"
git push origin v0.3.0
```

The tag triggers the release workflow. It publishes `0.3.0`, `0.3`, and
`latest` tags and creates the GitHub Release only after every configured
registry and the Helm chart have accepted their artifacts.

If a registry-side problem interrupts publication after the tag is created,
fix the external configuration and dispatch the reviewed workflow from
`master` without deleting or moving the tag:

```shell
gh workflow run Release --ref master -f release_tag=v0.3.0
```

The recovery path checks out the existing tag, verifies that `HEAD` resolves to
its commit, and derives both image version and revision from that immutable
source.

## Verify publication

Use the references in the release's `IMAGES.txt` asset. Each registry should
resolve the version tag to the manifest digest recorded in that file. Verify
both canonical and compatibility paths:

```shell
docker buildx imagetools inspect ghcr.io/ocularminds/blazra:0.3.0
docker buildx imagetools inspect docker.io/speedoo/blazra:0.3.0
docker buildx imagetools inspect ghcr.io/ocularminds/kubert:0.3.0
docker buildx imagetools inspect docker.io/speedoo/kubert:0.3.0
helm show chart oci://ghcr.io/ocularminds/charts/blazra --version 0.3.0
```

Finally, perform an unauthenticated pull from a clean Docker configuration to
verify that each intended public registry is actually public.
