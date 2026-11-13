#!/bin/sh
set -eu

# 1. Support DATABASE_URL fallback if DB_URL is not explicitly set
if [ -z "${DB_URL:-}" ] && [ -n "${DATABASE_URL:-}" ]; then
  DB_URL="$DATABASE_URL"
fi

# 2. Convert Render/Postgres URI (postgres:// or postgresql://) to JDBC format
case "${DB_URL:-}" in
  postgres://*|postgresql://*)
    no_scheme="${DB_URL#postgres://}"
    no_scheme="${no_scheme#postgresql://}"
    authority="${no_scheme%%/*}"
    if [ "$authority" = "$no_scheme" ]; then
      path_and_query=""
    else
      path_and_query="/${no_scheme#*/}"
    fi

    case "$authority" in
      *@*)
        userinfo="${authority%@*}"
        hostport="${authority##*@}"
        case "$userinfo" in
          *:*)
            parsed_user="${userinfo%%:*}"
            parsed_pass="${userinfo#*:}"
            ;;
          *)
            parsed_user="$userinfo"
            parsed_pass=""
            ;;
        esac
        if [ -z "${DB_USERNAME:-}" ] && [ -n "$parsed_user" ]; then
          export DB_USERNAME="$parsed_user"
        fi
        if [ -z "${DB_PASSWORD:-}" ] && [ -n "$parsed_pass" ]; then
          export DB_PASSWORD="$parsed_pass"
        fi
        export DB_URL="jdbc:postgresql://${hostport}${path_and_query}"
        ;;
      *)
        export DB_URL="jdbc:postgresql://${no_scheme}"
        ;;
    esac
    ;;
esac

# 3. Construct JDBC URL from DB_HOST / DB_PORT / DB_NAME (used by Render Blueprints)
if [ -z "${DB_URL:-}" ] && [ -n "${DB_HOST:-}" ]; then
  export DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT:-5432}/${DB_NAME:-ledgerline}"
fi

normalize_url() {
  val=$(printf '%s' "$1" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//;s:/*$::')
  if [ -z "$val" ]; then
    printf ''
    return
  fi
  case "$val" in
    http://*|https://*)
      printf '%s' "$val"
      ;;
    *)
      printf 'https://%s' "$val"
      ;;
  esac
}

# 4. Normalize FRONTEND_URL and CORS_ALLOWED_ORIGINS (strip trailing slashes, ensure scheme)
if [ -n "${FRONTEND_URL:-}" ]; then
  FRONTEND_URL=$(normalize_url "$FRONTEND_URL")
  export FRONTEND_URL
fi

if [ -n "${CORS_ALLOWED_ORIGINS:-}" ]; then
  normalized_origins=""
  old_ifs="$IFS"
  IFS=','
  for origin in $CORS_ALLOWED_ORIGINS; do
    clean_origin=$(normalize_url "$origin")
    if [ -n "$clean_origin" ]; then
      if [ -z "$normalized_origins" ]; then
        normalized_origins="$clean_origin"
      else
        normalized_origins="${normalized_origins},${clean_origin}"
      fi
    fi
  done
  IFS="$old_ifs"
  export CORS_ALLOWED_ORIGINS="$normalized_origins"
fi

# Default container-friendly JVM memory settings for Render (e.g. 512 MB Free/Starter instances)
exec java ${JAVA_OPTS:--XX:MaxRAMPercentage=75.0} -jar /app/app.jar
