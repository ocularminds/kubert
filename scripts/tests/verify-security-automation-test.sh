#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SECURITY_WORKFLOW="${ROOT_DIR}/.github/workflows/security.yml"
DEPENDABOT_CONFIG="${ROOT_DIR}/.github/dependabot.yml"
CHECKS=0

fail() {
  echo "Security automation test failed: $*" >&2
  exit 1
}

assert_contains() {
  local file="$1"
  local expected="$2"
  local description="$3"
  grep -Fq -- "${expected}" "${file}" || fail "${description}"
  CHECKS=$((CHECKS + 1))
}

assert_count() {
  local file="$1"
  local expected="$2"
  local pattern="$3"
  local description="$4"
  local actual
  actual="$(grep -Ec -- "${pattern}" "${file}" || true)"
  [[ "${actual}" == "${expected}" ]] || fail "${description}: expected ${expected}, got ${actual}"
  CHECKS=$((CHECKS + 1))
}

[[ -f "${SECURITY_WORKFLOW}" ]] || fail "security workflow is missing"
[[ -f "${DEPENDABOT_CONFIG}" ]] || fail "Dependabot configuration is missing"

assert_contains "${SECURITY_WORKFLOW}" "contents: read" \
  "workflow must default to read-only repository access"
assert_count "${SECURITY_WORKFLOW}" 1 '^[[:space:]]+security-events: write$' \
  "only CodeQL may write security events"
assert_contains "${SECURITY_WORKFLOW}" "github.event_name == 'pull_request'" \
  "dependency review must run only for pull requests"
assert_contains "${SECURITY_WORKFLOW}" "fail-on-severity: moderate" \
  "dependency review must reject moderate or higher vulnerabilities"
assert_contains "${SECURITY_WORKFLOW}" "comment-summary-in-pr: never" \
  "dependency review must not require pull-request write access"
assert_contains "${SECURITY_WORKFLOW}" "- actions" \
  "CodeQL must scan GitHub Actions"
assert_contains "${SECURITY_WORKFLOW}" "- java-kotlin" \
  "CodeQL must scan Java"
assert_contains "${SECURITY_WORKFLOW}" "queries: security-extended" \
  "CodeQL must include extended security queries"
assert_count "${SECURITY_WORKFLOW}" 2 \
  'github/codeql-action/(init|analyze)@[0-9a-f]{40} # v[0-9]+\.[0-9]+\.[0-9]+$' \
  "CodeQL actions must be pinned to documented immutable revisions"
assert_count "${SECURITY_WORKFLOW}" 1 \
  'actions/dependency-review-action@[0-9a-f]{40} # v[0-9]+\.[0-9]+\.[0-9]+$' \
  "dependency review must be pinned to a documented immutable revision"

assert_contains "${DEPENDABOT_CONFIG}" "version: 2" \
  "Dependabot configuration version must be explicit"
assert_count "${DEPENDABOT_CONFIG}" 1 'package-ecosystem: gradle$' \
  "Gradle updates must be configured exactly once"
assert_count "${DEPENDABOT_CONFIG}" 1 'package-ecosystem: docker$' \
  "Docker updates must be configured exactly once"
assert_count "${DEPENDABOT_CONFIG}" 1 'package-ecosystem: github-actions$' \
  "GitHub Actions updates must be configured exactly once"
assert_count "${DEPENDABOT_CONFIG}" 3 '^[[:space:]]+interval: weekly$' \
  "all dependency ecosystems must use a weekly schedule"
assert_count "${DEPENDABOT_CONFIG}" 3 '^[[:space:]]+timezone: Europe/Stockholm$' \
  "all schedules must use the repository maintenance timezone"
assert_count "${DEPENDABOT_CONFIG}" 3 '^[[:space:]]+prefix: deps$' \
  "all automated commits must use the dependency prefix"

echo "Security automation tests passed: ${CHECKS}"
