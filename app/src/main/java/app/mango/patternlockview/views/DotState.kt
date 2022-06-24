package app.mango.patternlockview.views

import android.animation.ValueAnimator

class DotState {
    var mScale = 1.0f
    var mTranslateY = 0.0f
    var mAlpha = 1.0f
    var mSize = 0f
    var mLineEndX = Float.MIN_VALUE
    var mLineEndY = Float.MIN_VALUE
    var mLineAnimator: ValueAnimator? = null
}