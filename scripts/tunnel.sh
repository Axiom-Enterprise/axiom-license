#!/usr/bin/env sh
set -eu

PORT="${AXIOM_PORT:-8080}"
ORIGIN="http://127.0.0.1:${PORT}"

command -v cloudflared >/dev/null 2>&1 || { echo "cloudflared not installed: brew install cloudflared" >&2; exit 1; }

if [ -n "${AXIOM_TUNNEL:-}" ]; then
  [ -n "${AXIOM_HOSTNAME:-}" ] || { echo "AXIOM_HOSTNAME is required with AXIOM_TUNNEL" >&2; exit 1; }
  cloudflared tunnel route dns "$AXIOM_TUNNEL" "$AXIOM_HOSTNAME" 2>/dev/null || true
  exec cloudflared tunnel run --url "$ORIGIN" "$AXIOM_TUNNEL"
fi

exec cloudflared tunnel --url "$ORIGIN" --no-autoupdate
