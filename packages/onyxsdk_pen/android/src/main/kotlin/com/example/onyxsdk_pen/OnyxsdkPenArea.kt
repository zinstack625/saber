package com.example.onyxsdk_pen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import android.view.SurfaceView
import android.view.View
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.pen.NeoBrushPen
import com.onyx.android.sdk.pen.NeoCharcoalPenV2
import com.onyx.android.sdk.pen.NeoMarkerPen
import com.onyx.android.sdk.pen.NeoFountainPen
import com.onyx.android.sdk.pen.NeoPen
import com.onyx.android.sdk.api.device.epd.EpdController
import io.flutter.plugin.platform.PlatformView
import java.util.Timer
import java.util.TimerTask

internal class OnyxsdkPenArea(context: Context, id: Int, creationParams: Map<String?, Any?>?) : PlatformView {
    enum class StrokeStyle {
	FountainPen,
	Pen,
	Brush,
	Pencil,
	Marker
    }
    companion object {
        private val pointsToRedraw = 20
        var strokeWidth = 3f
	var strokeStyle = StrokeStyle.FountainPen
	var strokeColor = Color.YELLOW
    }

    private val touchHelper: TouchHelper by lazy { TouchHelper.create(view, callback) }

    private val view: SurfaceView = SurfaceView(context)
    override fun getView(): View {
        return view
    }

    private val paint: Paint = Paint()
    private val deviceMaxPressure = EpdController.getMaxTouchPressure()
    private var pointsSinceLastRedraw = 0

    private val currentStroke: ArrayList<TouchPoint> = ArrayList()

    private var refreshTimerTask: TimerTask? = null
    private val refreshDelayMs: Long by lazy { creationParams?.get("refreshDelayMs") as? Long ?: 100 }

    private val callback: RawInputCallback = object: RawInputCallback() {
        fun reset() {
	    touchHelper.setStrokeStyle(strokeStyleToOnyx(strokeStyle))
	    if (strokeStyle == StrokeStyle.Marker) {
		touchHelper.setStrokeWidth(strokeWidth * 2)
	    } else {
		touchHelper.setStrokeWidth(strokeWidth)
	    }
	    touchHelper.setStrokeColor(strokeColor)
	    
            currentStroke.clear()
            pointsSinceLastRedraw = 0
            drawPreview()
            refreshTimerTask?.cancel()
        }
        fun update(touchPoint: TouchPoint) {
            pointsSinceLastRedraw++
            if (pointsSinceLastRedraw < pointsToRedraw) return
            pointsSinceLastRedraw = 0

            currentStroke.add(touchPoint)

            drawPreview()
        }

        fun scheduleRefresh() {
            refreshTimerTask?.cancel()
            refreshTimerTask = object : TimerTask() {
                override fun run() {
                    touchHelper.setRawDrawingEnabled(false)
                    touchHelper.setRawDrawingEnabled(true)
                }
            }
            Timer().schedule(refreshTimerTask, refreshDelayMs)
        }


        override fun onBeginRawDrawing(b: Boolean, touchPoint: TouchPoint) {
            // begin of stylus data
            reset()
        }

        override fun onEndRawDrawing(b: Boolean, touchPoint: TouchPoint) {
            // end of stylus data
            reset()
            scheduleRefresh()
        }

        override fun onRawDrawingTouchPointMoveReceived(touchPoint: TouchPoint) {
            // stylus data during stylus moving
            update(touchPoint)
        }

        override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList) {
        }

        override fun onBeginRawErasing(b: Boolean, touchPoint: TouchPoint) {
            reset()
        }

        override fun onEndRawErasing(b: Boolean, touchPoint: TouchPoint) {
            reset()
            scheduleRefresh()
        }

        override fun onRawErasingTouchPointMoveReceived(touchPoint: TouchPoint) {
            update(touchPoint)
        }

        override fun onRawErasingTouchPointListReceived(touchPointList: TouchPointList) {
        }
    }

    private fun strokeStyleToOnyx(style: StrokeStyle): Int {
	return when (style) {
	    StrokeStyle.FountainPen -> TouchHelper.STROKE_STYLE_FOUNTAIN
	    StrokeStyle.Pen -> TouchHelper.STROKE_STYLE_CHARCOAL_V2
	    StrokeStyle.Brush -> TouchHelper.STROKE_STYLE_NEO_BRUSH
	    StrokeStyle.Pencil -> TouchHelper.STROKE_STYLE_PENCIL
	    StrokeStyle.Marker -> TouchHelper.STROKE_STYLE_MARKER
	}
    }

    fun drawPreview() {
	currentStroke.clear()
    }

    init {
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val limit = Rect()
            val exclude = emptyList<Rect>()
            view.getLocalVisibleRect(limit)
            touchHelper.setLimitRect(limit, exclude)

            touchHelper.setRawDrawingEnabled(false)
            touchHelper.setRawDrawingEnabled(true)
        }

        paint.setStrokeWidth(strokeWidth)
        paint.setColor(strokeColor)

        touchHelper.openRawDrawing()
        touchHelper.setRawDrawingEnabled(true)
        touchHelper.setRawDrawingRenderEnabled(false)

        drawPreview()
    }

    override fun dispose() {
        touchHelper.closeRawDrawing()
    }
}
