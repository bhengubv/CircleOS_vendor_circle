#!/usr/bin/env python3
"""
Circle Mail — public-key directory (#153).

The blind-to-us mail seal needs one thing from the server: a place to publish your
X25519 public key and to look up a recipient's. This is that directory — the exact
contract CircleMail's MailBridge calls:

  POST /api/mail/key            body {"key":"<x509 b64>"}   (publisher identified
                                by the X-Circle-User header the mail bridge sets
                                after it authenticates the IMAP/SMTP session)
  GET  /api/mail/key?user=<addr>  -> 200 {"key":"<x509 b64>"}  or  404 {"key":""}

Standard library only, so the whole thing is self-hostable — the point being that
the key directory, like the OTA plane (ota_server.py), needs no central,
censorable authority. The production mail bridge can host this same logic inline;
the contract is what matters. Keys are public, so the only integrity rule is
"only the authenticated owner may publish their own key" — enforced via the
header the bridge attaches.

Run:
  python3 key_directory.py [--store ./mail_keys.json] [--port 8089]
"""
import argparse
import json
import os
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

STORE = "mail_keys.json"
# Accept a base64 X.509 SubjectPublicKeyInfo (our X25519 pubkey is ~44-48 b64 chars).
KEY_RE = re.compile(r"^[A-Za-z0-9+/=]{32,512}$")


def _load():
    try:
        with open(STORE, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}


def _save(d):
    tmp = STORE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(d, f)
    os.replace(tmp, STORE)


def _norm(user):
    return (user or "").strip().lower()


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, obj):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        u = urlparse(self.path)
        if u.path != "/api/mail/key":
            return self._send(404, {"error": "not found"})
        user = _norm((parse_qs(u.query).get("user") or [""])[0])
        if not user:
            return self._send(400, {"error": "user required"})
        key = _load().get(user)
        if not key:
            return self._send(404, {"key": ""})   # not a Circle user -> sender stays plaintext
        return self._send(200, {"key": key})

    def do_POST(self):
        if urlparse(self.path).path != "/api/mail/key":
            return self._send(404, {"error": "not found"})
        # The mail bridge authenticates the IMAP/SMTP session and sets this header;
        # a publisher may only write its OWN key.
        owner = _norm(self.headers.get("X-Circle-User", ""))
        if not owner:
            return self._send(401, {"error": "unauthenticated"})
        try:
            n = int(self.headers.get("Content-Length", "0"))
            payload = json.loads(self.rfile.read(n).decode("utf-8")) if n else {}
        except Exception:
            return self._send(400, {"error": "bad json"})
        key = (payload.get("key") or "").strip()
        if not KEY_RE.match(key):
            return self._send(400, {"error": "bad key"})
        d = _load()
        if d.get(owner) == key:
            return self._send(200, {"ok": True, "unchanged": True})
        d[owner] = key
        _save(d)
        return self._send(200, {"ok": True})

    def log_message(self, *a):
        pass  # quiet


def main():
    global STORE
    ap = argparse.ArgumentParser(description="Circle Mail public-key directory.")
    ap.add_argument("--store", default=STORE)
    ap.add_argument("--port", type=int, default=8089)
    ap.add_argument("--host", default="0.0.0.0")
    a = ap.parse_args()
    STORE = a.store
    srv = ThreadingHTTPServer((a.host, a.port), Handler)
    print("Circle Mail key directory on %s:%d (store=%s)" % (a.host, a.port, STORE))
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
