package com.test.jingmengsong.douyinloading

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.util.TypedValue
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator


/**
 * 业务名：
 * 功能说明：
 * 创建于：2018/9/12 on 17:19
 * 作者： jingmengsong
 * <p/>
 * 历史记录
 * 修改日期：
 * 修改人：
 * 修改内容：
 */
class DouYinLoadingView @JvmOverloads constructor(context: Context, attributeSet: AttributeSet) : View(context, attributeSet) {


    //默认值
    private val RADIUS = dp2px(6f)
    private val GAP = dp2px(0.8f)
    private val RTL_SCALE = 0.7f
    private val LTR_SCALR = 1.3f
    private val LEFT_COLOR = -0xbfc0
    private val RIGHT_COLOR = -0xff1112
    private val MIX_COLOR = Color.BLACK
    private val DURATION: Long = 350
    private val PAUSE_DUARTION: Long = 80
    private val SCALE_START_FRACTION = 0.2f
    private val SCALE_END_FRACTION = 0.8f


    //属性
    private var radius1: Float//初始时左小球的半径
    private var radius2: Float//初始时右小球的半径
    private var gap: Float//两小球直接的间隔
    private var rtlScale: Float //小球从右边移动到左边时大小倍数变化(rtl = right to left)
    private var ltrScale: Float //小球从左边移动到右边时大小倍数变化(ltr = left to right)
    private var color1: Int //初始左小球的颜色
    private var color2: Int //初始右小球的颜色
    private var mixColor: Int //两小球重叠的颜色
    private var duration: Long //小球一次移动的时长
    private var pauseDuration: Long // 小球一次移动后停顿的时长
    private var scaleStartFraction: Float//小球一次移动期间，进度在[0,scaleStartFraction]期间根据rtlScale、ltrScale逐渐缩放，取值为[0,0.5]
    private var scaleEndFraction: Float//小球一次移动期间，进度在[scaleEndFraction,1]期间逐渐恢复初始大小,取值为[0.5,1]

    //绘图
    private lateinit var paint1: Paint
    private lateinit var paint2: Paint
    private lateinit var mixPaint: Paint
    private lateinit var ltrPath: Path
    private lateinit var rtlPath: Path
    private lateinit var mixPath: Path
    private var distance: Float //小球一次移动距离（即两球圆点之间的距离）

    //动画
    private lateinit var anim: ValueAnimator
    private var fraction: Float = 0.0f //小球一次移动动画的进度百分比
    private var isAnimCanceled: Boolean = false
    private var isLtr: Boolean = true //true = 【初始左球】当前正【从左往右】移动,false = 【初始左球】当前正【从右往左】移动


    /**
     *  类创建实例时，进行的初始操作
     */
    init {

        //获取自定义属性
        val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.DouYinLoadingView)
        radius1 = typedArray.getDimension(R.styleable.DouYinLoadingView_radius1, RADIUS)
        radius2 = typedArray.getDimension(R.styleable.DouYinLoadingView_radius2, RADIUS)
        gap = typedArray.getDimension(R.styleable.DouYinLoadingView_gap, GAP)
        rtlScale = typedArray.getFloat(R.styleable.DouYinLoadingView_rtlScale, RTL_SCALE)
        ltrScale = typedArray.getFloat(R.styleable.DouYinLoadingView_ltrScale, LTR_SCALR)
        color1 = typedArray.getColor(R.styleable.DouYinLoadingView_color1, LEFT_COLOR)
        color2 = typedArray.getColor(R.styleable.DouYinLoadingView_color2, RIGHT_COLOR)
        mixColor = typedArray.getColor(R.styleable.DouYinLoadingView_mixColor, MIX_COLOR)
        duration = typedArray.getInt(R.styleable.DouYinLoadingView_duration, DURATION.toInt()).toLong()
        pauseDuration = typedArray.getInt(R.styleable.DouYinLoadingView_pauseDuration, PAUSE_DUARTION.toInt()).toLong()
        scaleStartFraction = typedArray.getFloat(R.styleable.DouYinLoadingView_scaleStartFraction, SCALE_START_FRACTION)
        scaleEndFraction = typedArray.getFloat(R.styleable.DouYinLoadingView_scaleEndFraction, SCALE_END_FRACTION)
        typedArray.recycle()


        checkAttr()

        distance = gap + radius1 + radius2

        initDraw()

