<p align="center">
  <img src="docs/blazra.svg" width="112" height="112" alt="Blazra logo">
</p>

# Blazra

[![CI](https://github.com/ocularminds/blazra/actions/workflows/ci.yml/badge.svg)](https://github.com/ocularminds/blazra/actions/workflows/ci.yml)
[![Coverage](https://codecov.io/gh/ocularminds/blazra/branch/master/graph/badge.svg)](https://codecov.io/gh/ocularminds/blazra)
[![Docker Pulls](https://img.shields.io/docker/pulls/speedoo/blazra?logo=docker&logoColor=white)](https://hub.docker.com/r/speedoo/blazra)
[![Apache 2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Kubernetes 1.29+](https://img.shields.io/badge/Kubernetes-1.29%2B-326CE5?logo=kubernetes&logoColor=white)](https://kubernetes.io/docs/concepts/workloads/pods/sidecar-containers/)
[![Java 17](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net/)

Blazra watches one container in a Kubernetes Deployment and updates its image
when a newer numeric tag is available. It supports Docker Hub plus public and
private OCI Distribution repositories, including GHCR, GAR, ACR, and ECR
Public. The Helm chart runs Blazra as a Kubernetes-native sidecar beside that
workload and grants access to exactly one Deployment.

Supported tags contain two to four numeric components, with an optional `v`
prefix—for example `1.4`, `v2.3.1`, or `3.1.0.12`. Digests and non-numeric
release tags are deliberately left unchanged. The default `PATCH` policy keeps
the first two numeric components fixed; `MINOR` keeps the first component
fixed, while `MAJOR` permits any newer numeric track. Updates also preserve
whether the current tag uses a `v` prefix.

## Published artifacts

Each release publishes one `linux/amd64` and `linux/arm64` image manifest to
GitHub Container Registry and `docker.io/speedoo/blazra`. Google Artifact
Registry, Azure Container Registry, and Amazon ECR Public are included when
their OIDC settings are configured. Version, major/minor, and `latest` tags all
point to the same release build. The Helm chart remains available from the GHCR
OCI chart repository. During the rename transition, the same image is also
published to `ghcr.io/ocularminds/kubert` and `docker.io/speedoo/kubert`.

Exact image references and their shared manifest digest are attached to each
GitHub Release as `IMAGES.txt`. See [the release guide](docs/releasing.md) for
registry configuration, authentication, and verification.

### Migrating from Kubert

Blazra 0.3.0 is the first release under the new name. Existing Kubert image
references remain supported as compatibility aliases, but Helm users should
move to the `blazra` chart and replace the `kubert.*` values namespace with
`blazra.*`. The deprecated `KUBERT_*` environment variables remain available
for direct-image users during the transition.

## Install with Helm

The chart requires Kubernetes 1.29 or newer because it uses the stable native
sidecar model: an init container with `restartPolicy: Always`. Helm 3.8+ or
Helm 4 is required for an OCI install.

```shell
helm install demo oci://ghcr.io/ocularminds/charts/blazra \
  --version 0.3.1 \
  --namespace apps \
  --create-namespace \
  --set workload.image.repository=nginx \
  --set workload.image.tag=1.30.4 \
  --set workload.containerName=app
```

For local chart development:

```shell
helm install demo ./charts/blazra --namespace apps --create-namespace
```

The chart is intentionally limited to one workload replica. A sidecar runs in
every Pod, so additional replicas would duplicate registry and Kubernetes API
traffic. The values schema rejects replica counts other than one.

### Existing workloads

This chart creates and owns both the Deployment and the primary workload
container. It does not patch or adopt an existing Deployment: doing so would
leave injected state outside Helm's lifecycle and make upgrades and rollbacks
unsafe. To migrate an existing single-replica workload, express its image,
command, arguments, ports, environment, resources, and security context through
the `workload` values before installing the chart under a new release name.

### Private Docker Hub repositories

Public repositories need no credentials. For a private repository, provision a
Secret through your secret manager with `username` and `token` keys, then set:

```yaml
blazra:
  registryCredentials:
    existingSecret: docker-hub
    usernameKey: username
    tokenKey: token
```

Use a scoped Docker Hub access token, never an account password. The chart does
not accept secret values directly, so they cannot be persisted in Helm release
values.

### OCI registries

Use a fully qualified repository for public GHCR, Google Artifact Registry,
Azure Container Registry, Amazon ECR Public, or another OCI Distribution
registry. For example:

```yaml
workload:
  image:
    repository: public.ecr.aws/example/team/app
    tag: 1.2.3
```

Anonymous registries may issue a short-lived bearer token challenge. Blazra
accepts that flow only when the token service uses the registry's own HTTPS
origin. Registry names must be externally reachable, fully qualified DNS names;
literal IP, localhost, `.local`, and `.internal` destinations are rejected.

For a private OCI repository, create a standard Kubernetes Docker config
Secret. Use a read-only account or token with pull access only:

```shell
kubectl create secret docker-registry private-registry \
  --namespace apps \
  --docker-server=ghcr.io \
  --docker-username=YOUR_ACCOUNT \
  --docker-password=YOUR_READ_ONLY_TOKEN
```

Reference the same Secret for the kubelet and the Blazra sidecar:

```yaml
imagePullSecrets:
  - name: private-registry

blazra:
  ociRegistryCredentials:
    existingSecret: private-registry
```

The `.dockerconfigjson` file is mounted read-only into Blazra and never into the
primary workload. Blazra looks up credentials by exact registry host and sends
them only after that registry returns a Bearer or Basic authentication
challenge. It rereads the projected file for each new challenge, allowing
Kubernetes Secret rotation without rebuilding the image. Docker credential
helpers are not executed; the Secret must contain a static `auth` entry.
Docker Hub credentials remain isolated to the dedicated Docker Hub client.

## Important values

| Value | Default | Purpose |
| --- | --- | --- |
| `workload.image.repository` | `nginx` | Docker Hub or fully qualified public OCI repository |
| `workload.image.tag` | `1.30.4` | Initial numeric workload tag |
| `workload.image.digest` | empty | Immutable workload digest; disables updates while set |
| `workload.containerName` | `app` | Container Blazra is allowed to update |
| `blazra.image.repository` | `ghcr.io/ocularminds/blazra` | Blazra sidecar image |
| `blazra.pollInterval` | `PT5M` | Check interval, from 10 seconds to 24 hours |
| `blazra.updatePolicy` | `PATCH` | Allow `PATCH`, `MINOR`, or `MAJOR` version scope |
| `blazra.dryRun` | `false` | Report changes without applying them |
| `blazra.registryCredentials.existingSecret` | empty | Optional Docker Hub Secret |
| `blazra.ociRegistryCredentials.existingSecret` | empty | Optional Docker config Secret for private OCI repositories |
| `rbac.create` | `true` | Create the least-privilege Role and binding |
| `service.enabled` | `true` | Expose the primary workload with a Service |

See [`values.yaml`](charts/blazra/values.yaml) for the complete configuration.

## Security model

- RBAC permits only `get`, `patch`, and `update` on the release's exact
  Deployment name.
- A short-lived projected service-account token is mounted only into Blazra;
  the primary workload cannot read it.
- The sidecar runs as UID/GID 65532 with no capabilities, no privilege
  escalation, a read-only root filesystem, and the runtime-default seccomp
  profile.
- The runtime image is shell-free and distroless. Build and runtime bases are
  pinned by digest, while release images include provenance and an SBOM.
- Registry responses, pagination, timeouts, bearer tokens, and origins are
  bounded and validated. Error messages do not include upstream bodies or
  credentials, and Docker Hub credentials are never routed to another host.
- Private OCI credentials are resolved by exact host, mounted only into Blazra,
  and sent only in response to an HTTPS authentication challenge.
- Kubernetes writes use the resource version and re-check the current image to
  avoid overwriting concurrent changes.
- CodeQL scans Java and GitHub Actions on every change and weekly. Dependency
  review rejects newly introduced vulnerabilities of moderate severity or
  higher, while Dependabot groups routine dependency updates by ecosystem.

## Application configuration

The Helm chart maps these environment settings. They are also available when
running the image directly.

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `BLAZRA_DEPLOYMENT` | Yes | — | Deployment name |
| `BLAZRA_CONTAINER` | Yes | — | Container to update |
| `BLAZRA_NAMESPACE` | No | `default` | Deployment namespace |
| `BLAZRA_POLL_INTERVAL` | No | `PT5M` | ISO-8601 duration, 10 seconds to 24 hours |
| `BLAZRA_UPDATE_POLICY` | No | `PATCH` | Maximum allowed version scope: `PATCH`, `MINOR`, or `MAJOR` |
| `BLAZRA_CONNECT_TIMEOUT` | No | `PT5S` | Registry connection timeout |
| `BLAZRA_REQUEST_TIMEOUT` | No | `PT15S` | Registry request timeout |
| `BLAZRA_DRY_RUN` | No | `false` | Report updates without writing them |
| `BLAZRA_OCI_REGISTRY_CONFIG_PATH` | No | — | Absolute path to a Docker `config.json` credential file |
| `DOCKER_HUB_USERNAME` | No | — | Configure together with the token |
| `DOCKER_HUB_TOKEN` | No | — | Scoped Docker Hub access token |

The equivalent `KUBERT_*` names remain deprecated aliases for direct-image
users. Do not set both names for the same option; conflicting values are
rejected during startup.

## Design

The application follows dependency inversion and keeps infrastructure outside
the update policy:

- `model` owns validated, immutable domain values.
- `service` owns image selection and update orchestration.
- `registry` routes Docker Hub to its dedicated adapter and other images to a
  bounded OCI Distribution adapter with a host-scoped credential provider.
- `repository` adapts Kubernetes with optimistic concurrency.
- `runtime` schedules non-overlapping checks.
- `config` validates all input before startup.

Each class has one reason to change, and every repository file is kept below
500 lines.

## Development and verification

Java 17 or newer is required. The Gradle wrapper verifies its download
checksum, dependencies are locked, and CI pins third-party Actions by commit.

```shell
./gradlew clean check installDist
charts/blazra/tests/render.sh
docker build --tag blazra:local .
```

Tests are separated under `src/test/java` and mirror production packages. They
cover domain validation, update policy, HTTP boundaries, scheduling, and a mock
Kubernetes API. `check` enforces at least 90% line and 80% branch coverage; the
HTML report is written to `build/reports/jacoco/test/html/index.html`.

The Apache License 2.0 applies; see [LICENSE](LICENSE).
