/*
 * One personality mode. Fields exposed directly because every consumer
 * (ModeChooserActivity, PersonalityMainActivity, CommunityActivity,
 * ModeEditorActivity) reads them via dot-access.
 *
 *   m.id          — stable internal identifier
 *   m.name        — user-visible name
 *   m.tier        — 1 = built-in, 2/3 = downloadable (community / pro)
 *   m.isCustom    — user-created (vs built-in or community-imported)
 *   m.config      — ModeConfig with the radio/UI/privacy switches
 */
package za.co.circleos.personality;

import android.os.Parcel;
import android.os.Parcelable;

public final class PersonalityMode implements Parcelable {

    public String     id;
    public String     name;
    /** Short blurb shown in the InferenceBridge /api/personality JSON. */
    public String     description;
    public int        tier;
    public boolean    isCustom;
    public ModeConfig config;

    public PersonalityMode() {
        this.id          = "";
        this.name        = "";
        this.description = "";
        this.tier        = 1;
        this.isCustom    = false;
        this.config      = null;
    }

    private PersonalityMode(Parcel in) {
        this.id          = in.readString();
        this.name        = in.readString();
        this.description = in.readString();
        this.tier        = in.readInt();
        this.isCustom    = in.readInt() != 0;
        this.config      = in.readParcelable(ModeConfig.class.getClassLoader());
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeString(id);
        out.writeString(name);
        out.writeString(description);
        out.writeInt(tier);
        out.writeInt(isCustom ? 1 : 0);
        out.writeParcelable(config, flags);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<PersonalityMode> CREATOR =
            new Parcelable.Creator<PersonalityMode>() {
        @Override public PersonalityMode createFromParcel(Parcel in) { return new PersonalityMode(in); }
        @Override public PersonalityMode[] newArray(int n) { return new PersonalityMode[n]; }
    };
}
