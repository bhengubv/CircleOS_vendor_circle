# Installing Circle OS (dual-boot, zero-risk)

Circle OS installs **alongside** your current Android as a *Dynamic System* (DSU).
Nothing is wiped, the bootloader stays locked, and **a normal reboot always returns
you to your stock phone.** If Circle OS ever misbehaves, just reboot.

## Will it work on my phone?
- **Android 10 or newer** → yes, via DSU (below).
- **Older / locked phones that block DSU** → see *Hard‑mode* at the bottom.

---

## Path A — one‑click from a computer (easiest reliable path)
You need a PC/Mac with `adb` ([Android platform‑tools](https://developer.android.com/tools/releases/platform-tools)).

1. On the phone: **Settings → About phone → tap "Build number" 7 times** to unlock
   Developer options, then **Settings → System → Developer options → enable USB debugging.**
2. Plug the phone into the computer; tap **Allow** on the "Allow USB debugging?" prompt.
3. Download `circleos-gsi.img.gz` (the Circle OS image) next to the installer.
4. Run:
   ```bash
   ./install-circleos.sh circleos-gsi.img.gz
   ```
   (Windows: run the `.ps1` wrapper, or use the bash script under WSL/Git‑Bash.)
5. On the phone, **confirm** the install dialog, wait for it to finish, then **Restart**.
   Your phone boots into Circle OS. Reboot normally any time to go back to stock.

## Path B — entirely on the phone (no computer)
Some phones expose the DSU loader directly:

1. **Settings → About phone → tap "Build number" 7 times** (enables Developer options).
2. **Settings → System → Developer options → DSU Loader**
   (may be called "Dynamic System Updates").
3. Choose the **Circle OS** image, confirm, and **Restart** when prompted.

If **DSU Loader isn't listed**, your phone's maker disabled it — use Path A, or Hard‑mode.

## Returning to stock Android
Just **reboot** (or, after a Circle OS session, choose *Restart*). The next normal boot
is your untouched stock phone. To remove the Circle OS dynamic system entirely, use
Developer options → DSU, or it's reclaimed automatically.

---

## Why there's no "install from an app, one tap" option
Android deliberately guards the dynamic‑system install behind a **privileged system
permission** (`INSTALL_DYNAMIC_SYSTEM`). A normal app you download **cannot** hold it,
so a single‑tap in‑app installer is impossible on a stock retail phone — by Android's
design, not ours. Paths A and B are the supported ways in; both keep your phone safe.

## Hard‑mode (locked / old phones that block DSU)
For devices where the maker locks everything down (e.g. older Huawei/Kirin), Circle OS
can still **coexist** via a non‑destructive partition‑injection install (boot‑ROM unlock
+ a tiny first‑stage that boots Circle OS on **Volume‑Up**, else stock). This is an
advanced, device‑specific procedure — see the hard‑mode reference. It is the proving
ground: "coexist, don't conquer."
