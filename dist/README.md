# Circle OS — verifiable, uncensorable distribution (#23 / SOS‑B6)

Circle OS ships **peer‑to‑peer over BitTorrent**, with mirror fallback, and every
download is **cryptographically verifiable** against the Circle OS release key.
No single host is a takedown chokepoint, and no mirror or peer can hand you a
tampered image without it being detected.

## Tools here
- `make_torrent.py` — pure‑stdlib `.torrent` + magnet builder (trackers + BEP‑19 web seeds).
- `sign-release.sh` — produces `SHA256SUMS` + a detached signature over the manifest.

## Publish a release
1. Build the release image, e.g. `circleos-0.1.0-alpha.img.gz`.
2. **Sign** it (authenticity):
   ```bash
   ./sign-release.sh -k release_key.pem circleos-0.1.0-alpha.img.gz
   ```
   → `SHA256SUMS` + `SHA256SUMS.sig`. (Convert the Android `.pk8` to PEM once — see the script header.)
3. **Torrent** it (distribution), pointing web seeds at your mirror(s):
   ```bash
   python3 make_torrent.py circleos-0.1.0-alpha.img.gz \
       -w https://dl.circleos.co.za/circleos-0.1.0-alpha.img.gz \
       -c "Circle OS 0.1.0-alpha"
   ```
   → `circleos-0.1.0-alpha.img.gz.torrent` + `.magnet.txt`.
4. **Publish**: the `.torrent`, the `.magnet.txt`, `SHA256SUMS`, `SHA256SUMS.sig`, and the
   public key `circleos-release.pub`. Seed from at least one always‑on box.

## What a user does
1. Open the magnet or `.torrent` in any client → downloads from peers **and** the web‑seed mirrors.
2. **Verify before flashing** (both must say OK):
   ```bash
   sha256sum -c SHA256SUMS
   openssl dgst -sha256 -verify circleos-release.pub -signature SHA256SUMS.sig SHA256SUMS
   ```
3. Install with the Circle OS installer (`../circleos-installer/`).

## Why this matters
- **Uncensorable** — P2P + magnet means no single host to block; mirrors are optional
  accelerators (web seeds), not chokepoints.
- **Tamper‑evident** — the signature covers the hash manifest, so a hostile mirror or peer
  cannot slip in a backdoored image without failing verification.
- **Self‑healing** — a torrent survives dead mirrors as long as one seed remains.

This is the "coexist, don't conquer" distribution pillar: the OS reaches people even where
app stores and mirrors won't — no carrier gating, no central kill switch.
