/*
 * PIN / enterprise-management policy for a personality mode. Fields
 * accessed by ManagedModeActivity:
 *   policy.modeId, policy.pinHash, policy.isEnterpriseManaged
 */
package za.co.circleos.personality;

import android.os.Parcel;
import android.os.Parcelable;

public final class ManagedModePolicy implements Parcelable {

    public String  modeId;
    /** sha256 hex of the unlock PIN. Never the plaintext. */
    public String  pinHash;
    /**
     * True when the policy was pushed by an enterprise admin —
     * disables the user's "Clear" affordance in the UI.
     */
    public boolean isEnterpriseManaged;

    public ManagedModePolicy() {
        this.modeId              = "";
        this.pinHash             = "";
        this.isEnterpriseManaged = false;
    }

    private ManagedModePolicy(Parcel in) {
        this.modeId              = in.readString();
        this.pinHash             = in.readString();
        this.isEnterpriseManaged = in.readInt() != 0;
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeString(modeId);
        out.writeString(pinHash);
        out.writeInt(isEnterpriseManaged ? 1 : 0);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<ManagedModePolicy> CREATOR =
            new Parcelable.Creator<ManagedModePolicy>() {
        @Override public ManagedModePolicy createFromParcel(Parcel in) { return new ManagedModePolicy(in); }
        @Override public ManagedModePolicy[] newArray(int n) { return new ManagedModePolicy[n]; }
    };
}
