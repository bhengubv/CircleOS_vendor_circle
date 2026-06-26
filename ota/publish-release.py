#!/usr/bin/env python3
"""
Circle OS - publish an OTA release to a channel registry (#15 / #36).

Records a release in a per-channel manifest that the on-device CircleUpdateService
polls via GET /api/os/check. Computes the payload SHA-256 so the device can verify
the download before applying it. The payload bytes themselves are produced by the
release build (ota_from_target_files) and hosted at --payload-url; this tool manages
the *control plane* (which version is current on which channel), not the payload.

Usage:
  python3 publish-release.py --channel stable --version 0.1.1-alpha \
      --payload out/circleos-0.1.1-alpha-payload.bin \
      --payload-url https://cdn.thegeek.co.za/circleos/0.1.1-alpha/payload.bin \
      [--min-version 0.1.0] [--device generic_arm64] [--registry ./channels]
"""
import argparse, hashlib, json, os, sys


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    ap = argparse.ArgumentParser(description="Publish a Circle OS OTA release to a channel.")
    ap.add_argument("--channel", required=True, help="e.g. stable | beta")
    ap.add_argument("--version", required=True, help="e.g. 0.1.1-alpha")
    ap.add_argument("--payload", required=True, help="local path to payload.bin (for hashing)")
    ap.add_argument("--payload-url", required=True, help="public URL the device will download")
    ap.add_argument("--min-version", default="0.0.0", help="lowest current version allowed to take this OTA")
    ap.add_argument("--device", default="generic_arm64", help="ro.product.system.device this build targets")
    ap.add_argument("--registry", default="channels", help="directory holding <channel>.json manifests")
    a = ap.parse_args()

    if not os.path.isfile(a.payload):
        sys.exit("payload not found: " + a.payload)
    os.makedirs(a.registry, exist_ok=True)

    entry = {
        "channel": a.channel,
        "device": a.device,
        "version": a.version,
        "payload_url": a.payload_url,
        "payload_sha256": sha256(a.payload),
        "min_version": a.min_version,
    }
    out = os.path.join(a.registry, a.channel + ".json")
    with open(out, "w") as f:
        json.dump(entry, f, indent=2)
        f.write("\n")
    print("published %s to channel '%s' -> %s" % (a.version, a.channel, out))
    print("  payload_sha256: " + entry["payload_sha256"])


if __name__ == "__main__":
    main()
