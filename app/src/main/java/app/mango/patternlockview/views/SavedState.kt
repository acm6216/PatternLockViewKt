package app.mango.patternlockview.views

import android.os.Parcel
import android.os.Parcelable
import android.view.View

class SavedState : View.BaseSavedState {

    val serializedPattern: String
    val displayMode: Int
    val isInputEnabled: Boolean
    val isInStealthMode: Boolean
    val isTactileFeedbackEnabled: Boolean

    constructor(
        superState: Parcelable, serializedPattern: String,
        displayMode: Int, inputEnabled: Boolean, inStealthMode: Boolean,
        tactileFeedbackEnabled: Boolean
    ) : super(superState) {
        this.serializedPattern = serializedPattern
        this.displayMode = displayMode
        isInputEnabled = inputEnabled
        isInStealthMode = inStealthMode
        isTactileFeedbackEnabled = tactileFeedbackEnabled
    }

    private constructor(pa: Parcel) : super(pa) {
        serializedPattern = pa.readString().toString()
        displayMode = pa.readInt()
        isInputEnabled = (pa.readValue(null) as Boolean?)!!
        isInStealthMode = (pa.readValue(null) as Boolean?)!!
        isTactileFeedbackEnabled = (pa.readValue(null) as Boolean?)!!
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeString(serializedPattern)
        dest.writeInt(displayMode)
        dest.writeValue(isInputEnabled)
        dest.writeValue(isInStealthMode)
        dest.writeValue(isTactileFeedbackEnabled)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<SavedState> {
        override fun createFromParcel(parcel: Parcel): SavedState = SavedState(parcel)
        override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
    }
}