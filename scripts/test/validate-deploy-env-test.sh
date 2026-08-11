#!/usr/bin/env bash

set -uo pipefail
umask 077
export LC_ALL=C

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
VALIDATOR_SCRIPT="$SCRIPT_DIR/../validate-deploy-env.sh"
TEMP_DIR=$(mktemp -d)

PASSED=0
FAILED=0
SKIPPED=0
TEST_CASES=0
LAST_OUTPUT=""

HMAC_31=$(head -c 31 /dev/zero | base64 | tr -d '\n')
HMAC_32=$(head -c 32 /dev/zero | base64 | tr -d '\n')
HMAC_64=$(head -c 64 /dev/zero | base64 | tr -d '\n')
AES_31=$(head -c 31 /dev/zero | base64 | tr -d '\n')
AES_32=$(head -c 32 /dev/zero | base64 | tr -d '\n')
AES_33=$(head -c 33 /dev/zero | base64 | tr -d '\n')
INVALID_HMAC="invalid-hmac-sensitive-marker"
INVALID_AES="invalid-aes-sensitive-marker"
ADMIN_KEY="admin-sensitive-marker"
OCTOMO_API_KEY="octomo-sensitive-marker"
SMTP_USERNAME="smtp-test-user@example.com"
SMTP_PASSWORD="smtp-test-password"

cleanup() {
  chmod 600 "$TEMP_DIR/unreadable.env" 2>/dev/null || true
  rm -rf -- "$TEMP_DIR"
}

trap cleanup EXIT

write_fixture() {
  local target="$1"
  local hmac_secret="${2-$HMAC_32}"
  local hmac_version="${3-1}"
  local aes_key="${4-$AES_32}"
  local aes_version="${5-1}"
  local admin_enabled="${6-false}"
  local worker_enabled="${7-false}"
  local admin_key="${8-__OMIT__}"
  local octomo_api_key="${9-$OCTOMO_API_KEY}"
  local email_mode="${10-smtp}"
  local mail_username="${11-$SMTP_USERNAME}"
  local mail_password="${12-$SMTP_PASSWORD}"
  local refresh_cookie_secure="${13-true}"

  : > "$target"
  if [ "$hmac_secret" != "__OMIT__" ]; then
    printf 'GATHER_AUTH_REJOIN_BLOCK_HMAC_SECRET=%s\n' "$hmac_secret" >> "$target"
  fi
  if [ "$hmac_version" != "__OMIT__" ]; then
    printf 'GATHER_AUTH_REJOIN_BLOCK_HMAC_KEY_VERSION=%s\n' "$hmac_version" >> "$target"
  fi
  if [ "$aes_key" != "__OMIT__" ]; then
    printf 'GATHER_AUTH_SOCIAL_ACCOUNT_ENCRYPTION_KEY=%s\n' "$aes_key" >> "$target"
  fi
  if [ "$aes_version" != "__OMIT__" ]; then
    printf 'GATHER_AUTH_SOCIAL_ACCOUNT_ENCRYPTION_KEY_VERSION=%s\n' "$aes_version" >> "$target"
  fi
  if [ "$admin_enabled" != "__OMIT__" ]; then
    printf 'KAKAO_ADMIN_ENABLED=%s\n' "$admin_enabled" >> "$target"
  fi
  if [ "$admin_key" != "__OMIT__" ]; then
    printf 'KAKAO_ADMIN_KEY=%s\n' "$admin_key" >> "$target"
  fi
  if [ "$worker_enabled" != "__OMIT__" ]; then
    printf 'KAKAO_UNLINK_WORKER_ENABLED=%s\n' "$worker_enabled" >> "$target"
  fi
  if [ "$octomo_api_key" != "__OMIT__" ]; then
    printf 'OCTOMO_API_KEY=%s\n' "$octomo_api_key" >> "$target"
  fi
  if [ "$email_mode" != "__OMIT__" ]; then
    printf 'GATHER_EMAIL_MODE=%s\n' "$email_mode" >> "$target"
  fi
  if [ "$mail_username" != "__OMIT__" ]; then
    printf 'SPRING_MAIL_USERNAME=%s\n' "$mail_username" >> "$target"
  fi
  if [ "$mail_password" != "__OMIT__" ]; then
    printf 'SPRING_MAIL_PASSWORD=%s\n' "$mail_password" >> "$target"
  fi
  if [ "$refresh_cookie_secure" != "__OMIT__" ]; then
    printf 'GATHER_REFRESH_COOKIE_SECURE=%s\n' "$refresh_cookie_secure" >> "$target"
  fi
}

