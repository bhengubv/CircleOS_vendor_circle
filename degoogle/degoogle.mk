# Circle OS - De-Googling configuration
# Removes or stubs Google telemetry, replaces with privacy-respecting alternatives.

# ---- Remove Google apps and services ----
PRODUCT_PACKAGES_EX := \
    PrebuiltGmsCore \
    GmsCore \
    Phonesky \
    GoogleServicesFramework \
    GoogleLoginService \
    GoogleBackupTransport \
    GoogleContactsSyncAdapter \
    GoogleCalendarSyncAdapter \
    PartnerBookmarksProvider \
    WebViewGoogle \
    Chrome \
    TalkBack

# ---- Replace with privacy-respecting alternatives ----
PRODUCT_PACKAGES += \
    WebViewAOSP \
    F-Droid

# ---- microG (open-source GMS replacement for app compatibility) ----
# Provides stub GMS APIs so apps that require GMS can still function
# without sending data to Google.
PRODUCT_PACKAGES += \
    GmsCore-microG \
    FakeStore

# ---- DNS: replace Google DNS with Quad9 (privacy + security) ----
PRODUCT_PROPERTY_OVERRIDES += \
    net.dns1=9.9.9.9 \
    net.dns2=149.112.112.112 \
    net.dns3=1.1.1.1

# ---- Disable Google telemetry system properties ----
PRODUCT_PROPERTY_OVERRIDES += \
    ro.com.google.gmsversion= \
    ro.error.receiver.system.apps= \
    ro.setupwizard.mode=DISABLED \
    ro.setupwizard.enterprise_mode=1 \
    setupwizard.feature.baseline_setupwizard_enabled=false \
    ro.opa.eligible_device=false \
    ro.com.android.dataroaming=false

# ---- Disable crash reporting to Google ----
PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.usap_pool_enabled=false \
    ro.kernel.android.checkjni=0

# ---- Replace NTP server with privacy-respecting alternative ----
PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.ntp_server=pool.ntp.org

# ---- Disable DropBox uploads ----
PRODUCT_PROPERTY_OVERRIDES += \
    persist.sys.dropbox.max_files=0 \
    persist.sys.dropbox.quota_kb=0

# Include in vendor makefile
$(call inherit-product, vendor/circle/degoogle/degoogle.mk)
