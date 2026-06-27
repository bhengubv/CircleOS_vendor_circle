#!/system/bin/sh
# Circle Layer — post-fs-data (#152). Runs early, before most of the framework.
# Reserved for any pre-boot fixups (e.g. seeding a default Circle config into a
# magisk-mirror path). Kept minimal in the scaffold.
MODDIR=${0%/*}
log -t CircleLayer "post-fs-data: Circle Layer staging"
