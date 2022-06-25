package app.mango.patternlockview.views

import android.animation.ValueAnimator

data class DotState(
    var mScale:Float = 1.0f, var mTranslateY:Float = 0.0f,
    var mAlpha:Float = 1.0f, var mSize:Float = 0f,
    var mLineEndX:Float = Float.MIN_VALUE,
    var mLineEndY:Float = Float.MIN_VALUE,
    var mLineAnimator: ValueAnimator? = null
)