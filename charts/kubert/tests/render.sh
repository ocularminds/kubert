#!/usr/bin/env bash
set -euo pipefail

chart_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
test_dir="$(mktemp -d)"
trap 'rm -rf "${test_dir}"' EXIT

fail() {
  echo "chart test failed: $1" >&2
  exit 1
}

assert_contains() {
  local file="$1"
  local value="$2"
  grep -Fq -- "${value}" "${file}" || fail "expected ${value} in ${file}"
}

helm lint "${chart_dir}" --strict
helm template demo "${chart_dir}" \
  --namespace demo \
  --kube-version 1.29.0 > "${test_dir}/default.yaml"

assert_contains "${test_dir}/default.yaml" "kind: Deployment"
assert_contains "${test_dir}/default.yaml" "restartPolicy: Always"
assert_contains "${test_dir}/default.yaml" "resourceNames:"
assert_contains "${test_dir}/default.yaml" "- demo-kubert"
assert_contains "${test_dir}/default.yaml" "automountServiceAccountToken: false"
assert_contains "${test_dir}/default.yaml" "runAsUser: 65532"
assert_contains "${test_dir}/default.yaml" "defaultMode: 0444"
assert_contains "${test_dir}/default.yaml" "name: KUBERT_UPDATE_POLICY"
assert_contains "${test_dir}/default.yaml" "value: \"PATCH\""

mount_count="$(grep -Fc 'mountPath: /var/run/secrets/kubernetes.io/serviceaccount' \
  "${test_dir}/default.yaml")"
[[ "${mount_count}" == "1" ]] || fail "the API token must be mounted only in Kubert"

if grep -Fq 'DOCKER_HUB_TOKEN' "${test_dir}/default.yaml"; then
  fail "anonymous installs must not emit credential references"
fi

helm template secured "${chart_dir}" \
  --namespace apps \
  --kube-version 1.29.0 \
  --set workload.containerName=web \
  --set-string 'podLabels.app\.kubernetes\.io/name=untrusted' \
  --set-string 'podAnnotations.kubectl\.kubernetes\.io/default-container=untrusted' \
  --set kubert.registryCredentials.existingSecret=docker-hub \
  --set kubert.registryCredentials.usernameKey=account \
  --set kubert.registryCredentials.tokenKey=access-token > "${test_dir}/secured.yaml"

assert_contains "${test_dir}/secured.yaml" "name: DOCKER_HUB_USERNAME"
assert_contains "${test_dir}/secured.yaml" "name: DOCKER_HUB_TOKEN"
assert_contains "${test_dir}/secured.yaml" "name: docker-hub"
assert_contains "${test_dir}/secured.yaml" "key: access-token"
assert_contains "${test_dir}/secured.yaml" "value: web"
if grep -Fq 'untrusted' "${test_dir}/secured.yaml"; then
  fail "custom metadata must not override chart-owned keys"
fi

digest="sha256:$(printf 'a%.0s' {1..64})"
helm template pinned "${chart_dir}" \
  --kube-version 1.29.0 \
  --set workload.image.tag= \
  --set workload.image.digest="${digest}" \
  --set kubert.image.digest="${digest}" > "${test_dir}/pinned.yaml"

assert_contains "${test_dir}/pinned.yaml" "nginx@${digest}"
assert_contains "${test_dir}/pinned.yaml" "ghcr.io/ocularminds/kubert@${digest}"

if helm template invalid "${chart_dir}" \
  --kube-version 1.29.0 \
  --set workload.replicaCount=2 > /dev/null 2>&1; then
  fail "multiple workload replicas must be rejected"
fi

if helm template unsupported "${chart_dir}" \
  --kube-version 1.28.0 > /dev/null 2>&1; then
  fail "Kubernetes versions without native sidecars must be rejected"
fi

if helm template invalid-duration "${chart_dir}" \
  --kube-version 1.29.0 \
  --set kubert.pollInterval=PT5S > /dev/null 2>&1; then
  fail "poll intervals below ten seconds must be rejected"
fi

if helm template invalid-policy "${chart_dir}" \
  --kube-version 1.29.0 \
  --set kubert.updatePolicy=EVERYTHING > /dev/null 2>&1; then
  fail "unknown update policies must be rejected"
fi

helm package "${chart_dir}" --destination "${test_dir}" > /dev/null
[[ -f "${test_dir}/kubert-0.2.0.tgz" ]] || fail "chart package was not created"
