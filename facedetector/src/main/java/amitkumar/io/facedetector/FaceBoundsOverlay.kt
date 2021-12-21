package amitkumar.io.facedetector

import android.R
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class FaceBoundsOverlay @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) :
    View(ctx, attrs) {

    private val facesBounds = mutableListOf<FaceBounds>()
    private val anchorPaint = Paint()
    private val idPaint = Paint()
    private val boundsPaint = Paint()

    init {
        anchorPaint.color = ContextCompat.getColor(context, R.color.darker_gray)

        idPaint.color = ContextCompat.getColor(context, R.color.darker_gray)
        idPaint.textSize = 40f

        boundsPaint.style = Paint.Style.STROKE
        boundsPaint.color = ContextCompat.getColor(context, R.color.darker_gray)
        boundsPaint.strokeWidth = 4f
    }

    internal fun updateFaces(bounds: List<FaceBounds>) {
        facesBounds.clear()
        facesBounds.addAll(bounds)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        facesBounds.forEach { faceBounds ->
            canvas.drawAnchor(faceBounds.box.center())
            canvas.drawId(faceBounds.id.toString(), faceBounds.box.center())
            canvas.drawBounds(faceBounds.box)
        }
    }

    /** Draws an anchor (dot) at the center of a face. */
    private fun Canvas.drawAnchor(center: PointF) {
        drawCircle(center.x, center.y, ANCHOR_RADIUS, anchorPaint)
    }

    /** Draws (Writes) the face's id. */
    private fun Canvas.drawId(faceId: String, center: PointF) {
        drawText("face id $faceId", center.x - ID_OFFSET, center.y + ID_OFFSET, idPaint)
    }

    /** Draws bounds around a face as a rectangle. */
    private fun Canvas.drawBounds(box: RectF) {
        drawRect(box, boundsPaint)
    }

    private fun RectF.center(): PointF {
        val centerX = left + (right - left) / 2
        val centerY = top + (bottom - top) / 2
        return PointF(centerX, centerY)
    }

    companion object {
        private const val ANCHOR_RADIUS = 10f
        private const val ID_OFFSET = 50f
    }
}