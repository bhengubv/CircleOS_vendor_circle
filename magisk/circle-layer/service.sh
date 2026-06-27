#!/system/bin/sh
# Circle Layer — late_start service (#152). Runs after the system has booted.
MODDIR=${0%/*}

# Wait for boot completion so the Circle apps can be addressed.
until [ "$(getprop sys.boot_completed)" = "1" ]; do
  sleep 2
done

# Marker for Circle apps probing whether the layer is present (vs full Circle OS).
resetprop -n ro.circle.layer.active 1

log -t CircleLayer "Circle Layer active (systemless overlay on stock Android)"