run_validator() {
  local mode="$1"
  local input="${2:-}"
  local output_file="$3"
  local status

  case "$mode" in
    file)
      bash "$VALIDATOR_SCRIPT" "$input" > "$output_file" 2>&1
      ;;
    stdin)
      bash "$VALIDATOR_SCRIPT" - < "$input" > "$output_file" 2>&1
      ;;
    no-arguments)
      bash "$VALIDATOR_SCRIPT" > "$output_file" 2>&1
      ;;
    two-arguments)
      bash "$VALIDATOR_SCRIPT" "$input" "$input" > "$output_file" 2>&1
      ;;
    *)
      printf 'Unknown test mode: %s\n' "$mode" > "$output_file"
      return 99
      ;;
  esac
  status=$?
  return "$status"
}

run_success_case() {
  local name="$1"
  local mode="$2"
  local input="$3"
  local output_file
  local status

  TEST_CASES=$((TEST_CASES + 1))
  output_file="$TEMP_DIR/output-$TEST_CASES.log"
  run_validator "$mode" "$input" "$output_file"
  status=$?
  LAST_OUTPUT="$output_file"

  if [ "$status" -eq 0 ]; then
    PASSED=$((PASSED + 1))
    printf 'PASS: %s\n' "$name"
  else
    FAILED=$((FAILED + 1))
    printf 'FAIL: %s (expected success, exit=%s)\n' "$name" "$status"
  fi
}

run_failure_case() {
  local name="$1"
  local mode="$2"
  local input="${3:-}"
  local output_file
  local status

  TEST_CASES=$((TEST_CASES + 1))
  output_file="$TEMP_DIR/output-$TEST_CASES.log"
  run_validator "$mode" "$input" "$output_file"
  status=$?
  LAST_OUTPUT="$output_file"

  if [ "$status" -ne 0 ]; then
    PASSED=$((PASSED + 1))
    printf 'PASS: %s\n' "$name"
  else
    FAILED=$((FAILED + 1))
    printf 'FAIL: %s (expected failure)\n' "$name"
  fi
}

assert_output_does_not_contain() {
  local name="$1"
  local output_file="$2"
  local sensitive_value="$3"

  if grep -Fq -- "$sensitive_value" "$output_file"; then
    FAILED=$((FAILED + 1))
    printf 'FAIL: %s (sensitive value was exposed)\n' "$name"
  else
    PASSED=$((PASSED + 1))
    printf 'PASS: %s\n' "$name"
  fi
}

skip_case() {
  local name="$1"
  local reason="$2"

  TEST_CASES=$((TEST_CASES + 1))
  SKIPPED=$((SKIPPED + 1))
  printf 'SKIP: %s (%s)\n' "$name" "$reason"
}

FIXTURE="$TEMP_DIR/test.env"

write_fixture "$FIXTURE"
run_success_case "default false/false configuration" file "$FIXTURE"

write_fixture "$FIXTURE" "$HMAC_64"
run_success_case "64-byte HMAC secret" file "$FIXTURE"

write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 true false "$ADMIN_KEY"
run_success_case "Admin enabled and worker disabled" file "$FIXTURE"

write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 true true "$ADMIN_KEY"
run_success_case "Admin and worker enabled" file "$FIXTURE"

