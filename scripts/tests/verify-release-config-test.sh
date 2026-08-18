#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
validator="${project_root}/scripts/verify-release-config.sh"
release_workflow="${project_root}/.github/workflows/release.yml"

core_environment=(
  RELEASE_TAG=v0.3.0
  GITHUB_REPOSITORY=ocularminds/blazra
  GHCR_IMAGE=ghcr.io/ocularminds/blazra
  GHCR_LEGACY_IMAGE=ghcr.io/ocularminds/kubert
  DOCKERHUB_IMAGE=docker.io/speedoo/blazra
  DOCKERHUB_LEGACY_IMAGE=docker.io/speedoo/kubert
  DOCKERHUB_USER=speedoo
  DOCKERHUB_TOKEN=test-token
)
cloud_environment=(
  GAR_REGISTRY=europe-west1-docker.pkg.dev
  GAR_PROJECT_ID=ocularminds-blazra
  GAR_REPOSITORY=blazra
  GCP_WORKLOAD_IDENTITY_PROVIDER=projects/123456789/locations/global/workloadIdentityPools/github/providers/blazra
  GCP_SERVICE_ACCOUNT=publisher@ocularminds-blazra.iam.gserviceaccount.com
  GAR_IMAGE=europe-west1-docker.pkg.dev/ocularminds-blazra/blazra/blazra
  ACR_LOGIN_SERVER=ocularmindsblazra.azurecr.io
  ACR_IMAGE=ocularmindsblazra.azurecr.io/blazra
  AZURE_CLIENT_ID=test-client-id
  AZURE_TENANT_ID=test-tenant-id
  AZURE_SUBSCRIPTION_ID=test-subscription-id
)

run_core_validator() {
  env "${core_environment[@]}" "$@" "${validator}" >/dev/null 2>&1
}

run_full_validator() {
  env "${core_environment[@]}" "${cloud_environment[@]}" "$@" \
    "${validator}" >/dev/null 2>&1
}

run_core_validator
run_full_validator
test_count=2

required_values=(
  RELEASE_TAG
  GITHUB_REPOSITORY
  GHCR_IMAGE
  GHCR_LEGACY_IMAGE
  DOCKERHUB_IMAGE
  DOCKERHUB_LEGACY_IMAGE
  DOCKERHUB_USER
  DOCKERHUB_TOKEN
)
for value_name in "${required_values[@]}"; do
  if run_core_validator "${value_name}="; then
    echo "Expected an empty ${value_name} to fail" >&2
    exit 1
  fi
  test_count=$((test_count + 1))
done

partial_cloud_values=(
  'GAR_REGISTRY=europe-west1-docker.pkg.dev'
  'GAR_PROJECT_ID=ocularminds-blazra'
  'GAR_REPOSITORY=blazra'
  'GCP_WORKLOAD_IDENTITY_PROVIDER=projects/123456789/locations/global/workloadIdentityPools/github/providers/blazra'
  'GCP_SERVICE_ACCOUNT=publisher@ocularminds-blazra.iam.gserviceaccount.com'
  'ACR_LOGIN_SERVER=ocularmindsblazra.azurecr.io'
  'AZURE_CLIENT_ID=test-client-id'
  'AZURE_TENANT_ID=test-tenant-id'
  'AZURE_SUBSCRIPTION_ID=test-subscription-id'
)
for partial_value in "${partial_cloud_values[@]}"; do
  if run_core_validator "${partial_value}"; then
    echo "Expected partial cloud configuration to fail" >&2
    exit 1
  fi
  test_count=$((test_count + 1))
done

invalid_values=(
  'RELEASE_TAG=0.3.0'
  'RELEASE_TAG=v0.3.0-rc.1'
  'GITHUB_REPOSITORY=ocularminds/kubert'
  'GHCR_IMAGE=ghcr.io/ocularminds/kubert'
  'GHCR_LEGACY_IMAGE=ghcr.io/ocularminds/blazra'
  'DOCKERHUB_IMAGE=docker.io/speedoo/kubert'
  'DOCKERHUB_LEGACY_IMAGE=docker.io/speedoo/blazra'
  'GAR_REGISTRY=https://europe-west1-docker.pkg.dev'
  'GAR_PROJECT_ID=INVALID'
  'GAR_REPOSITORY=invalid/repository'
  'GCP_WORKLOAD_IDENTITY_PROVIDER=projects/123/providers/blazra'
  'GCP_SERVICE_ACCOUNT=publisher@example.com'
  'GAR_IMAGE=europe-west1-docker.pkg.dev/ocularminds-blazra/blazra/kubert'
  'ACR_LOGIN_SERVER=https://example.azurecr.io'
  'ACR_LOGIN_SERVER=example.azurecr.io/path'
  'ACR_IMAGE=ocularmindsblazra.azurecr.io/kubert'
)
for invalid_value in "${invalid_values[@]}"; do
  if run_full_validator "${invalid_value}"; then
    echo "Expected ${invalid_value%%=*} validation to fail" >&2
    exit 1
  fi
  test_count=$((test_count + 1))
done

if run_core_validator RELEASE_TAG=v9.9.9; then
  echo "Expected a mismatched chart version to fail" >&2
  exit 1
fi
test_count=$((test_count + 1))

# These are literal GitHub Actions and shell expressions in the workflow.
# shellcheck disable=SC2016
workflow_expectations=(
  'GHCR_IMAGE: ghcr.io/ocularminds/blazra'
  'GHCR_LEGACY_IMAGE: ghcr.io/ocularminds/kubert'
  'DOCKERHUB_IMAGE: docker.io/speedoo/blazra'
  'DOCKERHUB_LEGACY_IMAGE: docker.io/speedoo/kubert'
  'GAR_IMAGE: ${{ vars.GAR_REGISTRY }}/${{ vars.GAR_PROJECT_ID }}/${{ vars.GAR_REPOSITORY }}/blazra'
  'ACR_IMAGE: ${{ vars.ACR_LOGIN_SERVER }}/blazra'
  'echo "${GHCR_LEGACY_IMAGE}"'
  'echo "${DOCKERHUB_LEGACY_IMAGE}"'
  'helm package charts/blazra --destination dist'
  '--title "Blazra ${RELEASE_TAG#v}"'
)
for expected_value in "${workflow_expectations[@]}"; do
  if ! grep -Fq -- "${expected_value}" "${release_workflow}"; then
    echo "Expected release workflow value: ${expected_value}" >&2
    exit 1
  fi
  test_count=$((test_count + 1))
done

echo "Release configuration tests passed: ${test_count}"
