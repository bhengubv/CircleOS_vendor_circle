#!/usr/bin/env python3
"""
Circle OS - reference OTA check server (#15 / #36 / SOS-A1).

Implements the exact contract the on-device CircleUpdateService polls:

  GET /api/os/check?device={d}&channel={c}&current={v}
  -> 200 application/json
     {
       "update_available": <bool>,
       "version":        "<latest on channel>",
       "payload_url":    "<url>",
       "payload_sha256": "<hex>",
       "min_version":    "<min current required>"
     }

It serves the per-channel manifests written by publish-release.py. Standard library
only, so the whole OTA plane is self-hostable - which is the point: no central,
censorable update authority. The production endpoint (ota.circleos.co.za, fronted by
SleptOnAPI) mirrors this same logic.

Run:
  python3 ota_server.py [--registry ./channels] [--port 8080]
"""
import argparse, json, os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

REGISTRY = "channels"


def parse_ver(v):
    """'0.1.1-alpha.2' -> ((0,1,1), 'alpha.2'). Non-numeric main parts become 0."""
    main, _, pre = (v or "0.0.0").partition("-")
    parts = []
    for x in main.split("."):
        try:
            parts.append(int(x))
        except ValueError:
            parts.append(0)
    while len(parts) < 3:
        parts.append(0)
    return tuple(parts[:3]), pre


def vercmp(a, b):
    """-1/0/1. Final release > pre-release at equal numeric; pre-release tags lexical."""
    na, pa = parse_ver(a)
    nb, pb = parse_ver(b)
    if na != nb:
        return -1 if na < nb else 1
    if pa == pb:
        return 0
    if not pa:            # a is final, b is pre-release -> a is newer
        return 1
    if not pb:
        return -1
    return -1 if pa < pb else 1


class Handler(BaseHTTPRequestHandler):
    server_version = "CircleOS-OTA/1.0"

    def _json(self, code, obj):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        u = urlparse(self.path)
        if u.path != "/api/os/check":
            self._json(404, {"error": "not found"})
            return
        q = parse_qs(u.query)
        channel = (q.get("channel") or ["stable"])[0]
        current = (q.get("current") or ["0.0.0"])[0]
        device = (q.get("device") or ["generic_arm64"])[0]

        path = os.path.join(REGISTRY, os.path.basename(channel) + ".json")
        if not os.path.isfile(path):
            self._json(200, {"update_available": False})
            return
        with open(path) as f:
            rel = json.load(f)

        avail = (
            rel.get("device", device) == device
            and vercmp(rel.get("version", "0.0.0"), current) > 0
            and vercmp(current, rel.get("min_version", "0.0.0")) >= 0
        )
        if avail:
            self._json(200, {
                "update_available": True,
                "version": rel["version"],
                "payload_url": rel["payload_url"],
                "payload_sha256": rel["payload_sha256"],
                "min_version": rel.get("min_version", "0.0.0"),
            })
        else:
            self._json(200, {"update_available": False})

    def log_message(self, fmt, *args):
        pass  # quiet by default; wrap with a real access log in production


def main():
    global REGISTRY
    ap = argparse.ArgumentParser(description="Circle OS reference OTA check server.")
    ap.add_argument("--registry", default="channels")
    ap.add_argument("--port", type=int, default=8080)
    a = ap.parse_args()
    REGISTRY = a.registry
    srv = ThreadingHTTPServer(("0.0.0.0", a.port), Handler)
    print("Circle OS OTA check server on :%d  (registry=%s)" % (a.port, a.registry))
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        srv.shutdown()


if __name__ == "__main__":
    main()
