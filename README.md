# Kubert

Kubert watches one container in a Kubernetes Deployment and updates its Docker
Hub image when a newer numeric tag is available. It is deliberately scoped to
one target so that its Kubernetes permissions and failure domain remain small.

Supported tags contain two to four numeric components, with an optional `v`
prefix—for example `1.4`, `v2.3.1`, or `3.1.0.12`. Digests, non-Docker Hub
registries, and non-numeric release tags are left unchanged.

## Design

The application follows dependency inversion and keeps infrastructure outside
the update policy:

- `model` owns validated, immutable domain values.
- `service` owns image selection and deployment update orchestration.
- `registry` adapts the Docker Hub API.
- `repository` adapts the Kubernetes API with optimistic concurrency.
- `runtime` schedules non-overlapping checks.
- `config` validates environment configuration before startup.

The Docker Hub client uses bounded responses, pagination, TLS, timeouts, and
same-origin pagination checks. The Kubernetes write verifies both the resource
version and current image immediately before updating the named container.

## Configuration

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `KUBERT_DEPLOYMENT` | Yes | — | Deployment name |
| `KUBERT_CONTAINER` | Yes | — | Container to update |
| `KUBERT_NAMESPACE` | No | `default` | Deployment namespace |
| `KUBERT_POLL_INTERVAL` | No | `PT5M` | ISO-8601 duration, from 10 seconds to 24 hours |
| `KUBERT_CONNECT_TIMEOUT` | No | `PT5S` | Docker Hub connection timeout |
| `KUBERT_REQUEST_TIMEOUT` | No | `PT15S` | Docker Hub request timeout |
| `KUBERT_DRY_RUN` | No | `false` | Report updates without writing them |
| `DOCKER_HUB_USERNAME` | No | — | Docker Hub account; configure with the token |
| `DOCKER_HUB_TOKEN` | No | — | Docker Hub access token; configure with the username |

Public repositories can be checked anonymously. Prefer a scoped access token
for authenticated requests; never use an account password.

## Development

Java 17 or newer is required. The checked-in Gradle wrapper verifies its own
download checksum and locks all resolved dependency versions.

```shell
./gradlew clean check
./gradlew installDist
```

Tests live under `src/test/java`, mirror the production packages, and include
unit, HTTP integration, scheduler, and Kubernetes API mock coverage. `check`
enforces at least 90% line coverage and 80% branch coverage. The HTML coverage
report is written to `build/reports/jacoco/test/html/index.html`.
