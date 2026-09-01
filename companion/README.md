# Circle companion (sub-Android tier)

The bottom rung of "one codebase, every device": chips too small for AOSP
(ESP32, nRF52) run this tiny C node instead. It carries the device's Circle
identity and joins the Aether mesh, so a $5 sensor is still part of your Circle
-- it just doesn't render the full UI.

- `circle_mesh_node.c` - identity (libsodium Ed25519) + mesh presence loop.
- Links against **aether-protocol's C port** (`aether-protocol/c`), the same
  protocol the phones speak, kept byte-identical by the shared fixtures.

## Status / honest gap
The aether-protocol C port is **not yet synced onto this build server**, so the
mesh calls (`aethernet_node_init` / `aethernet_mesh_join` / `aethernet_presence_beacon`)
are declared here as the integration seam, not yet linked. To build for real:
sync `aether-protocol/c`, build `libaether-protocol`, then `make` here (POSIX) or
add as an ESP-IDF / nRF component for the target chip.
