/*
 * Per-mode configuration block. Every field is directly read/written by
 * ModeEditorActivity (see cfg.X / def.X assignments). Keep this list
 * in sync with res/layout/activity_editor.xml in PersonalityEditor.
 *
 * Booleans are simple on/off radio/feature toggles; ints use 0/1/2/...
 * for spinner positions (the spinner XML defines the meaning).
 */
package za.co.circleos.personality;

import android.os.Parcel;
import android.os.Parcelable;

public final class ModeConfig implements Parcelable {

    public boolean dndEnabled;
    /** 0 = silent, 1 = priority, 2 = all. */
    public int     notificationLevel;
    public boolean wifiEnabled;
    public boolean dataEnabled;
    public boolean bluetoothEnabled;
    public boolean locationEnabled;
    /** 0 = system, 1 = light, 2 = dark, 3 = mode-themed. */
    public int     themeMode;
    public boolean enforceVpn;
    /** 0 = standard, 1 = strict, 2 = paranoid. */
    public int     privacyLevel;
    public boolean cloudSyncEnabled;
    /** -1 = auto, 0..255 = manual screen brightness. */
    public int     screenBrightness;

    public ModeConfig() {
        this.dndEnabled        = false;
        this.notificationLevel = 2;
        this.wifiEnabled       = true;
        this.dataEnabled       = true;
        this.bluetoothEnabled  = true;
        this.locationEnabled   = true;
        this.themeMode         = 0;
        this.enforceVpn        = false;
        this.privacyLevel      = 0;
        this.cloudSyncEnabled  = false;
        this.screenBrightness  = -1;
    }

    private ModeConfig(Parcel in) {
        this.dndEnabled        = in.readInt() != 0;
        this.notificationLevel = in.readInt();
        this.wifiEnabled       = in.readInt() != 0;
        this.dataEnabled       = in.readInt() != 0;
        this.bluetoothEnabled  = in.readInt() != 0;
        this.locationEnabled   = in.readInt() != 0;
        this.themeMode         = in.readInt();
        this.enforceVpn        = in.readInt() != 0;
        this.privacyLevel      = in.readInt();
        this.cloudSyncEnabled  = in.readInt() != 0;
        this.screenBrightness  = in.readInt();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(dndEnabled       ? 1 : 0);
        out.writeInt(notificationLevel);
        out.writeInt(wifiEnabled      ? 1 : 0);
        out.writeInt(dataEnabled      ? 1 : 0);
        out.writeInt(bluetoothEnabled ? 1 : 0);
        out.writeInt(locationEnabled  ? 1 : 0);
        out.writeInt(themeMode);
        out.writeInt(enforceVpn       ? 1 : 0);
        out.writeInt(privacyLevel);
        out.writeInt(cloudSyncEnabled ? 1 : 0);
        out.writeInt(screenBrightness);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<ModeConfig> CREATOR =
            new Parcelable.Creator<ModeConfig>() {
        @Override public ModeConfig createFromParcel(Parcel in) { return new ModeConfig(in); }
        @Override public ModeConfig[] newArray(int n) { return new ModeConfig[n]; }
    };
}
