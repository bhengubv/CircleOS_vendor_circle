/*
 * One "the user might want this" suggestion from the personality
 * engine's behavioural model. Displayed in LearningSuggestionsActivity;
 * the user can accept or dismiss each one.
 *
 *   sug.id          — stable identifier for accept/dismiss callbacks
 *   sug.description — one-line summary shown in the list and dialog
 *   sug.confidence  — 0..100 model confidence (shown as "[N%]")
 */
package za.co.circleos.personality;

import android.os.Parcel;
import android.os.Parcelable;

public final class LearningSuggestion implements Parcelable {

    public String id;
    public String description;
    public int    confidence;
    /** Mode the suggestion would create or modify, if applicable. */
    public String relatedModeId;

    public LearningSuggestion() {
        this.id            = "";
        this.description   = "";
        this.confidence    = 0;
        this.relatedModeId = "";
    }

    private LearningSuggestion(Parcel in) {
        this.id            = in.readString();
        this.description   = in.readString();
        this.confidence    = in.readInt();
        this.relatedModeId = in.readString();
    }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeString(id);
        out.writeString(description);
        out.writeInt(confidence);
        out.writeString(relatedModeId);
    }

    @Override public int describeContents() { return 0; }

    public static final Parcelable.Creator<LearningSuggestion> CREATOR =
            new Parcelable.Creator<LearningSuggestion>() {
        @Override public LearningSuggestion createFromParcel(Parcel in) { return new LearningSuggestion(in); }
        @Override public LearningSuggestion[] newArray(int n) { return new LearningSuggestion[n]; }
    };
}
