package app.mango.patternlockview.views

import android.os.Parcel
import android.os.Parcelable

class Dot : Parcelable {
    var row: Int
        private set
    var column: Int
        private set

    companion object {

        private val sDots: Array<Array<Dot>> = Array(PatternLockView.sDotCount){ i->
            Array(PatternLockView.sDotCount){ j->
                Dot(i,j)
            }
        }

        @Synchronized
        fun of(row: Int, column: Int): Dot {
            checkRange(row, column)
            return sDots[row][column]
        }

        /** 从其标识符获取单元格 */
        @Synchronized
        fun of(id: Int): Dot {
            return of(id / PatternLockView.sDotCount, id % PatternLockView.sDotCount)
        }

        private fun checkRange(row: Int, column: Int) {
            if (row < 0 || row > PatternLockView.sDotCount - 1) {
                throw IllegalArgumentException(
                    "mRow must be in range 0-${PatternLockView.sDotCount - 1}"
                )
            }
            if (column < 0 || column > PatternLockView.sDotCount - 1) {
                throw IllegalArgumentException(
                    "mColumn must be in range 0-${PatternLockView.sDotCount - 1}"
                )
            }
        }

        @JvmField
        val CREATOR: Parcelable.Creator<Dot> = object : Parcelable.Creator<Dot> {
            override fun createFromParcel(p: Parcel): Dot = Dot(p)
            override fun newArray(size: Int): Array<Dot?> = arrayOfNulls(size)
        }
    }

    private constructor(row: Int, column: Int) {
        checkRange(row, column)
        this.row = row
        this.column = column
    }

    /** 获取点的标识符。从左到右，从上到下，从0开始计数 */
    val id: Int get() = row * PatternLockView.sDotCount + column

    override fun toString(): String = "(Row = $row, Col = $column)"

    override fun equals(other: Any?): Boolean {
        return if (other is Dot) (column == other.column
                && row == other.row) else super.equals(other)
    }

    override fun hashCode(): Int = 31 * row + column

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(column)
        dest.writeInt(row)
    }

    private constructor(p: Parcel) {
        column = p.readInt()
        row = p.readInt()
    }
}