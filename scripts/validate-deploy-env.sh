#!/usr/bin/env bash

set -euo pipefail
umask 077
export LC_ALL=C

MAX_KEY_VERSION="2147483647"
TEMP_DIR=""

HMAC_SECRET=""
HMAC_KEY_VERSION=""
AES_KEY=""
AES_KEY_VERSION=""
KAKAO_ADMIN_ENABLED_VALUE=""
KAKAO_ADMIN_KEY_VALUE=""
KAKAO_UNLINK_WORKER_ENABLED_VALUE=""
OCTOMO_API_KEY_VALUE=""
EMAIL_MODE_VALUE=""
MAIL_USERNAME_VALUE=""
MAIL_PASSWORD_VALUE=""
REFRESH_COOKIE_SECURE_VALUE=""

HMAC_SECRET_SEEN=false
HMAC_KEY_VERSION_SEEN=false
AES_KEY_SEEN=false
AES_KEY_VERSION_SEEN=false
KAKAO_ADMIN_ENABLED_SEEN=false
KAKAO_ADMIN_KEY_SEEN=false
KAKAO_UNLINK_WORKER_ENABLED_SEEN=false
OCTOMO_API_KEY_SEEN=false
EMAIL_MODE_SEEN=false
MAIL_USERNAME_SEEN=false
MAIL_PASSWORD_SEEN=false
REFRESH_COOKIE_SECURE_SEEN=false

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

cleanup() {
  if [ -n "$TEMP_DIR" ] && [ -d "$TEMP_DIR" ]; then
    rm -rf -- "$TEMP_DIR"
  fi
}

validate_arguments() {
  if [ "$#" -ne 1 ]; then
    fail "usage: validate-deploy-env.sh <environment-file|->"
  fi
}

validate_tracked_value_format() {
  local variable_name="$1"
  local value="$2"

  if [[ "$value" =~ ^[[:space:]] ]] || [[ "$value" =~ [[:space:]]$ ]]; then
    fail "$variable_name must not contain leading or trailing whitespace"
  fi

  if [[ "$value" == \"*\" ]] || [[ "$value" == \'*\' ]]; then
    fail "$variable_name must use the unquoted KEY=value format"
  fi
}

record_tracked_value() {
  local variable_name="$1"
  local value="$2"

  validate_tracked_value_format "$variable_name" "$value"

  case "$variable_name" in
    GATHER_AUTH_REJOIN_BLOCK_HMAC_SECRET)
      if [ "$HMAC_SECRET_SEEN" = true ]; then
        fail "duplicate tracked key: $variable_name"
      fi
      HMAC_SECRET_SEEN=true
      HMAC_SECRET="$value"
      ;;
    GATHER_AUTH_REJOIN_BLOCK_HMAC_KEY_VERSION)
      if [ "$HMAC_KEY_VERSION_SEEN" = true ]; then
        fail "duplicate tracked key: $variable_name"
      fi
      HMAC_KEY_VERSION_SEEN=true
      HMAC_KEY_VERSION="$value"
      ;;
    GATHER_AUTH_SOCIAL_ACCOUNT_ENCRYPTION_KEY)
      if [ "$AES_KEY_SEEN" = true ]; then
        fail "duplicate tracked key: $variable_name"
      fi
      AES_KEY_SEEN=true
      AES_KEY="$value"
      ;;
    GATHER_AUTH_SOCIAL_ACCOUNT_ENCRYPTION_KEY_VERSION)
      if [ "$AES_KEY_VERSION_SEEN" = true ]; then
        fail "duplicate tracked key: $variable_name"
      fi
      AES_KEY_VERSION_SEEN=true
      AES_KEY_VERSION="$value"
      ;;
    KAKAO_ADMIN_ENABLED)
      if [ "$KAKAO_ADMIN_ENABLED_SEEN" = true ]; then
        fail "duplicate tracked key: $variable_name"
      fi
      KAKAO_ADMIN_ENABLED_SEEN=true
      KAKAO_ADMIN_ENABLED_VALUE="$value"
      ;;
    KAKAO_ADMIN_KEY)
      if [ "$KAKAO_ADMIN_KEY_SEEN" = true ]; then
        fail "duplicate tracked key: $variable_name"
      fi
      KAKAO_ADMIN_KEY_SEEN=true
      KAKAO_ADMIN_KEY_VALUE="$value"
      ;;
    KAKAO_UNLINK_WORKER_ENABLED)
      if [ "$KAKAO_UNLINK_WORKER_ENABLED_SEEN" = true ]; then
        fail "duplicate tracked key: $variable_name"
      fi
      KAKAO_UNLINK_WORKER_ENABLED_SEEN=true
      KAKAO_UNLINK_WORKER_ENABLED_VALUE="$value"
      ;;
    OCTOMO_API_KEY)
      if [ "$OCTOMO_API_KEY_SEEN" = true ]; then
        fail "duplicate tracked key: $variable_name"
      fi
      OCTOMO_API_KEY_SEEN=true
      OCTOMO_API_KEY_VALUE="$value"
      ;;
    GATHER_EMAIL_MODE)
      if [ "$EMAIL_MODE_SEEN" = true ]; then
        fail "duplicate tracked key: $variable_name"
      fi
      EMAIL_MODE_SEEN=true
      EMAIL_MODE_VALUE="$value"
      ;;
    SPRING_MAIL_USERNAME)
      if [ "$MAIL_USERNAME_SEEN" = true ]; then
        fail "duplicate tracked key: $variable_name"
      fi
      MAIL_USERNAME_SEEN=true
      MAIL_USERNAME_VALUE="$value"
      ;;
    SPRING_MAIL_PASSWORD)
      if [ "$MAIL_PASSWORD_SEEN" = true ]; then
        fail "duplicate tracked key: $variable_name"
      fi
      MAIL_PASSWORD_SEEN=true
      MAIL_PASSWORD_VALUE="$value"
      ;;
    GATHER_REFRESH_COOKIE_SECURE)
      if [ "$REFRESH_COOKIE_SECURE_SEEN" = true ]; then
        fail "duplicate tracked key: $variable_name"
      fi
      REFRESH_COOKIE_SECURE_SEEN=true
      REFRESH_COOKIE_SECURE_VALUE="$value"
      ;;
  esac
}

