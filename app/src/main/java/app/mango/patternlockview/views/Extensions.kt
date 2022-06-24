package app.mango.patternlockview.views

fun patternToString(
    patternLockView: PatternLockView,
    pattern: List<Dot>?
): String {
    pattern?:return ""
    val patternSize = pattern.size
    val stringBuilder = StringBuilder()
    for (i in 0 until patternSize) {
        val dot: Dot = pattern[i]
        stringBuilder.append(dot.row * patternLockView.dotCount + dot.column)
    }
    return stringBuilder.toString()
}

fun stringToPattern(
    patternLockView: PatternLockView,
    string: String
): List<Dot> {
    val result: MutableList<Dot> = ArrayList()
    for (element in string) {
        val number = Character.getNumericValue(element)
        result.add(
            Dot.of(
                number / patternLockView.dotCount,
                number % patternLockView.dotCount
            )
        )
    }
    return result
}