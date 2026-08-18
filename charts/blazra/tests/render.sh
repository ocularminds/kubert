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

assert_not_contains() {
  local file="$1"
  local value="$2"
  if grep -Fq -- "${value}" "${file}"; then
    fail "did not expect ${value} in ${file}"
  fi
}

helm lint "${chart_dir}" --strict
helm template demo "${chart_dir}" \
  --namespace demo \
  --kube-version 1.29.0 > "${test_dir}/default.yaml"

assert_contains "${test_dir}/default.yaml" "kind: Deployment"
assert_contains "${test_dir}/default.yaml" "restartPolicy: Always"
assert_contains "${test_dir}/default.yaml" "resourceNames:"
assert_contains "${test_dir}/default.yaml" "- demo-blazra"
assert_contains "${test_dir}/default.yaml" "helm.sh/chart: blazra-0.2.0"
assert_contains "${test_dir}/default.yaml" "app.kubernetes.io/name: blazra"
assert_contains "${test_dir}/default.yaml" "- name: blazra"
assert_contains "${test_dir}/default.yaml" "ghcr.io/ocularminds/kubert:0.2.0"
assert_contains "${test_dir}/default.yaml" "automountServiceAccountToken: false"
assert_contains "${test_dir}/default.yaml" "runAsUser: 65532"
assert_contains "${test_dir}/default.yaml" "defaultMode: 0444"
assert_contains "${test_dir}/default.yaml" "name: BLAZRA_NAMESPACE"
assert_contains "${test_dir}/default.yaml" "name: BLAZRA_DEPLOYMENT"
assert_contains "${test_dir}/default.yaml" "name: BLAZRA_CONTAINER"
assert_contains "${test_dir}/default.yaml" "name: BLAZRA_POLL_INTERVAL"
assert_contains "${test_dir}/default.yaml" "name: BLAZRA_UPDATE_POLICY"
assert_contains "${test_dir}/default.yaml" "name: BLAZRA_CONNECT_TIMEOUT"
assert_contains "${test_dir}/default.yaml" "name: BLAZRA_REQUEST_TIMEOUT"
assert_contains "${test_dir}/default.yaml" "name: BLAZRA_DRY_RUN"
assert_contains "${test_dir}/default.yaml" "value: \"PATCH\""
assert_not_contains "${test_dir}/default.yaml" "KUBERT_"

mount_count="$(grep -Fc 'mountPath: /var/run/secrets/kubernetes.io/serviceaccount' \
  "${test_dir}/default.yaml")"
[[ "${mount_count}" == "1" ]] || fail "the API token must be mounted only in Blazra"

if grep -Fq 'DOCKER_HUB_TOKEN' "${test_dir}/default.yaml"; then
  fail "anonymous installs must not emit credential references"
fi

helm template secured "${chart_dir}" \
  --namespace apps \
  --kube-version 1.29.0 \
  --set workload.containerName=web \
  --set-string 'podLabels.app\.kubernetes\.io/name=untrusted' \
  --set-string 'podAnnotations.kubectl\.kubernetes\.io/default-container=untrusted' \
  --set blazra.registryCredentials.existingSecret=docker-hub \
  --set blazra.registryCredentials.usernameKey=account \
  --set blazra.registryCredentials.tokenKey=access-token > "${test_dir}/secured.yaml"

assert_contains "${test_dir}/secured.yaml" "name: DOCKER_HUB_USERNAME"
assert_contains "${test_dir}/secured.yaml" "name: DOCKER_HUB_TOKEN"
assert_contains "${test_dir}/secured.yaml" "name: \"docker-hub\""
assert_contains "${test_dir}/secured.yaml" "key: \"access-token\""
assert_contains "${test_dir}/secured.yaml" "value: \"web\""
assert_not_contains "${test_dir}/secured.yaml" "untrusted"

digest="sha256:$(printf 'a%.0s' {1..64})"
helm template pinned "${chart_dir}" \
  --kube-version 1.29.0 \
  --set workload.image.tag= \
  --set workload.image.digest="${digest}" \
  --set blazra.image.digest="${digest}" > "${test_dir}/pinned.yaml"

assert_contains "${test_dir}/pinned.yaml" "nginx@${digest}"
assert_contains "${test_dir}/pinned.yaml" "ghcr.io/ocularminds/kubert@${digest}"

helm template external-access "${chart_dir}" \
  --namespace apps \
  --kube-version 1.29.0 \
  --set service.enabled=false \
  --set rbac.create=false \
  --set serviceAccount.create=false \
  --set serviceAccount.name=existing-blazra > "${test_dir}/external-access.yaml"

assert_contains "${test_dir}/external-access.yaml" "serviceAccountName: existing-blazra"
assert_not_contains "${test_dir}/external-access.yaml" "kind: ServiceAccount"
assert_not_contains "${test_dir}/external-access.yaml" "kind: Role"
assert_not_contains "${test_dir}/external-access.yaml" "kind: Service"

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
  --set blazra.pollInterval=PT5S > /dev/null 2>&1; then
  fail "poll intervals below ten seconds must be rejected"
fi

if helm template invalid-policy "${chart_dir}" \
  --kube-version 1.29.0 \
  --set blazra.updatePolicy=EVERYTHING > /dev/null 2>&1; then
  fail "unknown update policies must be rejected"
fi

if helm template missing-service-account "${chart_dir}" \
  --kube-version 1.29.0 \
  --set serviceAccount.create=false > /dev/null 2>&1; then
  fail "an existing service account name must be required"
fi

if helm template invalid-digest "${chart_dir}" \
  --kube-version 1.29.0 \
  --set blazra.image.digest=sha256:invalid > /dev/null 2>&1; then
  fail "invalid image digests must be rejected"
fi

if helm template legacy-values "${chart_dir}" \
  --kube-version 1.29.0 \
  --set kubert.dryRun=true > /dev/null 2>&1; then
  fail "the removed kubert values namespace must be rejected"
fi

helm package "${chart_dir}" --destination "${test_dir}" > /dev/null
[[ -f "${test_dir}/blazra-0.2.0.tgz" ]] || fail "chart package was not created"
