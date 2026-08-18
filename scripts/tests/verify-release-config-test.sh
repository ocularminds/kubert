#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
validator="${project_root}/scripts/verify-release-config.sh"

core_environment=(
  RELEASE_TAG=v0.2.0
  DOCKERHUB_USER=speedoo
  DOCKERHUB_TOKEN=test-token
)
cloud_environment=(
  GAR_REGISTRY=europe-west1-docker.pkg.dev
  GAR_PROJECT_ID=ocularminds-kubert
  GAR_REPOSITORY=kubert
  GCP_WORKLOAD_IDENTITY_PROVIDER=projects/123456789/locations/global/workloadIdentityPools/github/providers/kubert
  GCP_SERVICE_ACCOUNT=publisher@ocularminds-kubert.iam.gserviceaccount.com
  ACR_LOGIN_SERVER=ocularmindskubert.azurecr.io
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

required_values=(RELEASE_TAG DOCKERHUB_USER DOCKERHUB_TOKEN)
for value_name in "${required_values[@]}"; do
  if run_core_validator "${value_name}="; then
    echo "Expected an empty ${value_name} to fail" >&2
    exit 1
  fi
  test_count=$((test_count + 1))
done

partial_cloud_values=(
  'GAR_REGISTRY=europe-west1-docker.pkg.dev'
  'GAR_PROJECT_ID=ocularminds-kubert'
  'GAR_REPOSITORY=kubert'
  'GCP_WORKLOAD_IDENTITY_PROVIDER=projects/123456789/locations/global/workloadIdentityPools/github/providers/kubert'
  'GCP_SERVICE_ACCOUNT=publisher@ocularminds-kubert.iam.gserviceaccount.com'
  'ACR_LOGIN_SERVER=ocularmindskubert.azurecr.io'
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
  'RELEASE_TAG=0.2.0'
  'RELEASE_TAG=v0.2.0-rc.1'
  'GAR_REGISTRY=https://europe-west1-docker.pkg.dev'
  'GAR_PROJECT_ID=INVALID'
  'GAR_REPOSITORY=invalid/repository'
  'GCP_WORKLOAD_IDENTITY_PROVIDER=projects/123/providers/kubert'
  'GCP_SERVICE_ACCOUNT=publisher@example.com'
  'ACR_LOGIN_SERVER=https://example.azurecr.io'
  'ACR_LOGIN_SERVER=example.azurecr.io/path'
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

echo "Release configuration tests passed: ${test_count}"