        initAnim()


    }


    /**
     * 属性合法性 检查校正
     */
    private fun checkAttr() {
        radius1 = if (radius1 > 0) radius1 else RADIUS
        radius2 = if (radius2 > 0) radius2 else RADIUS
        gap = if (gap >= 0) gap else GAP
        rtlScale = if (rtlScale >= 0) rtlScale else RTL_SCALE
        ltrScale = if (ltrScale >= 0) ltrScale else LTR_SCALR
        duration = if (duration >= 0) duration else DURATION
        pauseDuration = if (pauseDuration >= 0) pauseDuration else PAUSE_DUARTION

        if (scaleStartFraction < 0 || scaleStartFraction > 0.5f) {
            scaleStartFraction = SCALE_START_FRACTION
        }
        if (scaleEndFraction < 0.5 || scaleEndFraction > 1) {
            scaleEndFraction = SCALE_END_FRACTION
        }

    }


    /**
     * 初始化 绘图工具
     */
    private fun initDraw() {

        paint1 = Paint(Paint.ANTI_ALIAS_FLAG)
        paint2 = Paint(Paint.ANTI_ALIAS_FLAG)
        mixPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint1.color = color1
        paint2.color = color2
        mixPaint.color = mixColor

        ltrPath = Path()
        rtlPath = Path()
        mixPath = Path()


    }

    /**
     *  初始化动画
     */
    private fun initAnim() {

        fraction = 0.0f
        stop()
        anim = ValueAnimator.ofFloat(0.0f, 1.0f)
        anim.duration = duration

        if (pauseDuration > 0) {

            //如果小球一次移动后不需要停顿， 即 pauseDuration = 0 那么就直接通过设置重复次数 来让动画无限循环，
            // 否则的话通过setStartDelay 来设置停顿时间，然后在监听的onAnimationEnd里重启动画，进而实现每次移动后小球能停顿一定时间
            anim.startDelay = pauseDuration
            anim.interpolator = AccelerateDecelerateInterpolator()

        } else {
            anim.repeatCount = ValueAnimator.INFINITE
            anim.repeatMode = ValueAnimator.RESTART
            anim.interpolator = LinearInterpolator()

        }

        anim.addUpdateListener { animation ->
            fraction = animation?.animatedFraction!!

            //重新绘制
            invalidate()
        }

        anim.addListener(object : Animator.AnimatorListener {
            override fun onAnimationRepeat(p0: Animator?) {
                isLtr = !isLtr
            }

            override fun onAnimationEnd(p0: Animator?) {
                if (!isAnimCanceled) {
                    anim.start()
                }
            }

            override fun onAnimationCancel(p0: Animator?) {
                isAnimCanceled = true
            }

            override fun onAnimationStart(p0: Animator?) {
                isLtr = !isLtr
            }

        })


    }


    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        Log.i("TAG", " measuredHeight的值是 $measuredHeight + measuredWidth 的值是 $measuredWidth")

        //初始化左边球 及 右边球 的 半径 及 画笔的颜色
        var ltrInitRadius: Float
        var rtlInitRadius: Float
        var ltrPaint: Paint
        var rtlPaint: Paint
        //动画过程中小球的Y坐标 不变， 始终为屏幕的高度的一半
        val centerY = measuredHeight / 2.0f

        //确定从左往右移动的是哪颗小球
        if (isLtr) {
            //初始左球 正从左往右移动
            ltrInitRadius = radius1
            rtlInitRadius = radius2
            ltrPaint = paint1
            rtlPaint = paint2
        } else {
            ltrInitRadius = radius2
            rtlInitRadius = radius1
            ltrPaint = paint2
            rtlPaint = paint1
        }

        //球的坐标的变化 重点是 measuredWidth 指示的是什么？onMeasure中定义的宽度的
        var ltrX = measuredWidth / 2.0f - distance / 2.0f
        ltrX += (distance * fraction) //当前从左往右的球的X坐标
        var rtlX = measuredWidth / 2.0f + distance / 2.0f
        rtlX -= (distance * fraction) //当前从右往左的球的X坐标

        //计算小球移动中的大小变化
        var ltrBallRadius: Float
        var rtlBallRadius: Float

        when {
            fraction <= scaleStartFraction -> { //动画进度[0,scaleStartFraction]时，球大小由1倍逐渐缩放至ltrScale/rtlScale倍
                val scaleFraction = 1.0f / scaleStartFraction * fraction //百分比转换 [0,scaleStartFraction]] -> [0,1]
                ltrBallRadius = ltrInitRadius * (1 + (ltrScale - 1) * scaleFraction)
                rtlBallRadius = rtlInitRadius * (1 + (rtlScale - 1) * scaleFraction)
            }
            fraction >= scaleEndFraction -> { //动画进度[scaleEndFraction,1]，球大小由ltrScale/rtlScale倍逐渐恢复至1倍
                val scaleFraction = (fraction - 1) / (scaleEndFraction - 1) //百分比转换，[scaleEndFraction,1] -> [1,0]
                ltrBallRadius = ltrInitRadius * (1 + (ltrScale - 1) * scaleFraction)
                rtlBallRadius = rtlInitRadius * (1 + (rtlScale - 1) * scaleFraction)
            }
            else -> { //动画进度[scaleStartFraction,scaleEndFraction]，球保持缩放后的大小
                ltrBallRadius = ltrInitRadius * ltrScale
                rtlBallRadius = rtlInitRadius * rtlScale
            }
        }

        //每次绘制时 先重置路径
        ltrPath.reset()
        //为路径添加圆形轮廓   最后的那个参数 是方向 cw 是顺时针 ccw  是逆时针
        ltrPath.addCircle(ltrX, centerY, ltrBallRadius, Path.Direction.CW)

        rtlPath.reset()
        rtlPath.addCircle(rtlX, centerY, rtlBallRadius, Path.Direction.CW)
        mixPath.op(ltrPath, rtlPath, Path.Op.INTERSECT)


        //绘制路径
        canvas?.drawPath(ltrPath, ltrPaint)
        canvas?.drawPath(rtlPath, rtlPaint)
        canvas?.drawPath(mixPath, mixPaint)
    }


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        var wSize = MeasureSpec.getSize(widthMeasureSpec)
        val wMode = MeasureSpec.getMode(widthMeasureSpec)
        var hSize = MeasureSpec.getSize(heightMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)

        //WRAP_CONTENT时控件大小为最大可能的大小,保证显示的下
        var maxScale = Math.max(rtlScale, ltrScale)
        maxScale = Math.max(maxScale, 1f)

        if (wMode != View.MeasureSpec.EXACTLY) {
            wSize = (gap + (2 * radius1 + 2 * radius2) * maxScale + dp2px(1f)).toInt()  //宽度= 间隙 + 2球直径*最大比例 + 1dp
        }
        if (hMode != View.MeasureSpec.EXACTLY) {

            hSize = (2f * Math.max(radius1, radius2) * maxScale + dp2px(1f)).toInt() // 高度= 1球直径*最大比例 + 1dp
        }
        setMeasuredDimension(wSize, hSize)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }


    //公开方法

    /**
     * 停止动画
     */
    fun stop() {

        if (::anim.isInitialized) {
            anim.cancel()
//            anim = null
        }

    }

    /**
     * 开始动画
     */
    fun start() {
        if (anim == null) {
            initAnim()
        }
        if (anim.isRunning) {
            anim.cancel()
        }

        post {
            isAnimCanceled = false
            isLtr = false
            anim.start()
        }
    }

    /**
     * 设置小球半径和两小球间隔
     */
    fun setRadius(radius1: Float, radius2: Float, gap: Float) {
        stop()
        this.radius1 = radius1
        this.radius2 = radius2
        this.gap = gap
        checkAttr()
        distance = gap + radius1 + radius2
        requestLayout() //可能涉及宽高变化
    }


    /**
     * 设置小球颜色和重叠处颜色
     */
    fun setColors(color1: Int, color2: Int, mixColor: Int) {
        this.color1 = color1
        this.color2 = color2
        this.mixColor = color2
        checkAttr()
        paint1.color = color1
        paint2.color = color2
        mixPaint.color = mixColor
        invalidate()
    }

    /**
     * 设置动画时长
     *
     * @param duration      [.duration]
     * @param pauseDuration [.pauseDuration]
     */
    fun setDuration(duration: Long, pauseDuration: Long) {
        this.duration = duration
        this.pauseDuration = pauseDuration
        checkAttr()
        initAnim()
    }

    /**
     * 设置移动过程中缩放倍数
     *
     * @param ltrScale [.ltrScale]
     * @param rtlScale [.rtlScale]
     */
    fun setScales(ltrScale: Float, rtlScale: Float) {
        stop()
        this.ltrScale = ltrScale
        this.rtlScale = rtlScale
        checkAttr()
        requestLayout() //可能涉及宽高变化
    }

    /**
     * 设置缩放开始、结束的范围
     *
     * @param scaleStartFraction [.scaleStartFraction]
     * @param scaleEndFraction   [.scaleEndFraction]
     */
    fun setStartEndFraction(scaleStartFraction: Float, scaleEndFraction: Float) {
        this.scaleStartFraction = scaleStartFraction
        this.scaleEndFraction = scaleEndFraction
        checkAttr()
        invalidate()
    }


    fun getRadius1(): Float {
        return radius1
    }

    fun getRadius2(): Float {
        return radius2
    }

    fun getGap(): Float {
        return gap
    }

    fun getRtlScale(): Float {
        return rtlScale
    }

    fun getLtrScale(): Float {
        return ltrScale
    }

    fun getColor1(): Int {
        return color1
    }

    fun getColor2(): Int {
        return color2
    }

    fun getMixColor(): Int {
        return mixColor
    }

    fun getDuration(): Long {
        return duration
    }

    fun getPauseDuration(): Long {
        return pauseDuration
    }

    fun getScaleStartFraction(): Float {
        return scaleStartFraction
    }

    fun getScaleEndFraction(): Float {
        return scaleEndFraction
    }


    private fun dp2px(dp: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
    }

}