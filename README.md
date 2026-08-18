<p align="center">
  <img src="docs/kubert.svg" width="112" height="112" alt="Kubert logo">
</p>

# Kubert

[![CI](https://github.com/ocularminds/kubert/actions/workflows/ci.yml/badge.svg)](https://github.com/ocularminds/kubert/actions/workflows/ci.yml)
[![Coverage](https://codecov.io/gh/ocularminds/kubert/branch/master/graph/badge.svg)](https://codecov.io/gh/ocularminds/kubert)
[![Apache 2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Kubernetes 1.29+](https://img.shields.io/badge/Kubernetes-1.29%2B-326CE5?logo=kubernetes&logoColor=white)](https://kubernetes.io/docs/concepts/workloads/pods/sidecar-containers/)
[![Java 17](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net/)

Kubert watches one container in a Kubernetes Deployment and updates its Docker
Hub image when a newer numeric tag is available. The Helm chart runs Kubert as
a Kubernetes-native sidecar beside that workload and grants access to exactly
one Deployment.

Supported tags contain two to four numeric components, with an optional `v`
prefix—for example `1.4`, `v2.3.1`, or `3.1.0.12`. Digests, non-Docker Hub
registries, and non-numeric release tags are deliberately left unchanged. The
default `PATCH` policy keeps the first two numeric components fixed; `MINOR`
keeps the first component fixed, while `MAJOR` permits any newer numeric track.
Updates also preserve whether the current tag uses a `v` prefix.

## Published artifacts

Each release publishes one `linux/amd64` and `linux/arm64` image manifest to
GitHub Container Registry and `docker.io/speedoo/kubert`. Google Artifact
Registry and Azure Container Registry are included when their OIDC settings
are configured. Version, major/minor, and `latest` tags all point to the same
release build. The Helm chart remains available from the GHCR OCI chart
repository.

Exact image references and their shared manifest digest are attached to each
GitHub Release as `IMAGES.txt`. See [the release guide](docs/releasing.md) for
registry configuration, authentication, and verification.

## Install with Helm

The chart requires Kubernetes 1.29 or newer because it uses the stable native
sidecar model: an init container with `restartPolicy: Always`. Helm 3.8+ or
Helm 4 is required for an OCI install.

```shell
helm install demo oci://ghcr.io/ocularminds/charts/kubert \
  --version 0.2.0 \
  --namespace apps \
  --create-namespace \
  --set workload.image.repository=nginx \
  --set workload.image.tag=1.30.4 \
  --set workload.containerName=app
```

For local chart development:

```shell
helm install demo ./charts/kubert --namespace apps --create-namespace
```

The chart is intentionally limited to one workload replica. A sidecar runs in
every Pod, so additional replicas would duplicate Docker Hub and Kubernetes API
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
kubert:
  registryCredentials:
    existingSecret: docker-hub
    usernameKey: username
    tokenKey: token
```

Use a scoped Docker Hub access token, never an account password. The chart does
not accept secret values directly, so they cannot be persisted in Helm release
values.

## Important values

| Value | Default | Purpose |
| --- | --- | --- |
| `workload.image.repository` | `nginx` | Docker Hub workload repository |
| `workload.image.tag` | `1.30.4` | Initial numeric workload tag |
| `workload.image.digest` | empty | Immutable workload digest; disables updates while set |
| `workload.containerName` | `app` | Container Kubert is allowed to update |
| `kubert.image.repository` | `ghcr.io/ocularminds/kubert` | Kubert sidecar image |
| `kubert.pollInterval` | `PT5M` | Check interval, from 10 seconds to 24 hours |
| `kubert.updatePolicy` | `PATCH` | Allow `PATCH`, `MINOR`, or `MAJOR` version scope |
| `kubert.dryRun` | `false` | Report changes without applying them |
| `kubert.registryCredentials.existingSecret` | empty | Optional Docker Hub Secret |
| `rbac.create` | `true` | Create the least-privilege Role and binding |
| `service.enabled` | `true` | Expose the primary workload with a Service |

See [`values.yaml`](charts/kubert/values.yaml) for the complete configuration.

## Security model

- RBAC permits only `get`, `patch`, and `update` on the release's exact
  Deployment name.
- A short-lived projected service-account token is mounted only into Kubert;
  the primary workload cannot read it.
- The sidecar runs as UID/GID 65532 with no capabilities, no privilege
  escalation, a read-only root filesystem, and the runtime-default seccomp
  profile.
- The runtime image is shell-free and distroless. Build and runtime bases are
  pinned by digest, while release images include provenance and an SBOM.
- Docker Hub responses, pagination, timeouts, and origin are bounded and
  validated. Error messages do not include upstream bodies or credentials.
- Kubernetes writes use the resource version and re-check the current image to
  avoid overwriting concurrent changes.

## Application configuration

The Helm chart maps these environment settings. They are also available when
running the image directly.

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `KUBERT_DEPLOYMENT` | Yes | — | Deployment name |
| `KUBERT_CONTAINER` | Yes | — | Container to update |
| `KUBERT_NAMESPACE` | No | `default` | Deployment namespace |
| `KUBERT_POLL_INTERVAL` | No | `PT5M` | ISO-8601 duration, 10 seconds to 24 hours |
| `KUBERT_UPDATE_POLICY` | No | `PATCH` | Maximum allowed version scope: `PATCH`, `MINOR`, or `MAJOR` |
| `KUBERT_CONNECT_TIMEOUT` | No | `PT5S` | Docker Hub connection timeout |
| `KUBERT_REQUEST_TIMEOUT` | No | `PT15S` | Docker Hub request timeout |
| `KUBERT_DRY_RUN` | No | `false` | Report updates without writing them |
| `DOCKER_HUB_USERNAME` | No | — | Configure together with the token |
| `DOCKER_HUB_TOKEN` | No | — | Scoped Docker Hub access token |

## Design

The application follows dependency inversion and keeps infrastructure outside
the update policy:

- `model` owns validated, immutable domain values.
- `service` owns image selection and update orchestration.
- `registry` adapts the Docker Hub API.
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
charts/kubert/tests/render.sh
docker build --tag kubert:local .
```

Tests are separated under `src/test/java` and mirror production packages. They
cover domain validation, update policy, HTTP boundaries, scheduling, and a mock
Kubernetes API. `check` enforces at least 90% line and 80% branch coverage; the
HTML report is written to `build/reports/jacoco/test/html/index.html`.

The Apache License 2.0 applies; see [LICENSE](LICENSE).
