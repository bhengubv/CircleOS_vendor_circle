# Biometric — on-device test plan

Biometric authorization is wired in code (commit `175a248`). It is **verified by
build-logic + brace/paren balance only** — these cases close caveat 3 (real
sensor) during the long test pass. Run on a handset **with a fingerprint/face
sensor** (the device supplies the HAL; a GSI rides the device's own biometric).

## Pre-req
- [ ] Flash/boot Circle OS on a device whose vendor partition provides a
      fingerprint or face HAL.
- [ ] Confirm `BiometricManager.canAuthenticate(BIOMETRIC_STRONG|DEVICE_CREDENTIAL)`
      returns `BIOMETRIC_SUCCESS` after enrolling (Settings → Security).

## Wallet — send (outgoing transfer)
- [ ] Enroll a fingerprint + a PIN. Open Wallet → enter amount → **Send**.
- [ ] **Expect:** BiometricPrompt appears ("Authorise payment of R…"). Money does
      NOT move before auth.
- [ ] Authenticate with fingerprint → transfer proceeds.
- [ ] Repeat, **cancel** the prompt → transfer does NOT happen (fail-closed).
- [ ] Repeat, fail biometric 3× → fall through to PIN → PIN succeeds → proceeds.

## Wallet — accept (incoming transfer)
- [ ] Trigger an incoming NFC transfer → **Accept**.
- [ ] **Expect:** BiometricPrompt ("Authorise accepting R…") before
      `acceptIncomingTransfer` runs. Cancel → not accepted.

## Wallet — quick-pay tile (lock screen)
- [ ] Add the QuickPay tile. From the **lock screen**, tap it.
- [ ] **Expect:** pay dialog → **Send** still raises the BiometricPrompt (the
      tile routes through `showPayDialog`; it does not bypass the gate).

## Tap-to-pay on a locked device (the closed hole)
- [ ] Lock the phone. Hold it to an NFC payment terminal.
- [ ] **Expect:** NO payment — `hce_service.xml requireDeviceUnlock="true"` blocks
      HCE until the device is unlocked (biometric/PIN). This is the regression
      test for the locked-phone-pays vulnerability.

## Fail-closed with nothing enrolled
- [ ] On a device with NO biometric and NO screen lock, attempt a wallet send.
- [ ] **Expect:** toast "Set a screen lock or fingerprint to authorise payments";
      money does NOT move.

## Inherited device-unlock (caveat 2 sanity)
- [ ] Lock screen → unlock with the enrolled fingerprint/face.
- [ ] **Expect:** works via the stock AOSP keyguard (no Circle code path) — proves
      the framework biometric stack survived de-Googling and the device HAL is live.
