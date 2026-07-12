#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SMOKE_ROOT="${PROJECT_ROOT}/target/cncf.d/render-web-page-browser-profile"
SERVER_PORT="${TEXTUS_TOOLCHAIN_BROWSER_PROFILE_PORT:-19649}"
IMAGE="${TEXTUS_TOOLCHAIN_BROWSER_PROFILE_IMAGE:-textus-toolchain:browser-profile-smoke}"
server_pid=""

cleanup() {
  if [[ -n "$server_pid" ]]; then
    kill "$server_pid" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

assert_contains() {
  local value="$1"
  local expected="$2"
  local label="$3"
  if [[ "$value" != *"$expected"* ]]; then
    printf 'Expected %s to contain %s.\n%s\n' "$label" "$expected" "$value" >&2
    exit 1
  fi
}

mkdir -p "$SMOKE_ROOT"
rm -f "$SMOKE_ROOT"/*.json "$SMOKE_ROOT"/*.log "$SMOKE_ROOT"/fixture-server.py

cat > "$SMOKE_ROOT/fixture-server.py" <<'PYTHON'
import json
import pathlib
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

root = pathlib.Path(sys.argv[1])
port = int(sys.argv[2])

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        key = "reviewed" if self.path.startswith("/reviewed") else "standard"
        headers = {name.lower(): value for name, value in self.headers.items()}
        (root / f"{key}-headers.json").write_text(json.dumps(headers), encoding="utf-8")
        body = b"""<!doctype html><html><body>browser fixture<script>
document.documentElement.dataset.webdriver = String(navigator.webdriver);
document.body.dataset.userAgent = navigator.userAgent;
</script></body></html>"""
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        pass

ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
PYTHON

python3 "$SMOKE_ROOT/fixture-server.py" "$SMOKE_ROOT" "$SERVER_PORT" > "$SMOKE_ROOT/server.log" 2>&1 &
server_pid=$!
for _ in $(seq 1 50); do
  if curl -fsS "http://127.0.0.1:${SERVER_PORT}/ready" >/dev/null 2>&1; then
    break
  fi
  sleep 0.1
done
curl -fsS "http://127.0.0.1:${SERVER_PORT}/ready" >/dev/null

cat > "$SMOKE_ROOT/standard-request.json" <<JSON
{"url":"http://host.docker.internal:${SERVER_PORT}/standard","waitUntil":"load","timeoutSeconds":30,"userAgent":"MuseumBrowser/1.0","browserProfile":"standard"}
JSON
cat > "$SMOKE_ROOT/reviewed-request.json" <<JSON
{"url":"http://host.docker.internal:${SERVER_PORT}/reviewed","waitUntil":"load","timeoutSeconds":30,"userAgent":"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36","browserProfile":"reviewed_site_compatibility"}
JSON

docker build -q -t "$IMAGE" "$PROJECT_ROOT/docker/textus-toolchain" >/dev/null
docker run --rm -v "$SMOKE_ROOT:/work" -w /work "$IMAGE" \
  render-web-page --request standard-request.json --response standard-response.json
docker run --rm -v "$SMOKE_ROOT:/work" -w /work "$IMAGE" \
  render-web-page --request reviewed-request.json --response reviewed-response.json

standard_response="$(cat "$SMOKE_ROOT/standard-response.json")"
reviewed_response="$(cat "$SMOKE_ROOT/reviewed-response.json")"
reviewed_headers="$(cat "$SMOKE_ROOT/reviewed-headers.json")"

assert_contains "$standard_response" 'data-webdriver=\"true\"' "standard browser response"
assert_contains "$standard_response" 'MuseumBrowser/1.0' "standard browser response"
assert_contains "$reviewed_response" 'data-webdriver=\"false\"' "reviewed browser response"
assert_contains "$reviewed_headers" 'sec-ch-ua' "reviewed request headers"
assert_contains "$reviewed_headers" 'Google Chrome' "reviewed request headers"
assert_contains "$reviewed_headers" 'macOS' "reviewed request headers"

printf 'Browser profile Playwright smoke passed.\n'
