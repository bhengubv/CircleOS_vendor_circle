# Circle OS — OTA update control plane (#15 / #36 / SOS‑A1)

The on‑device `CircleUpdateService` (compiled into `services.jar`) periodically polls for
updates. This is the **server side** it polls — the part that decides "is there a newer
build on your channel?" It is deliberately tiny and self‑hostable: the update authority
must not be a single censorable chokepoint.

## The contract (fixed by `CircleUpdateService`)
Device →
```
GET {ro.circleos.update.url}/api/os/check?device={d}&channel={c}&current={v}
```
Server →
```json
{ "update_available": true,
  "version": "0.1.1-alpha",
  "payload_url": "https://cdn.thegeek.co.za/circleos/0.1.1-alpha/payload.bin",
  "payload_sha256": "…",
  "min_version": "0.1.0" }
```
The device verifies `payload_sha256` before applying. Only the release **build** produces
the actual `payload.bin` (via `ota_from_target_files`) — this layer just points at it and
decides eligibility.

## Tools
- `publish-release.py` — record a release on a channel (hashes the payload, writes `channels/<channel>.json`).
- `ota_server.py` — reference `/api/os/check` server (standard library only). Production
  `ota.circleos.co.za` (fronted by SleptOnAPI) mirrors this exact logic.

## Publish + serve
```bash
# 1. publish a build to the beta channel
python3 publish-release.py --channel beta --version 0.1.1-alpha \
    --payload out/payload.bin \
    --payload-url https://cdn.thegeek.co.za/circleos/0.1.1-alpha/payload.bin \
    --min-version 0.1.0

# 2. serve it (self-host, or point SleptOnAPI at the same channels/ data)
python3 ota_server.py --port 8080
```

## Channels
`stable` and `beta` are independent manifests. A device on **beta** (developer‑preview,
WP‑60 / #96) gets early builds; **stable** (WP‑61 / #97 — fast, direct, no carrier gating)
gets vetted ones. Add a channel simply by publishing to a new name. Eligibility rules,
applied server‑side per request:
- the build's `device` must match the device asking,
- the build `version` must be newer than the device's `current` (semver‑ish compare,
  final‑release > pre‑release at equal numeric), and
- the device's `current` must be ≥ the build's `min_version` (no skipping a required floor).

> ⚠️ Pre‑releases sort **before** their final (`0.1.0-alpha` < `0.1.0`). For an alpha
> rollout set `min_version` to a pre‑release floor (e.g. `0.1.0-alpha`), **not** the bare
> final — otherwise you lock out the very testers you're shipping to.
