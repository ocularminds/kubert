#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

required_values=(
  RELEASE_TAG
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
if [[ -n "${GAR_REGISTRY:-}" ]]; then
  require_match GAR_REGISTRY "${GAR_REGISTRY}" '^[a-z0-9.-]+-docker\.pkg\.dev$'
  require_match GAR_PROJECT_ID "${GAR_PROJECT_ID}" '^[a-z][a-z0-9-]{4,28}[a-z0-9]$'
  require_match GAR_REPOSITORY "${GAR_REPOSITORY}" '^[a-z0-9][a-z0-9._-]*$'
  require_match GCP_WORKLOAD_IDENTITY_PROVIDER "${GCP_WORKLOAD_IDENTITY_PROVIDER}" '^projects/[0-9]+/locations/global/workloadIdentityPools/[^/]+/providers/[^/]+$'
  require_match GCP_SERVICE_ACCOUNT "${GCP_SERVICE_ACCOUNT}" '^[^@]+@[^@]+\.iam\.gserviceaccount\.com$'
fi
if [[ -n "${ACR_LOGIN_SERVER:-}" ]]; then
  require_match ACR_LOGIN_SERVER "${ACR_LOGIN_SERVER}" '^[a-z0-9]+\.azurecr\.io$'
fi

version="${RELEASE_TAG#v}"
chart_file="${project_root}/charts/kubert/Chart.yaml"
if ! grep -Fxq "version: ${version}" "${chart_file}"; then
  echo "Chart version does not match ${RELEASE_TAG}" >&2
  exit 1
fi
if ! grep -Fxq "appVersion: \"${version}\"" "${chart_file}"; then
  echo "Chart appVersion does not match ${RELEASE_TAG}" >&2
  exit 1
fi
