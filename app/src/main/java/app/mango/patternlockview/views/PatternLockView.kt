package app.mango.patternlockview.views

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Parcelable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import androidx.annotation.*
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import app.mango.patternlockview.R
import kotlin.math.*

class PatternLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
): View(context, attrs, defStyleAttr) {

    @IntDef(AspectRatio.ASPECT_RATIO_SQUARE, AspectRatio.ASPECT_RATIO_WIDTH_BIAS, AspectRatio.ASPECT_RATIO_HEIGHT_BIAS)
    @Retention(AnnotationRetention.SOURCE)
    annotation class AspectRatio {
        companion object {
            /**宽度和高度是一样的。最小宽度和高度*/
            const val ASPECT_RATIO_SQUARE = 0
            /**宽度将被固定。高度将是宽度和高度的最小值*/
            const val ASPECT_RATIO_WIDTH_BIAS = 1
            /**高度是固定的。宽度将是宽度和高度的最小值*/
            const val ASPECT_RATIO_HEIGHT_BIAS = 2
        }
    }

    @IntDef(PatternViewMode.CORRECT, PatternViewMode.AUTO_DRAW, PatternViewMode.WRONG)
    @Retention(AnnotationRetention.SOURCE)
    annotation class PatternViewMode {
        companion object {
            /** 此状态表示用户正确绘制的模式。路径的颜色和圆点都将被更改为这种颜色。*/
            const val CORRECT = 0
            /** 自动绘制模式：演示或教程。 */
            const val AUTO_DRAW = 1
            /** 此状态表示用户错误绘制的模式。路径的颜色和圆点都将被更改为这种颜色。*/
            const val WRONG = 2
        }
    }

    private var mDotStates: Array<Array<DotState>>
    private var mPatternSize = 0
    private var mDrawingProfilingStarted = false
    private var mAnimatingPeriodStart: Long = 0
    private val mHitFactor = 0.6f

    companion object {
        var sDotCount = 0
        private const val DEFAULT_PATTERN_DOT_COUNT = 3
        private const val PROFILE_DRAWING = false

        /**
         * 如果设置了动画模式，那么动画化一个锁定模式的每个圆圈所花费的时间(以米为单位)。
         * 整个动画应该使用这个常量来完成模式的长度。
         */
        private const val MILLIS_PER_CIRCLE_ANIMATING = 700
        /** 动画一个点所花费的时间(以米为单位) */
        private const val DEFAULT_DOT_ANIMATION_DURATION = 190
        /** 动画路径结束所花费的时间(以米为单位) */
        private const val DEFAULT_PATH_END_ANIMATION_DURATION = 100
        /** 这可以用来避免更新显示非常小的动作或嘈杂的面板 */
        private const val DEFAULT_DRAG_THRESHOLD = 0.0f
    }

    private var mAspectRatioEnabled = false
    private var mAspectRatio = 0
    private var mNormalStateColor = 0
    private var mWrongStateColor = 0
    private var mCorrectStateColor = 0
    private var mPathWidth = 0
    private var mDotNormalSize = 0
    private var mDotSelectedSize = 0
    private var mDotAnimationDuration = 0
    private var mPathEndAnimationDuration = 0

    private val dotPaint: Paint = Paint().apply {
        isAntiAlias = true
        isDither = true
    }
    private val pathPaint: Paint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private var doStarted:(()->Unit)? = null
    private var doProgress:((List<Dot>)->Unit)? = null
    private var doComplete:((List<Dot>)->Unit)? = null
    private var doCleared:(()->Unit)? = null

    // 已连接点的表
    private val mPattern = ArrayList<Dot>()

    /**
     * 查找表中我们正在绘制的图案的点。
     * 这将是完整模式的点，除非我们在动画，在这种情况下，
     * 我们使用它来保持点，我们正在绘制的动画。
     */
    private var mPatternDrawLookup: Array<BooleanArray>

    private var mInProgressX = -1f
    private var mInProgressY = -1f

    private var mPatternViewMode: Int = PatternViewMode.CORRECT
    private var mInputEnabled = true
    private var mInStealthMode = false
    private var mEnableHapticFeedback = true
    private var mPatternInProgress = false

    private var mViewWidth = 0f
    private var mViewHeight = 0f

    private val mCurrentPath = Path()
    private val mInvalidate = Rect()
    private val mTempInvalidateRect = Rect()

    private lateinit var mFastOutSlowInInterpolator: Interpolator
    private lateinit var mLinearOutSlowInInterpolator: Interpolator

    init{
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.PatternLockView)
        try {
            sDotCount = typedArray.getInt(
                R.styleable.PatternLockView_dotCount,
                DEFAULT_PATTERN_DOT_COUNT
            )
            mAspectRatioEnabled = typedArray.getBoolean(
                R.styleable.PatternLockView_aspectRatioEnabled,
                false
            )
            mAspectRatio = typedArray.getInt(
                R.styleable.PatternLockView_aspectRatio,
                AspectRatio.ASPECT_RATIO_SQUARE
            )
            mPathWidth = typedArray.getDimension(
                R.styleable.PatternLockView_pathWidth,
                getDimensionInPx(R.dimen.pattern_lock_path_width)
            ).toInt()
            mNormalStateColor = typedArray.getColor(
                R.styleable.PatternLockView_normalStateColor,
                getColor(R.color.white)
            )
            mCorrectStateColor = typedArray.getColor(
                R.styleable.PatternLockView_correctStateColor,
                getColor(R.color.white)
            )
            mWrongStateColor = typedArray.getColor(
                R.styleable.PatternLockView_wrongStateColor,
                getColor(R.color.pomegranate)
            )
            mDotNormalSize = typedArray.getDimension(
                R.styleable.PatternLockView_dotNormalSize,
                getDimensionInPx(R.dimen.pattern_lock_dot_size)
            ).toInt()
            mDotSelectedSize = typedArray.getDimension(
                R.styleable.PatternLockView_dotSelectedSize,
                getDimensionInPx(R.dimen.pattern_lock_dot_selected_size)
            ).toInt()
            mDotAnimationDuration = typedArray.getInt(
                R.styleable.PatternLockView_dotAnimationDuration,
                DEFAULT_DOT_ANIMATION_DURATION
            )
            mPathEndAnimationDuration = typedArray.getInt(
                R.styleable.PatternLockView_pathEndAnimationDuration,
                DEFAULT_PATH_END_ANIMATION_DURATION
            )
        } finally {
            typedArray.recycle()
        }

        // 图案总是对称的
        mPatternSize = sDotCount * sDotCount
        mPatternDrawLookup = Array(sDotCount) {
            BooleanArray(sDotCount)
        }

        mDotStates = Array(sDotCount){
            Array(sDotCount){
                DotState(mSize = mDotNormalSize.toFloat())
            }
        }

        initView()
    }

    private fun initView() {
        isClickable = true
        pathPaint.color = mNormalStateColor
        pathPaint.strokeWidth = mPathWidth.toFloat()

        if (!isInEditMode) {
            mFastOutSlowInInterpolator = loadInterpolator(android.R.interpolator.fast_out_slow_in)
            mLinearOutSlowInInterpolator = loadInterpolator(android.R.interpolator.linear_out_slow_in)
        }
    }

    private fun loadInterpolator(@InterpolatorRes inRes:Int) = AnimationUtils.loadInterpolator(context, inRes)
    private fun getDimensionInPx(@DimenRes dimenRes: Int): Float = context.resources.getDimension(dimenRes)
    private fun getColor(@ColorRes colorRes: Int): Int = ContextCompat.getColor(context, colorRes)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (!mAspectRatioEnabled) return

        val oldWidth: Int = resolveMeasured(widthMeasureSpec, suggestedMinimumWidth)
        val oldHeight: Int = resolveMeasured(heightMeasureSpec, suggestedMinimumHeight)

        val newWidth: Int
        val newHeight: Int
        when (mAspectRatio) {
            AspectRatio.ASPECT_RATIO_SQUARE -> {
                newHeight = min(oldWidth, oldHeight)
                newWidth = newHeight
            }
            AspectRatio.ASPECT_RATIO_WIDTH_BIAS -> {
                newWidth = oldWidth
                newHeight = min(oldWidth, oldHeight)
            }
            AspectRatio.ASPECT_RATIO_HEIGHT_BIAS -> {
                newWidth = min(oldWidth, oldHeight)
                newHeight = oldHeight
            }
            else -> throw IllegalStateException("Unknown aspect ratio")
        }
        setMeasuredDimension(newWidth, newHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val pattern: java.util.ArrayList<Dot> =
            mPattern
        val patternSize = pattern.size
        val drawLookupTable = mPatternDrawLookup

        if (mPatternViewMode == PatternViewMode.AUTO_DRAW) {
            val oneCycle: Int = (patternSize + 1) * MILLIS_PER_CIRCLE_ANIMATING
            val spotInCycle = (SystemClock.elapsedRealtime() - mAnimatingPeriodStart).toInt() % oneCycle
            val numCircles: Int = spotInCycle / MILLIS_PER_CIRCLE_ANIMATING
            clearPatternDrawLookup()
            for (i in 0 until numCircles) {
                val dot: Dot = pattern[i]
                drawLookupTable[dot.row][dot.column] = true
            }
            val needToUpdateInProgressPoint = (numCircles in 1 until patternSize)
            if (needToUpdateInProgressPoint) {
                val percentageOfNextCircle: Float =
                    ((spotInCycle % MILLIS_PER_CIRCLE_ANIMATING).toFloat()
                            / MILLIS_PER_CIRCLE_ANIMATING)
                val currentDot: Dot = pattern[numCircles - 1]
                val centerX = getCenterXForColumn(currentDot.column)
                val centerY = getCenterYForRow(currentDot.row)
                val nextDot: Dot = pattern[numCircles]
                val dx = (percentageOfNextCircle
                        * (getCenterXForColumn(nextDot.column) - centerX))
                val dy = (percentageOfNextCircle
                        * (getCenterYForRow(nextDot.row) - centerY))
                mInProgressX = centerX + dx
                mInProgressY = centerY + dy
            }
            invalidate()
        }

        val currentPath = mCurrentPath
        currentPath.rewind()

        // 画点
        for (column in 0 until sDotCount) {
            val centerY = getCenterYForRow(column)
            for (row in 0 until sDotCount) {
                val dotState = mDotStates[column][row]
                val centerX = getCenterXForColumn(row)
                val size = dotState.mSize * dotState.mScale
                val translationY = dotState.mTranslateY
                drawCircle(
                    canvas, centerX.toInt().toFloat(), centerY.toInt() + translationY,
                    size, drawLookupTable[column][row], dotState.mAlpha
                )
            }
        }

        // 绘制路径模式
        val drawPath = !mInStealthMode
        if (drawPath) {
            pathPaint.color = getCurrentColor(true)
            var anyCircles = false
            var lastX = 0f
            var lastY = 0f
            for (i in 0 until patternSize) {
                val dot:Dot = pattern[i]

                // 只绘制存储表中的部分
                if (!drawLookupTable[dot.row][dot.column]) {
                    break
                }
                anyCircles = true
                val centerX = getCenterXForColumn(dot.column)
                val centerY = getCenterYForRow(dot.row)
                if (i != 0) {
                    val state = mDotStates[dot.row][dot.column]
                    currentPath.rewind()
                    currentPath.moveTo(lastX, lastY)
                    if (state.mLineEndX != Float.MIN_VALUE
                        && state.mLineEndY != Float.MIN_VALUE
                    ) {
                        currentPath.lineTo(state.mLineEndX, state.mLineEndY)
                    } else {
                        currentPath.lineTo(centerX, centerY)
                    }
                    canvas.drawPath(currentPath, pathPaint)
                }
                lastX = centerX
                lastY = centerY
            }

            // 在进度部分最后绘制
            if ((mPatternInProgress || mPatternViewMode == PatternViewMode.AUTO_DRAW)
                && anyCircles
            ) {
                currentPath.rewind()
                currentPath.moveTo(lastX, lastY)
                currentPath.lineTo(mInProgressX, mInProgressY)
                pathPaint.alpha = (calculateLastSegmentAlpha(
                    mInProgressX, mInProgressY, lastX, lastY
                ) * 255f).toInt()
                canvas.drawPath(currentPath, pathPaint)
            }
        }
    }

    private fun resolveMeasured(measureSpec: Int, desired: Int): Int {
        val specSize = MeasureSpec.getSize(measureSpec)
        return when (MeasureSpec.getMode(measureSpec)) {
            MeasureSpec.UNSPECIFIED -> desired
            MeasureSpec.AT_MOST -> max(specSize, desired)
            MeasureSpec.EXACTLY -> specSize
            else -> specSize
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val adjustedWidth = width - paddingLeft - paddingRight
        mViewWidth = adjustedWidth / sDotCount.toFloat()
        val adjustedHeight = height - paddingTop - paddingBottom
        mViewHeight = adjustedHeight / sDotCount.toFloat()
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()!!
        return SavedState(
            superState,
            patternToString(this, mPattern),
            mPatternViewMode, mInputEnabled, mInStealthMode,
            mEnableHapticFeedback
        )
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState(state)
        val savedState = state as SavedState
        setPattern(
            PatternViewMode.CORRECT,
            stringToPattern(this, savedState.serializedPattern)
        )
        mPatternViewMode = savedState.displayMode
        mInputEnabled = savedState.isInputEnabled
        mInStealthMode = savedState.isInStealthMode
        mEnableHapticFeedback = savedState.isTactileFeedbackEnabled
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if ((context.getSystemService(
                Context.ACCESSIBILITY_SERVICE
            ) as AccessibilityManager).isTouchExplorationEnabled
        ) {
            val action = event.action
            when (action) {
                MotionEvent.ACTION_HOVER_ENTER -> event.action = MotionEvent.ACTION_DOWN
                MotionEvent.ACTION_HOVER_MOVE -> event.action = MotionEvent.ACTION_MOVE
                MotionEvent.ACTION_HOVER_EXIT -> event.action = MotionEvent.ACTION_UP
            }
            onTouchEvent(event)
            event.action = action
        }
        return super.onHoverEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!mInputEnabled || !isEnabled) return false

        return when (event.action) {
            MotionEvent.ACTION_DOWN -> handleActionDown(event)
            MotionEvent.ACTION_UP -> handleActionUp(event)
            MotionEvent.ACTION_MOVE -> handleActionMove(event)
            MotionEvent.ACTION_CANCEL -> {
                mPatternInProgress = false
                resetPattern()
                notifyPatternCleared()
                if (PROFILE_DRAWING&&mDrawingProfilingStarted) {
                    mDrawingProfilingStarted = false
                }
                true
            }
            else -> false
        }
    }

    /** SET */

    /**
     * 设置模式，不是等待用户输入。您可以将其用于帮助或演示目的。
     */
    fun setPattern(@PatternViewMode patternViewMode: Int, pattern: List<Dot>) {
        mPattern.clear()
        mPattern.addAll(pattern)
        clearPatternDrawLookup()
        for (dot in pattern) {
            mPatternDrawLookup[dot.row][dot.column] = true
        }
        setViewMode(patternViewMode)
    }

    /**
     * 设置当前模式的显示模式。结果：正确或错误。
     */
    fun setViewMode(@PatternViewMode patternViewMode: Int) {
        mPatternViewMode = patternViewMode
        if (patternViewMode == PatternViewMode.AUTO_DRAW) {
            check(mPattern.size != 0) {
                ("you must have a pattern to animate if you want to set the display mode to animate")
            }
            mAnimatingPeriodStart = SystemClock.elapsedRealtime()
            val first: Dot = mPattern[0]
            mInProgressX = getCenterXForColumn(first.column)
            mInProgressY = getCenterYForRow(first.row)
            clearPatternDrawLookup()
        }
        invalidate()
    }

    fun setDotCount(dotCount: Int) {
        sDotCount = dotCount
        mPatternSize = sDotCount * sDotCount
        mPattern.clear()
        mPatternDrawLookup = Array(sDotCount) {
            BooleanArray(sDotCount)
        }
        mDotStates = Array(sDotCount){
            Array(sDotCount){
                DotState(mSize = mDotNormalSize.toFloat())
            }
        }
        requestLayout()
        invalidate()
    }

    fun setAspectRatioEnabled(aspectRatioEnabled: Boolean) {
        mAspectRatioEnabled = aspectRatioEnabled
        requestLayout()
    }

    fun setAspectRatio(@AspectRatio aspectRatio: Int) {
        mAspectRatio = aspectRatio
        requestLayout()
    }

    fun setNormalStateColor(@ColorInt normalStateColor: Int) {
        mNormalStateColor = normalStateColor
    }

    fun setWrongStateColor(@ColorInt wrongStateColor: Int) {
        mWrongStateColor = wrongStateColor
    }

    fun setCorrectStateColor(@ColorInt correctStateColor: Int) {
        mCorrectStateColor = correctStateColor
    }

    fun setPathWidth(@Dimension pathWidth: Int) {
        mPathWidth = pathWidth
        initView()
        invalidate()
    }

    fun setDotNormalSize(@Dimension dotNormalSize: Int) {
        mDotNormalSize = dotNormalSize
        for (i in 0 until sDotCount) {
            for (j in 0 until sDotCount) {
                mDotStates[i][j] = DotState()
                mDotStates[i][j].mSize = mDotNormalSize.toFloat()
            }
        }
        invalidate()
    }

    fun setDotSelectedSize(@Dimension dotSelectedSize: Int) {
        mDotSelectedSize = dotSelectedSize
    }

    fun setDotAnimationDuration(dotAnimationDuration: Int) {
        mDotAnimationDuration = dotAnimationDuration
        invalidate()
    }

    fun setPathEndAnimationDuration(pathEndAnimationDuration: Int) {
        mPathEndAnimationDuration = pathEndAnimationDuration
    }

    /**
     * 设置视图是否处于隐身模式。如果是，当用户进入模式时，将没有可见的反馈(路径绘制、点动画等)
     */
    fun setInStealthMode(inStealthMode: Boolean) {
        mInStealthMode = inStealthMode
    }

    fun setTactileFeedbackEnabled(tactileFeedbackEnabled: Boolean) {
        mEnableHapticFeedback = tactileFeedbackEnabled
    }

    /**
     * 启用/禁用视图的任何用户输入。
     * 这对于在向用户显示任何消息时临时锁定视图非常有用，这样用户就不能以不想要的状态获得视图
     */
    fun setInputEnabled(inputEnabled: Boolean) {
        mInputEnabled = inputEnabled
    }

    fun setEnableHapticFeedback(enableHapticFeedback: Boolean) {
        mEnableHapticFeedback = enableHapticFeedback
    }

    fun setPatternLockListener(
        onStarted:(()->Unit)?=null, onCleared:(()->Unit)?=null,
        onProgress:((List<Dot>)->Unit)?=null, onComplete:((List<Dot>)->Unit)?=null
    ) {
        doStarted = onStarted
        doCleared = onCleared
        doProgress = onProgress
        doComplete = onComplete
    }

    fun clearPattern() {
        resetPattern()
    }

    private fun resetPattern() {
        mPattern.clear()
        clearPatternDrawLookup()
        mPatternViewMode = PatternViewMode.CORRECT
        invalidate()
    }

    private fun notifyPatternProgress() {
        sendAccessEvent(R.string.message_pattern_dot_added)
        notifyListenersProgress(mPattern)
    }

    private fun notifyPatternStarted() {
        sendAccessEvent(R.string.message_pattern_started)
        notifyListenersStarted()
    }

    private fun notifyPatternDetected() {
        sendAccessEvent(R.string.message_pattern_detected)
        notifyListenersComplete(mPattern)
    }

    private fun notifyPatternCleared() {
        sendAccessEvent(R.string.message_pattern_cleared)
        notifyListenersCleared()
    }

    private fun notifyListenersStarted() {
        doStarted?.invoke()
    }

    private fun notifyListenersProgress(pattern: List<Dot>) {
        doProgress?.invoke(pattern)
    }

    private fun notifyListenersComplete(pattern: List<Dot>) {
        doComplete?.invoke(pattern)
    }

    private fun notifyListenersCleared() {
        doCleared?.invoke()
    }

    private fun clearPatternDrawLookup() {
        for (column in 0 until sDotCount) {
            for (row in 0 until sDotCount) {
                mPatternDrawLookup[column][row] = false
            }
        }
    }

    /**
     * 确定点x、y是否会向当前模式添加新点(除了查找点之外，还会做出启发式选择，如根据当前模式填充空白)。
     *
     * @param x The x coordinate
     * @param y The y coordinate
     */
    private fun detectAndAddHit(x: Float, y: Float): Dot? {
        val dot: Dot? = checkForNewHit(x, y)
        if (dot != null) {
            // Check for gaps in existing pattern
            var fillInGapDot: Dot? = null
            val pattern:ArrayList<Dot> = mPattern
            if (pattern.isNotEmpty()) {
                val lastDot: Dot = pattern[pattern.size - 1]
                val dRow: Int = dot.row - lastDot.row
                val dColumn: Int = dot.column - lastDot.column
                val fillInRow: Int = if (abs(dRow) == 2 && abs(dColumn) != 1) {
                    lastDot.row + if (dRow > 0) 1 else -1
                }else lastDot.row
                val fillInColumn: Int = if (abs(dColumn) == 2 && abs(dRow) != 1) {
                    lastDot.column + if (dColumn > 0) 1 else -1
                }else lastDot.column
                fillInGapDot = Dot.of(fillInRow, fillInColumn)
            }
            if (fillInGapDot != null
                && !mPatternDrawLookup[fillInGapDot.row][fillInGapDot.column]
            ) {
                addCellToPattern(fillInGapDot)
            }
            addCellToPattern(dot)
            if (mEnableHapticFeedback) {
                performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                            or HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }
        }
        return dot
    }

    private fun addCellToPattern(newDot: Dot) {
        mPatternDrawLookup[newDot.row][newDot.column] = true
        mPattern.add(newDot)
        if (!mInStealthMode) {
            startDotSelectedAnimation(newDot)
        }
        notifyPatternProgress()
    }
    private fun startDotSelectedAnimation(dot: Dot) {
        val dotState = mDotStates[dot.row][dot.column]
        startSizeAnimation(mDotNormalSize.toFloat(),
            mDotSelectedSize.toFloat(),
            mDotAnimationDuration.toLong(),
            mLinearOutSlowInInterpolator,
            dotState,
            Runnable {
                startSizeAnimation(
                    mDotSelectedSize.toFloat(),
                    mDotNormalSize.toFloat(),
                    mDotAnimationDuration.toLong(),
                    mFastOutSlowInInterpolator,
                    dotState,
                    null
                )
            })
        startLineEndAnimation(
            dotState, mInProgressX, mInProgressY,
            getCenterXForColumn(dot.column), getCenterYForRow(dot.row)
        )
    }

    private fun startLineEndAnimation(
        state: DotState,
        startX: Float, startY: Float, targetX: Float,
        targetY: Float
    ) {
        state.mLineAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            addUpdateListener { animation ->
                val t = animation.animatedValue as Float
                state.mLineEndX = (1 - t) * startX + t * targetX
                state.mLineEndY = (1 - t) * startY + t * targetY
                invalidate()
            }
            doOnEnd { state.mLineAnimator = null }
            interpolator = mFastOutSlowInInterpolator
            duration = mPathEndAnimationDuration.toLong()
            start()
        }
    }

    private fun startSizeAnimation(
        start: Float, end: Float, duration: Long,
        interpolator: Interpolator, state: DotState,
        endRunnable: Runnable?
    ) {
        ValueAnimator.ofFloat(start, end).apply {
            addUpdateListener {
                state.mSize = (it.animatedValue as Float)
                invalidate()
            }
            if (endRunnable != null) {
                doOnEnd { endRunnable.run() }
            }
            this.interpolator = interpolator
            this.duration = duration
            start()
        }
    }

    /**
     * 辅助方法来将给定的x, y映射到它相应的单元格
     */
    private fun checkForNewHit(x: Float, y: Float): Dot? {
        val rowHit: Int = getRowHit(y)
        val columnHit: Int = getColumnHit(x)
        if (rowHit < 0 || columnHit < 0) {
            return null
        }
        return if (mPatternDrawLookup[rowHit][columnHit]) {
            null
        } else Dot.of(rowHit, columnHit)
    }

    /**
     * 来找到y坐标所在的行，没有则返回-1
     */
    private fun getRowHit(y: Float): Int {
        val squareHeight = mViewHeight
        val hitSize = squareHeight * mHitFactor
        val offset = paddingTop + (squareHeight - hitSize) / 2f
        for (i in 0 until sDotCount) {
            val hitTop = offset + squareHeight * i
            if (y >= hitTop && y <= hitTop + hitSize) {
                return i
            }
        }
        return -1
    }

    /**
     * 查找x所属的列，没有则返回-1
     */
    private fun getColumnHit(x: Float): Int {
        val squareWidth = mViewWidth
        val hitSize = squareWidth * mHitFactor
        val offset = paddingLeft + (squareWidth - hitSize) / 2f
        for (i in 0 until sDotCount) {
            val hitLeft = offset + squareWidth * i
            if (x >= hitLeft && x <= hitLeft + hitSize) {
                return i
            }
        }
        return -1
    }

    private fun handleActionMove(event: MotionEvent):Boolean {
        val radius = mPathWidth.toFloat()
        val historySize = event.historySize
        mTempInvalidateRect.setEmpty()
        var invalidateNow = false
        for (i in 0 until historySize + 1) {
            val x = if (i < historySize) event.getHistoricalX(i) else event.x
            val y = if (i < historySize) event.getHistoricalY(i) else event.y
            val hitDot: Dot? = detectAndAddHit(x, y)
            val patternSize = mPattern.size
            if (hitDot != null && patternSize == 1) {
                mPatternInProgress = true
                notifyPatternStarted()
            }
            // Note current x and y for rubber banding of in progress patterns
            val dx = abs(x - mInProgressX)
            val dy = abs(y - mInProgressY)
            if (dx > DEFAULT_DRAG_THRESHOLD || dy > DEFAULT_DRAG_THRESHOLD) {
                invalidateNow = true
            }
            if (mPatternInProgress && patternSize > 0) {
                val pattern: ArrayList<Dot> = mPattern
                val lastDot: Dot =
                    pattern[patternSize - 1]
                val lastCellCenterX: Float = getCenterXForColumn(lastDot.column)
                val lastCellCenterY: Float = getCenterYForRow(lastDot.row)

                // 调整绘制的线段从最后一个单元格到(x,y)。半径决定了线的宽度。
                var left = min(lastCellCenterX, x) - radius
                var right = max(lastCellCenterX, x) + radius
                var top = min(lastCellCenterY, y) - radius
                var bottom = max(lastCellCenterY, y) + radius

                // 在图案的新单元格和图案的前一个单元格之间无效
                if (hitDot != null) {
                    val width = mViewWidth * 0.5f
                    val height = mViewHeight * 0.5f
                    val hitCellCenterX: Float = getCenterXForColumn(hitDot.column)
                    val hitCellCenterY: Float = getCenterYForRow(hitDot.row)
                    left = min(hitCellCenterX - width, left)
                    right = max(hitCellCenterX + width, right)
                    top = min(hitCellCenterY - height, top)
                    bottom = max(hitCellCenterY + height, bottom)
                }

                // 在图案的最后一个单元格和前一个位置之间无效
                mTempInvalidateRect.union(
                    left.roundToInt(), top.roundToInt(),
                    right.roundToInt(), bottom.roundToInt()
                )
            }
        }
        mInProgressX = event.x
        mInProgressY = event.y

        // 为了保存更新，我们只在用户移动超过一定数量时才会失效。
        if (invalidateNow) {
            mInvalidate.union(mTempInvalidateRect)
            postInvalidate(mInvalidate.left,mInvalidate.top,mInvalidate.right,mInvalidate.bottom)
            mInvalidate.set(mTempInvalidateRect)
        }
        return true
    }

    private fun sendAccessEvent(resId: Int) {
        announceForAccessibility(context.getString(resId))
    }

    private fun handleActionUp(event: MotionEvent):Boolean {
        if (mPattern.isNotEmpty()) {
            mPatternInProgress = false
            cancelLineAnimations()
            notifyPatternDetected()
            invalidate()
        }
        if (PROFILE_DRAWING&&mDrawingProfilingStarted) {
            mDrawingProfilingStarted = false
        }
        return true
    }

    private fun cancelLineAnimations() {
        for (i in 0 until sDotCount) {
            for (j in 0 until sDotCount) {
                val state = mDotStates[i][j]
                state.mLineAnimator?.run {
                    cancel()
                    state.mLineEndX = Float.MIN_VALUE
                    state.mLineEndY = Float.MIN_VALUE
                }
            }
        }
    }

    private fun handleActionDown(event: MotionEvent):Boolean {
        resetPattern()
        val x = event.x
        val y = event.y
        val hitDot: Dot? = detectAndAddHit(x, y)
        if (hitDot != null) {
            mPatternInProgress = true
            mPatternViewMode = PatternViewMode.CORRECT
            notifyPatternStarted()
        } else {
            mPatternInProgress = false
            notifyPatternCleared()
        }
        if (hitDot != null) {
            val startX: Float = getCenterXForColumn(hitDot.column)
            val startY: Float = getCenterYForRow(hitDot.row)
            val widthOffset = mViewWidth / 2f
            val heightOffset = mViewHeight / 2f
            postInvalidate(
                (startX - widthOffset).toInt(),
                (startY - heightOffset).toInt(),
                (startX + widthOffset).toInt(),
                (startY + heightOffset).toInt()
            )
        }
        mInProgressX = x
        mInProgressY = y
        if (PROFILE_DRAWING&&!mDrawingProfilingStarted) {
            mDrawingProfilingStarted = true
        }
        return true
    }

    private fun getCenterXForColumn(column: Int): Float
        = (paddingLeft + column * mViewWidth + mViewWidth / 2f)

    private fun getCenterYForRow(row: Int): Float
        = (paddingTop + row * mViewHeight + mViewHeight / 2f)

    private fun calculateLastSegmentAlpha(x: Float, y: Float, lastX: Float, lastY: Float): Float {
        val diffX = x - lastX
        val diffY = y - lastY
        val dist = sqrt((diffX * diffX + diffY * diffY).toDouble()).toFloat()
        val fraction = dist / mViewWidth
        return min(1f, max(0f, (fraction - 0.3f) * 4f))
    }

    private fun getCurrentColor(partOfPattern: Boolean): Int {
        return if (!partOfPattern || mInStealthMode || mPatternInProgress) {
            mNormalStateColor
        } else if (mPatternViewMode == PatternViewMode.WRONG) {
            mWrongStateColor
        } else if (mPatternViewMode == PatternViewMode.CORRECT
            || mPatternViewMode == PatternViewMode.AUTO_DRAW
        ) {
            mCorrectStateColor
        } else {
            throw IllegalStateException("Unknown view mode $mPatternViewMode")
        }
    }

    private fun drawCircle(
        canvas: Canvas, centerX: Float, centerY: Float,
        size: Float, partOfPattern: Boolean, alpha: Float
    ) {
        dotPaint.color = getCurrentColor(partOfPattern)
        dotPaint.alpha = (alpha * 255).toInt()
        canvas.drawCircle(centerX, centerY, size / 2, dotPaint)
    }

    /** GET */

    fun getPattern(): List<Dot> = mPattern.clone() as List<Dot>

    @PatternViewMode
    fun getPatternViewMode(): Int = mPatternViewMode

    fun isInStealthMode(): Boolean = mInStealthMode

    fun isTactileFeedbackEnabled(): Boolean = mEnableHapticFeedback

    fun isInputEnabled(): Boolean = mInputEnabled

    fun isAspectRatioEnabled(): Boolean = mAspectRatioEnabled

    @AspectRatio
    fun getAspectRatio(): Int = mAspectRatio

    fun getNormalStateColor(): Int = mNormalStateColor

    fun getWrongStateColor(): Int = mWrongStateColor

    fun getCorrectStateColor(): Int = mCorrectStateColor

    fun getPathWidth(): Int = mPathWidth

    fun getDotNormalSize(): Int = mDotNormalSize

    fun getDotSelectedSize(): Int = mDotSelectedSize

    fun getPatternSize(): Int = mPatternSize

    fun getDotAnimationDuration(): Int = mDotAnimationDuration

    fun getPathEndAnimationDuration(): Int = mPathEndAnimationDuration

    val dotCount get() = sDotCount

}