write_fixture "$FIXTURE"
run_success_case "Base64 padding is preserved" file "$FIXTURE"

write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 true false "unit-test-admin=key"
run_success_case "additional equals signs are preserved" file "$FIXTURE"

write_fixture "$FIXTURE"
printf 'UNTRACKED_COMMAND=$(touch should-not-run)\n' >> "$FIXTURE"
run_success_case "unknown variables are ignored as data" file "$FIXTURE"

write_fixture "$FIXTURE"
{
  printf '\n'
  printf '   # full-line comment\n'
} >> "$FIXTURE"
run_success_case "blank lines and full-line comments" file "$FIXTURE"

write_fixture "$TEMP_DIR/lf.env"
while IFS= read -r line || [ -n "$line" ]; do
  printf '%s\r\n' "$line"
done < "$TEMP_DIR/lf.env" > "$FIXTURE"
run_success_case "CRLF environment file" file "$FIXTURE"

write_fixture "$FIXTURE"
run_success_case "file path input" file "$FIXTURE"

write_fixture "$FIXTURE"
run_success_case "stdin input" stdin "$FIXTURE"

write_fixture "$FIXTURE" "$HMAC_32" 2147483647 "$AES_32" 2147483647
run_success_case "maximum key version" file "$FIXTURE"

run_failure_case "missing input argument" no-arguments
write_fixture "$FIXTURE"
run_failure_case "too many input arguments" two-arguments "$FIXTURE"
run_failure_case "missing environment file" file "$TEMP_DIR/missing.env"

write_fixture "$TEMP_DIR/unreadable.env"
chmod 000 "$TEMP_DIR/unreadable.env"
if [ -r "$TEMP_DIR/unreadable.env" ]; then
  skip_case "unreadable environment file" "current user can still read mode 000 files"
else
  run_failure_case "unreadable environment file" file "$TEMP_DIR/unreadable.env"
fi
chmod 600 "$TEMP_DIR/unreadable.env"

write_fixture "$FIXTURE" __OMIT__
run_failure_case "missing HMAC secret" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" __OMIT__
run_failure_case "missing HMAC key version" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 __OMIT__
run_failure_case "missing AES key" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" __OMIT__
run_failure_case "missing AES key version" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 __OMIT__ false
run_failure_case "missing Admin boolean" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 false __OMIT__
run_failure_case "missing worker boolean" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 false false __OMIT__ __OMIT__
run_failure_case "missing OCTOMO API key" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 false false __OMIT__ ""
run_failure_case "empty OCTOMO API key" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 false false __OMIT__ " $OCTOMO_API_KEY"
run_failure_case "OCTOMO API key has surrounding whitespace" file "$FIXTURE"
assert_output_does_not_contain "OCTOMO API key is not exposed" "$LAST_OUTPUT" "$OCTOMO_API_KEY"

write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 false false __OMIT__ "$OCTOMO_API_KEY" __OMIT__
run_failure_case "missing email mode" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 false false __OMIT__ "$OCTOMO_API_KEY" invalid
run_failure_case "invalid email mode" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 false false __OMIT__ "$OCTOMO_API_KEY" log
run_failure_case "log email mode in production" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 false false __OMIT__ "$OCTOMO_API_KEY" smtp __OMIT__
run_failure_case "SMTP username is missing" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 false false __OMIT__ "$OCTOMO_API_KEY" smtp ""
run_failure_case "SMTP username is empty" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 false false __OMIT__ "$OCTOMO_API_KEY" smtp "$SMTP_USERNAME" __OMIT__
run_failure_case "SMTP password is missing" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 false false __OMIT__ "$OCTOMO_API_KEY" smtp "$SMTP_USERNAME" ""
run_failure_case "SMTP password is empty" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 false false __OMIT__ "$OCTOMO_API_KEY" smtp "$SMTP_USERNAME" "$SMTP_PASSWORD" __OMIT__
run_failure_case "refresh cookie secure is missing" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 false false __OMIT__ "$OCTOMO_API_KEY" smtp "$SMTP_USERNAME" "$SMTP_PASSWORD" false
run_failure_case "refresh cookie secure is false" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 false false __OMIT__ "$OCTOMO_API_KEY" smtp "$SMTP_USERNAME" "$SMTP_PASSWORD" not-a-boolean
run_failure_case "refresh cookie secure is invalid" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 false false __OMIT__ "$OCTOMO_API_KEY" smtp "$SMTP_USERNAME" "$SMTP_PASSWORD" true
run_success_case "SMTP configuration with secure refresh cookie" file "$FIXTURE"