parse_stream() {
  local line
  local content_without_leading_whitespace
  local variable_name
  local value

  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    content_without_leading_whitespace="${line#"${line%%[![:space:]]*}"}"

    if [ -z "$content_without_leading_whitespace" ] || [[ "$content_without_leading_whitespace" == \#* ]]; then
      continue
    fi

    if [[ "$line" != *=* ]]; then
      continue
    fi

    variable_name="${line%%=*}"
    value="${line#*=}"

    case "$variable_name" in
      GATHER_AUTH_REJOIN_BLOCK_HMAC_SECRET | \
        GATHER_AUTH_REJOIN_BLOCK_HMAC_KEY_VERSION | \
        GATHER_AUTH_SOCIAL_ACCOUNT_ENCRYPTION_KEY | \
        GATHER_AUTH_SOCIAL_ACCOUNT_ENCRYPTION_KEY_VERSION | \
        KAKAO_ADMIN_ENABLED | \
        KAKAO_ADMIN_KEY | \
        KAKAO_UNLINK_WORKER_ENABLED | \
        OCTOMO_API_KEY | \
        GATHER_EMAIL_MODE | \
        SPRING_MAIL_USERNAME | \
        SPRING_MAIL_PASSWORD | \
        GATHER_REFRESH_COOKIE_SECURE)
        record_tracked_value "$variable_name" "$value"
        ;;
    esac
  done
}

parse_input() {
  local input="$1"

  if [ "$input" = "-" ]; then
    parse_stream
    return
  fi

  if [ ! -e "$input" ]; then
    fail "environment file does not exist: $input"
  fi
  if [ ! -f "$input" ]; then
    fail "environment input is not a regular file: $input"
  fi
  if [ ! -r "$input" ]; then
    fail "environment file is not readable: $input"
  fi

  parse_stream < "$input"
}

require_variable() {
  local seen="$1"
  local value="$2"
  local variable_name="$3"

  if [ "$seen" != true ] || [ -z "$value" ]; then
    fail "$variable_name is required"
  fi
}

validate_boolean() {
  local value="$1"
  local variable_name="$2"

  case "$value" in
    true | false) ;;
    *) fail "$variable_name must be either true or false" ;;
  esac
}

