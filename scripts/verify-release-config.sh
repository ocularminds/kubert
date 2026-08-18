#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

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
  if [[ -z "${!value_name:-}" ]]; then
    echo "Missing release environment value: ${value_name}" >&2
    exit 1
  fi
done

require_complete_group() {
  local group_name="$1"
  shift
  local configured=false
  local value_name

  for value_name in "$@"; do
    if [[ -n "${!value_name:-}" ]]; then
      configured=true
    fi
  done
  if [[ "${configured}" == false ]]; then
    return
  fi
  for value_name in "$@"; do
    if [[ -z "${!value_name:-}" ]]; then
      echo "Incomplete ${group_name} release configuration: ${value_name}" >&2
      exit 1
    fi
  done
}

require_complete_group Google \
  GAR_REGISTRY \
  GAR_PROJECT_ID \
  GAR_REPOSITORY \
  GCP_WORKLOAD_IDENTITY_PROVIDER \
  GCP_SERVICE_ACCOUNT
require_complete_group Azure \
  ACR_LOGIN_SERVER \
  AZURE_CLIENT_ID \
  AZURE_TENANT_ID \
  AZURE_SUBSCRIPTION_ID
require_complete_group "Amazon ECR Public" \
  AWS_ACCOUNT_ID \
  AWS_ROLE_TO_ASSUME \
  ECR_PUBLIC_REGISTRY_ALIAS

require_match() {
  local value_name="$1"
  local value="$2"
  local pattern="$3"
  if [[ ! "${value}" =~ ${pattern} ]]; then
    echo "Invalid release environment value: ${value_name}" >&2
    exit 1
  fi
}

require_match RELEASE_TAG "${RELEASE_TAG}" '^v[0-9]+\.[0-9]+\.[0-9]+$'

require_exact() {
  local value_name="$1"
  local value="$2"
  local expected="$3"
  if [[ "${value}" != "${expected}" ]]; then
    echo "Invalid release environment value: ${value_name}" >&2
    exit 1
  fi
}

require_exact GITHUB_REPOSITORY "${GITHUB_REPOSITORY}" 'ocularminds/blazra'
require_exact GHCR_IMAGE "${GHCR_IMAGE}" 'ghcr.io/ocularminds/blazra'
require_exact GHCR_LEGACY_IMAGE "${GHCR_LEGACY_IMAGE}" 'ghcr.io/ocularminds/kubert'
require_exact DOCKERHUB_IMAGE "${DOCKERHUB_IMAGE}" 'docker.io/speedoo/blazra'
require_exact DOCKERHUB_LEGACY_IMAGE "${DOCKERHUB_LEGACY_IMAGE}" \
  'docker.io/speedoo/kubert'

if [[ -n "${GAR_REGISTRY:-}" ]]; then
  require_match GAR_REGISTRY "${GAR_REGISTRY}" '^[a-z0-9.-]+-docker\.pkg\.dev$'
  require_match GAR_PROJECT_ID "${GAR_PROJECT_ID}" '^[a-z][a-z0-9-]{4,28}[a-z0-9]$'
  require_match GAR_REPOSITORY "${GAR_REPOSITORY}" '^[a-z0-9][a-z0-9._-]*$'
  require_match GCP_WORKLOAD_IDENTITY_PROVIDER "${GCP_WORKLOAD_IDENTITY_PROVIDER}" '^projects/[0-9]+/locations/global/workloadIdentityPools/[^/]+/providers/[^/]+$'
  require_match GCP_SERVICE_ACCOUNT "${GCP_SERVICE_ACCOUNT}" '^[^@]+@[^@]+\.iam\.gserviceaccount\.com$'
  require_exact GAR_IMAGE "${GAR_IMAGE:-}" \
    "${GAR_REGISTRY}/${GAR_PROJECT_ID}/${GAR_REPOSITORY}/blazra"
fi
if [[ -n "${ACR_LOGIN_SERVER:-}" ]]; then
  require_match ACR_LOGIN_SERVER "${ACR_LOGIN_SERVER}" '^[a-z0-9]+\.azurecr\.io$'
  require_exact ACR_IMAGE "${ACR_IMAGE:-}" "${ACR_LOGIN_SERVER}/blazra"
fi
if [[ -n "${AWS_ROLE_TO_ASSUME:-}" ]]; then
  require_match AWS_ACCOUNT_ID "${AWS_ACCOUNT_ID}" '^[0-9]{12}$'
  require_match AWS_ROLE_TO_ASSUME "${AWS_ROLE_TO_ASSUME}" \
    "^arn:aws:iam::${AWS_ACCOUNT_ID}:role/[-A-Za-z0-9+=,.@_/]+$"
  require_match ECR_PUBLIC_REGISTRY_ALIAS "${ECR_PUBLIC_REGISTRY_ALIAS}" \
    '^[a-z][a-z0-9]+([._-][a-z0-9]+)*$'
  if (( ${#ECR_PUBLIC_REGISTRY_ALIAS} > 50 )); then
    echo "Invalid release environment value: ECR_PUBLIC_REGISTRY_ALIAS" >&2
    exit 1
  fi
  require_exact ECR_PUBLIC_IMAGE "${ECR_PUBLIC_IMAGE:-}" \
    "public.ecr.aws/${ECR_PUBLIC_REGISTRY_ALIAS}/blazra"
fi

version="${RELEASE_TAG#v}"
chart_file="${project_root}/charts/blazra/Chart.yaml"
build_file="${project_root}/build.gradle"
settings_file="${project_root}/settings.gradle"
if ! grep -Fxq "name: blazra" "${chart_file}"; then
  echo "Chart name is not blazra" >&2
  exit 1
fi
if ! grep -Fxq "version: ${version}" "${chart_file}"; then
  echo "Chart version does not match ${RELEASE_TAG}" >&2
  exit 1
fi
if ! grep -Fxq "appVersion: \"${version}\"" "${chart_file}"; then
  echo "Chart appVersion does not match ${RELEASE_TAG}" >&2
  exit 1
fi
if ! grep -Fxq "version = '${version}'" "${build_file}"; then
  echo "Application version does not match ${RELEASE_TAG}" >&2
  exit 1
fi
if ! grep -Fxq "rootProject.name = 'blazra'" "${settings_file}"; then
  echo "Gradle project name is not blazra" >&2
  exit 1
fi
