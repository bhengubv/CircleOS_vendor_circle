# Circle Play Runtime (#24)

The compatibility backend that actually runs Windows games for the Circle Play
library. The library (`za.co.circleos.circleplay`) fires
`za.co.circleos.circleplay.LAUNCH` with the game's exe + title; this app
(`za.co.circleos.circleplay.runtime`) hosts the display Surface and drives the
stack: **Box64** (x86_64→ARM64) running **Wine** (Windows API) with **DXVK**
(Direct3D→Vulkan).

## What's real here, and what's the frontier

**Done — committed, real for its layer:**
- The launch contract handler + display host (`RuntimeActivity`).
- The session/process orchestration (`RuntimeSession`): Wine-prefix bootstrap,
  the Box64→Wine→game process graph, environment (WINEPREFIX/DISPLAY/DXVK),
  lifecycle, log capture, clean teardown (`wineserver -k`).
- The native-pack contract (`NativePack`) + graceful "pack not installed" UX.
- The JNI seam to the render backend (`SurfaceBridge`).
- The native-pack build pipeline (`tools/fetch-runtime.sh`): builds Box64 from
  source, fetches Wine x86_64 + DXVK, assembles + versions the pack.

**The open frontier — honestly not finished:**
- `libcircleplay_render.so` + `circle-xserver`: the bridge that renders Wine's
  X11 output onto the Android Surface and forwards touch/controller input. This
  is the genuinely hard, sustained piece (a Winlator/Termux-X11-class effort).
  Until it exists, the pack installs and the orchestration runs, but games can't
  display — and the runtime says so plainly rather than faking it.

So Circle Play **front-end → contract → orchestration → build pipeline** is built
end to end; the last mile (the render bridge) is the one part of the OS that ships
as it matures, the way every console's compatibility layer has.

## Install the pack

```sh
ANDROID_NDK_HOME=/path/to/ndk \
  vendor/circle/apps/CirclePlayRuntime/tools/fetch-runtime.sh \
    --   # set WINE_X86_64_URL + RENDER_SRC for a complete pack
# -> out/circle-play-runtime.tar.zst, installed to the runtime's files dir
```