validate_positive_integer() {
  local value="$1"
  local variable_name="$2"
  local value_length

  if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
    fail "$variable_name must be between 1 and $MAX_KEY_VERSION"
  fi

  value_length=${#value}
  if [ "$value_length" -gt 10 ] \
    || { [ "$value_length" -eq 10 ] && [[ "$value" > "$MAX_KEY_VERSION" ]]; }; then
    fail "$variable_name must be between 1 and $MAX_KEY_VERSION"
  fi
}

decode_base64() {
  local encoded_value="$1"
  local variable_name="$2"
  local decoded_file="$3"

  if ! printf '%s' "$encoded_value" | base64 --decode > "$decoded_file" 2>/dev/null; then
    fail "$variable_name must be valid Base64"
  fi
}

validate_base64_minimum_bytes() {
  local encoded_value="$1"
  local variable_name="$2"
  local minimum_bytes="$3"
  local decoded_file="$TEMP_DIR/hmac-decoded"
  local decoded_bytes

  decode_base64 "$encoded_value" "$variable_name" "$decoded_file"
  decoded_bytes=$(wc -c < "$decoded_file")
  if [ "$decoded_bytes" -lt "$minimum_bytes" ]; then
    fail "$variable_name must decode to at least $minimum_bytes bytes"
  fi
}

validate_base64_exact_bytes() {
  local encoded_value="$1"
  local variable_name="$2"
  local expected_bytes="$3"
  local decoded_file="$TEMP_DIR/aes-decoded"
  local decoded_bytes

  decode_base64 "$encoded_value" "$variable_name" "$decoded_file"
  decoded_bytes=$(wc -c < "$decoded_file")
  if [ "$decoded_bytes" -ne "$expected_bytes" ]; then
    fail "$variable_name must decode to exactly $expected_bytes bytes"
  fi
}

validate_admin_worker_relationship() {
  if [ "$KAKAO_UNLINK_WORKER_ENABLED_VALUE" = true ] \
    && [ "$KAKAO_ADMIN_ENABLED_VALUE" != true ]; then
    fail "KAKAO_UNLINK_WORKER_ENABLED=true requires KAKAO_ADMIN_ENABLED=true"
  fi

  if [ "$KAKAO_ADMIN_ENABLED_VALUE" = true ]; then
    require_variable "$KAKAO_ADMIN_KEY_SEEN" "$KAKAO_ADMIN_KEY_VALUE" "KAKAO_ADMIN_KEY"
    if [ -z "${KAKAO_ADMIN_KEY_VALUE//[[:space:]]/}" ]; then
      fail "KAKAO_ADMIN_KEY must not be blank when KAKAO_ADMIN_ENABLED=true"
    fi
  fi
}

validate_email_configuration() {
  case "$EMAIL_MODE_VALUE" in
    log | smtp) ;;
    *) fail "GATHER_EMAIL_MODE must be either log or smtp" ;;
  esac

  if [ "$EMAIL_MODE_VALUE" != smtp ]; then
    fail "GATHER_EMAIL_MODE must be smtp for production deployment"
  fi

  require_variable "$MAIL_USERNAME_SEEN" "$MAIL_USERNAME_VALUE" "SPRING_MAIL_USERNAME"
  require_variable "$MAIL_PASSWORD_SEEN" "$MAIL_PASSWORD_VALUE" "SPRING_MAIL_PASSWORD"
}

validate_refresh_cookie_security() {
  validate_boolean "$REFRESH_COOKIE_SECURE_VALUE" "GATHER_REFRESH_COOKIE_SECURE"
  if [ "$REFRESH_COOKIE_SECURE_VALUE" != true ]; then
    fail "GATHER_REFRESH_COOKIE_SECURE must be true for production deployment"
  fi
}

main() {
  validate_arguments "$@"
  parse_input "$1"

  require_variable "$HMAC_SECRET_SEEN" "$HMAC_SECRET" "GATHER_AUTH_REJOIN_BLOCK_HMAC_SECRET"
  require_variable "$HMAC_KEY_VERSION_SEEN" "$HMAC_KEY_VERSION" "GATHER_AUTH_REJOIN_BLOCK_HMAC_KEY_VERSION"
  require_variable "$AES_KEY_SEEN" "$AES_KEY" "GATHER_AUTH_SOCIAL_ACCOUNT_ENCRYPTION_KEY"
  require_variable "$AES_KEY_VERSION_SEEN" "$AES_KEY_VERSION" "GATHER_AUTH_SOCIAL_ACCOUNT_ENCRYPTION_KEY_VERSION"
  require_variable "$KAKAO_ADMIN_ENABLED_SEEN" "$KAKAO_ADMIN_ENABLED_VALUE" "KAKAO_ADMIN_ENABLED"
  require_variable "$KAKAO_UNLINK_WORKER_ENABLED_SEEN" "$KAKAO_UNLINK_WORKER_ENABLED_VALUE" "KAKAO_UNLINK_WORKER_ENABLED"
  require_variable "$OCTOMO_API_KEY_SEEN" "$OCTOMO_API_KEY_VALUE" "OCTOMO_API_KEY"
  require_variable "$EMAIL_MODE_SEEN" "$EMAIL_MODE_VALUE" "GATHER_EMAIL_MODE"
  require_variable "$REFRESH_COOKIE_SECURE_SEEN" "$REFRESH_COOKIE_SECURE_VALUE" "GATHER_REFRESH_COOKIE_SECURE"

  validate_boolean "$KAKAO_ADMIN_ENABLED_VALUE" "KAKAO_ADMIN_ENABLED"
  validate_boolean "$KAKAO_UNLINK_WORKER_ENABLED_VALUE" "KAKAO_UNLINK_WORKER_ENABLED"
  validate_email_configuration
  validate_refresh_cookie_security
  validate_positive_integer "$HMAC_KEY_VERSION" "GATHER_AUTH_REJOIN_BLOCK_HMAC_KEY_VERSION"
  validate_positive_integer "$AES_KEY_VERSION" "GATHER_AUTH_SOCIAL_ACCOUNT_ENCRYPTION_KEY_VERSION"

  TEMP_DIR=$(mktemp -d)
  trap cleanup EXIT

  validate_base64_minimum_bytes "$HMAC_SECRET" "GATHER_AUTH_REJOIN_BLOCK_HMAC_SECRET" 32
  validate_base64_exact_bytes "$AES_KEY" "GATHER_AUTH_SOCIAL_ACCOUNT_ENCRYPTION_KEY" 32
  validate_admin_worker_relationship

  printf '%s\n' "Deploy environment validation succeeded."
}

main "$@"
