package amitkumar.io.facedetector

import amitkumar.io.facedetector.Frame
import amitkumar.io.facedetector.LensFacing
import android.graphics.RectF
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.annotation.GuardedBy
import com.google.android.gms.common.util.concurrent.HandlerExecutor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import husaynhakeem.io.facedetector.FaceBoundsOverlay
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceDetector(private val faceBoundsOverlay: FaceBoundsOverlay) {

    private val mlkitFaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(MIN_FACE_SIZE)
            .enableTracking()
            .build()
    )
    private var onFaceDetectionResultListener: OnFaceDetectionResultListener? = null
    private lateinit var faceDetectionExecutor: ExecutorService
    private val mainExecutor = HandlerExecutor(Looper.getMainLooper())
    private val lock = Object()

    @GuardedBy("lock")
    private var isProcessing = false

    init {
        faceBoundsOverlay.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View?) {
                faceDetectionExecutor = Executors.newSingleThreadExecutor()
            }

            override fun onViewDetachedFromWindow(view: View?) {
                if (::faceDetectionExecutor.isInitialized) {
                    faceDetectionExecutor.shutdown()
                }
            }
        })
    }
    fun setonFaceDetectionFailureListener(listener: OnFaceDetectionResultListener) {
        onFaceDetectionResultListener = listener
    }
    fun process(frame: Frame) {
        synchronized(lock) {
            if (!isProcessing) {
                isProcessing = true
                if (!::faceDetectionExecutor.isInitialized) {
                    val exception =
                        IllegalStateException("Cannot run face detection. Make sure the face " +
                                "bounds overlay is attached to the current window.")
                    onError(exception)
                } else {
                    faceDetectionExecutor.execute { frame.detectFaces() }
                }
            }
        }
    }

    private fun Frame.detectFaces() {
        val data = data ?: return
        val inputImage = InputImage.fromByteArray(data, size.width, size.height, rotation, format)
        mlkitFaceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                synchronized(lock) {
                    isProcessing = false
                }
                val faceBounds = faces.map { face -> face.toFaceBounds(this) }
                mainExecutor.execute { faceBoundsOverlay.updateFaces(faceBounds) }
            }
            .addOnFailureListener { exception ->
                synchronized(lock) {
                    isProcessing = false
                }
                onError(exception)
            }
    }
    private fun Face.toFaceBounds(frame: Frame): FaceBounds {
        val reverseDimens = frame.rotation == 90 || frame.rotation == 270
        val width = if (reverseDimens) frame.size.height else frame.size.width
        val height = if (reverseDimens) frame.size.width else frame.size.height
        val scaleX = faceBoundsOverlay.width.toFloat() / width
        val scaleY = faceBoundsOverlay.height.toFloat() / height
        val isFrontLens = frame.lensFacing == LensFacing.FRONT
        val flippedLeft = if (isFrontLens) width - boundingBox.right else boundingBox.left
        val flippedRight = if (isFrontLens) width - boundingBox.left else boundingBox.right

        val scaledLeft = scaleX * flippedLeft
        val scaledTop = scaleY * boundingBox.top
        val scaledRight = scaleX * flippedRight
        val scaledBottom = scaleY * boundingBox.bottom
        val scaledBoundingBox = RectF(scaledLeft, scaledTop, scaledRight, scaledBottom)
        return FaceBounds(
            trackingId,
            scaledBoundingBox
        )
    }

    private fun onError(exception: Exception) {
        onFaceDetectionResultListener?.onFailure(exception)
        Log.e(TAG, "An error occurred while running a face detection", exception)
    }

    interface OnFaceDetectionResultListener {
        fun onSuccess(faceBounds: List<FaceBounds>) {}
        fun onFailure(exception: Exception) {}
    }

    companion object {
        private const val TAG = "FaceDetector"
        private const val MIN_FACE_SIZE = 0.15F
    }
}