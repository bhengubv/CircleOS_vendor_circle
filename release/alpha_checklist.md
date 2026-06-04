# CircleOS Alpha Release Checklist

## Version: 0.1.0-alpha
## Minimum Device: Xiaomi Redmi Note 12 (SM6225/sky)
## Reference Device: Google Pixel 6 (GS101/oriole)

---

## Build Verification

- [ ] `lunch circle_redmi_note12-userdebug && make -j$(nproc)` succeeds
- [ ] `lunch circle_pixel6-userdebug && make -j$(nproc)` succeeds
- [ ] `adb shell getprop ro.circle.version` → `0.1.0-alpha`
- [ ] `adb shell getprop ro.circle.build.type` → `userdebug`

## Privacy Services (adb shell service check)

- [ ] `circle.privacy` → found
- [ ] `circle.permission` → found
- [ ] `circle.analytics` → found
- [ ] `circle.notification_privacy` → found
- [ ] `circle.clipboard_privacy` → found
- [ ] `circle.camera_privacy` → found
- [ ] `circle.backup` → found

## Privacy Framework Tests

- [ ] Default-deny internet: fresh app install cannot reach internet
- [ ] Network grant: grant permission → app reaches internet → revoke → blocked again
- [ ] Auto-revoke: unused permissions revoked after 7-day simulation
- [ ] Contacts scoping: READ_CONTACTS app returns 0 rows without scope grant
- [ ] Clipboard privacy: background app cannot read clipboard
- [ ] Camera indicator: notification appears when camera opens
- [ ] Mic indicator: notification appears when audio is recorded
- [ ] DoH: DNS queries go to Quad9 over HTTPS (verify with network capture)

## De-Googling

- [ ] No GMS present: `pm list packages | grep com.google.android.gms` → empty (or microG only)
- [ ] DNS is Quad9: `getprop net.dns1` → `9.9.9.9`
- [ ] microG GmsCore installed and running
- [ ] Push notifications work via microG/UnifiedPush

## Security

- [ ] SELinux enforcing: `getenforce` → `Enforcing`
- [ ] KASLR enabled (verify via kernel boot log)
- [ ] Circle services in correct SELinux domain (not `init` domain)

## UI

- [ ] CircleLauncher loads as default home screen
- [ ] Privacy widget shows correct status (green = protected)
- [ ] CircleSettings opens and shows Privacy Dashboard
- [ ] Per-app privacy scores displayed correctly

## OTA

- [ ] `circle.update` service running
- [ ] Update check configured (test URL or updates.circleos.org)

---

## Release Steps

1. Build release images for Redmi Note 12 and Pixel 6
2. Sign with release keys (not test keys)
3. Upload to GitHub Releases as `circle-0.1.0-alpha-<device>-<date>.zip`
4. SHA-256 checksum alongside each image
5. Make repositories public (`frameworks/base`, `vendor/circle`, `device/circle/*`)
6. Post announcement once all checklist items pass

---

## Known Alpha Limitations

- Kernel sources for SM6225 must be separately synced from Xiaomi OSS kernel tree
- OTA server (`updates.circleos.org`) not yet deployed — manual sideload only for alpha
- microG APKs are placeholders — build from source or use official microG releases
- Camera2 API on Tensor G1 needs validation with third-party camera apps
- `circle.backup` service encryption key derivation UI not yet implemented (key must be provided programmatically)