write_fixture "$FIXTURE" "$INVALID_HMAC"
run_failure_case "invalid HMAC Base64" file "$FIXTURE"
assert_output_does_not_contain "invalid HMAC is not exposed" "$LAST_OUTPUT" "$INVALID_HMAC"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$INVALID_AES"
run_failure_case "invalid AES Base64" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_31"
run_failure_case "31-byte HMAC secret" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_31"
run_failure_case "31-byte AES key" file "$FIXTURE"
assert_output_does_not_contain "AES key is not exposed" "$LAST_OUTPUT" "$AES_31"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_33"
run_failure_case "33-byte AES key" file "$FIXTURE"

for invalid_version in v1 0 -1 1.0 +1 01 " 1" 2147483648 999999999999999999999999999999999999; do
  write_fixture "$FIXTURE" "$HMAC_32" "$invalid_version"
  run_failure_case "invalid HMAC key version form" file "$FIXTURE"
done
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 2147483648
run_failure_case "AES key version exceeds Java int range" file "$FIXTURE"

for invalid_boolean in FALSE yes flase "" " false"; do
  write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 "$invalid_boolean" false
  run_failure_case "invalid Admin boolean" file "$FIXTURE"
done

write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 true false
run_failure_case "Admin key is missing" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 true false ""
run_failure_case "Admin key is empty" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 true false "   "
run_failure_case "Admin key is blank" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 true false " $ADMIN_KEY"
run_failure_case "Admin key has surrounding whitespace" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 false true "$ADMIN_KEY"
run_failure_case "worker enabled while Admin disabled" file "$FIXTURE"
assert_output_does_not_contain "Admin key is not exposed" "$LAST_OUTPUT" "$ADMIN_KEY"

write_fixture "$FIXTURE"
printf 'KAKAO_ADMIN_ENABLED=true\n' >> "$FIXTURE"
run_failure_case "duplicate tracked key with different value" file "$FIXTURE"
write_fixture "$FIXTURE"
printf 'KAKAO_ADMIN_ENABLED=false\n' >> "$FIXTURE"
run_failure_case "duplicate tracked key with same value" file "$FIXTURE"
write_fixture "$FIXTURE"
printf 'OCTOMO_API_KEY=duplicate-octomo-key\n' >> "$FIXTURE"
run_failure_case "duplicate OCTOMO API key" file "$FIXTURE"

write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 __OMIT__ false
printf 'export KAKAO_ADMIN_ENABLED=false\n' >> "$FIXTURE"
run_failure_case "export syntax is unsupported" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 '"false"' false
run_failure_case "double-quoted tracked value is unsupported" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 "'false'" false
run_failure_case "single-quoted tracked value is unsupported" file "$FIXTURE"
write_fixture "$FIXTURE" "$HMAC_32" 1 "$AES_32" 1 "false # comment" false
run_failure_case "inline comment is part of the boolean value" file "$FIXTURE"

printf '\nTest cases: %s, Passed checks: %s, Failed checks: %s, Skipped cases: %s\n' \
  "$TEST_CASES" "$PASSED" "$FAILED" "$SKIPPED"

if [ "$FAILED" -ne 0 ]; then
  exit 1
fi
