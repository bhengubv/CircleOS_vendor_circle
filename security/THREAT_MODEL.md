# Circle OS — Threat Model & Self-Audit

Honest, living document. It states what Circle OS defends against, what it does
*not* yet, and a checklist for the external audit. **Circle OS is pre-release and
unaudited.** Do not market it as "military-grade" or "audited" until the open
items below are closed and an independent party has tried to break it.

## Who we defend against

| Adversary | In scope | Status |
|-----------|----------|--------|
| The operator (us) reading user content | **Yes** — blind-to-us is the core promise | E2E in place (messaging, rooms, mail) |
| A mesh relay / nearby radio observer | **Yes** | Content E2E; envelope partly hardened (see below) |
| A malicious app exfiltrating data | Yes | Per-app network kill + behavioural detection + quarantine |
| A thief with the powered-off device | Yes | FBE + verified boot + TEE-sealed keys |
| A thief with a live, unlocked device | Partial | App sandbox + lock; no anti-forensics |
| A nation-state with the device + time | **No** (not yet) | No formal hardening/attestation guarantees |

## What is actually in place

- **E2E content crypto:** X25519 + Double Ratchet (forward secrecy) +
  ChaCha20-Poly1305, keys sealed in TEE/StrongBox, strict no-plaintext receive,
  fingerprint (safety-number) verification. Unit-tested (ratchet 18/18, seal 9/9).
- **Privacy framework:** per-app network firewall, scoped/decoy responses,
  access logging.
- **Malware posture:** TrafficLobby behavioural detection → QuarantineManager.
- **Platform:** verified boot (AVB/dm-verity), hardware keystore, hardening
  package (hardened_malloc when built + kernel mitigations).

## Open items — the honest gaps

1. **Mesh envelope metadata (partly open).** Content is E2E and the wire now
   hides packet *type* and *size* (padded framing), and the device id is random
   per boot. **Still leaks:** the fixed BLE service-UUID (a Circle fingerprint —
   a rendezvous problem) and link-layer is unencrypted. → protocol-hardening
   program, not a patch.
2. **No external audit / certification.** No third-party pentest, no FIPS/CC.
   This is the single biggest blocker to any "grade" claim.
3. **Never built or booted.** Every security module above is code-in-place,
   unproven on hardware. Proof comes in the test session.
4. **Hardening not fully matched to GrapheneOS.** hardened_malloc is a recipe
   (not yet a compiled allocator on a built image); no hardened-libc patches;
   SELinux hardening dir is reserved but empty (blind neverallows break builds).

## Self-audit checklist (for the external auditor)

- [ ] Capture mesh traffic — confirm no plaintext, no type/size leak; assess the
      BLE-UUID fingerprint exposure.
- [ ] Attempt MITM on the key exchange; verify the safety-number catches it.
- [ ] Confirm forward secrecy: compromise a current key, prove past messages stay
      sealed.
- [ ] Verify keys never leave TEE/StrongBox in cleartext at rest.
- [ ] Fuzz the AIDL surfaces (mesh, quarantine, privacy) from an unprivileged app.
- [ ] Confirm per-app network kill actually drops packets at the kernel.
- [ ] Verify AVB/dm-verity rejects a tampered system image.
- [ ] Pentest the bridges (mail key directory, OTA, social) for auth bypass / IDOR.
- [ ] Confirm hardened_malloc + kernel mitigations are active on the built image.

When every box is checked by someone who doesn't work for us, *then* we can talk
about how strong it is.
