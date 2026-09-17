#!/usr/bin/env sh
set -eu

port="${1:-8082}"
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
openapi="$script_dir/../openapi.yaml"

if [ ! -f "$openapi" ]; then
  echo "Contrato OpenAPI não encontrado: $openapi" >&2
  exit 1
fi

exec npx --yes @stoplight/prism-cli@5.16.0 mock "$openapi" -h 127.0.0.1 -p "$port" --errors
