#!/usr/bin/env bash
# Circle Play runtime — build the native pack (#24).
#
# Assembles the Box64 + Wine + DXVK + X-server pack that CirclePlayRuntime drives.
# This builds what is genuinely buildable today and is honest about the one part
# that is not yet finished — the Wine->Android surface render bridge
# (libcircleplay_render.so). It will NOT fabricate a fake render backend; if the
# bridge sources aren't present it leaves it out and says so, and the runtime
# degrades gracefully (shows "runtime pack incomplete").
#
# Output: out/circle-play-runtime/  with bin/, lib/, lib/x86_64/, PACK_VERSION,
# and circle-play-runtime.tar.zst for distribution.
#
# Prereqs: git, cmake, ndk (ANDROID_NDK_HOME), meson+ninja (DXVK), zstd, tar.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="${SCRIPT_DIR}/out/circle-play-runtime"
WORK="${SCRIPT_DIR}/work"
PACK_VERSION=1
ABI="arm64-v8a"

# Pinned upstreams (our forks where we carry patches).
BOX64_REPO="${BOX64_REPO:-https://github.com/ptitSeb/box64.git}"
BOX64_REF="${BOX64_REF:-v0.3.0}"
WINE_X86_64_URL="${WINE_X86_64_URL:-}"   # a glibc x86_64 Wine build that runs under Box64
DXVK_REPO="${DXVK_REPO:-https://github.com/doitsujin/dxvk.git}"
DXVK_REF="${DXVK_REF:-v2.4}"
RENDER_SRC="${RENDER_SRC:-}"             # path to the libcircleplay_render bridge sources (frontier)

log() { echo "==> $*"; }
die() { echo "ERROR: $*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "missing tool: $1"; }

need git; need cmake; need tar
[ -n "${ANDROID_NDK_HOME:-}" ] || die "set ANDROID_NDK_HOME to your NDK"

mkdir -p "${OUT}/bin" "${OUT}/lib" "${OUT}/lib/x86_64" "${WORK}"

# ── Box64 (x86_64 -> ARM64) ───────────────────────────────────────────────────
log "building Box64 ${BOX64_REF}"
if [ ! -d "${WORK}/box64" ]; then
  git clone --depth 1 --branch "${BOX64_REF}" "${BOX64_REPO}" "${WORK}/box64"
fi
cmake -S "${WORK}/box64" -B "${WORK}/box64/build" \
  -DCMAKE_TOOLCHAIN_FILE="${ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="${ABI}" -DANDROID_PLATFORM=android-29 \
  -DARM_DYNAREC=ON -DANDROID=ON -DCMAKE_BUILD_TYPE=Release
cmake --build "${WORK}/box64/build" -j"$(nproc)"
cp "${WORK}/box64/build/box64" "${OUT}/bin/box64"
chmod +x "${OUT}/bin/box64"

# ── Wine (x86_64, runs under Box64) ───────────────────────────────────────────
if [ -n "${WINE_X86_64_URL}" ]; then
  log "fetching Wine x86_64"
  curl -fsSL "${WINE_X86_64_URL}" -o "${WORK}/wine.tar.xz"
  mkdir -p "${WORK}/wine" && tar -xf "${WORK}/wine.tar.xz" -C "${WORK}/wine" --strip-components=1
  cp -a "${WORK}/wine/bin/." "${OUT}/bin/"            # wine, wineboot, wineserver, ...
  cp -a "${WORK}/wine/lib/." "${OUT}/lib/x86_64/"
else
  echo "WARN: WINE_X86_64_URL not set — Wine not bundled. Set it to a glibc x86_64 Wine build." >&2
fi

# ── DXVK (D3D9/10/11 -> Vulkan), x86_64 DLLs ──────────────────────────────────
if command -v meson >/dev/null 2>&1 && command -v ninja >/dev/null 2>&1; then
  log "building DXVK ${DXVK_REF}"
  [ -d "${WORK}/dxvk" ] || git clone --depth 1 --branch "${DXVK_REF}" "${DXVK_REPO}" "${WORK}/dxvk"
  ( cd "${WORK}/dxvk" && ./package-release.sh "${DXVK_REF}" "${WORK}/dxvk-out" --no-package ) || \
    echo "WARN: DXVK build failed — continuing without it" >&2
  if [ -d "${WORK}/dxvk-out" ]; then
    mkdir -p "${OUT}/lib/x86_64/dxvk"
    find "${WORK}/dxvk-out" -name '*.dll' -exec cp {} "${OUT}/lib/x86_64/dxvk/" \;
  fi
else
  echo "WARN: meson/ninja not found — skipping DXVK (games fall back to WineD3D)." >&2
fi

# ── X server + render bridge (THE FRONTIER) ───────────────────────────────────
# The X server renders into the Android Surface via libcircleplay_render.so. The
# bridge is the genuinely-unfinished part of #24; we build it only from real
# sources and never ship a fake one.
if [ -n "${RENDER_SRC}" ] && [ -d "${RENDER_SRC}" ]; then
  log "building circle render bridge + X server"
  cmake -S "${RENDER_SRC}" -B "${WORK}/render/build" \
    -DCMAKE_TOOLCHAIN_FILE="${ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="${ABI}" -DANDROID_PLATFORM=android-29 -DCMAKE_BUILD_TYPE=Release
  cmake --build "${WORK}/render/build" -j"$(nproc)"
  cp "${WORK}/render/build/libcircleplay_render.so" "${OUT}/lib/"
  cp "${WORK}/render/build/circle-xserver" "${OUT}/bin/"
  chmod +x "${OUT}/bin/circle-xserver"
else
  echo "FRONTIER: render bridge sources (RENDER_SRC) not provided." >&2
  echo "          The pack will install Box64/Wine/DXVK, but games can't display" >&2
  echo "          until libcircleplay_render.so + circle-xserver are built. This is" >&2
  echo "          the one part of Circle Play that is still being written." >&2
fi

echo "${PACK_VERSION}" > "${OUT}/PACK_VERSION"

# ── Package ───────────────────────────────────────────────────────────────────
if command -v zstd >/dev/null 2>&1; then
  log "packaging"
  tar -C "${SCRIPT_DIR}/out" -cf - circle-play-runtime | zstd -19 -o "${SCRIPT_DIR}/out/circle-play-runtime.tar.zst" -f
  echo "✓ ${SCRIPT_DIR}/out/circle-play-runtime.tar.zst"
fi
echo "✓ pack assembled at ${OUT}"
[ -f "${OUT}/lib/libcircleplay_render.so" ] \
  && echo "  render bridge: PRESENT — games can display" \
  || echo "  render bridge: MISSING — orchestration ready, display is the open frontier"
