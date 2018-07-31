package com.github.daynearby.javacvsamples

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.Toast
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
/**
 *
 * @date 2018-07-29
 * @author yangfujin
 * @describe
 * @email daynearby@hotmail.com
 */
class CamerView(context: Context) : TextureView(context) {

    val TAG = CamerView::class.java.simpleName
    var surfaceHolder: SurfaceHolder? = null
    var cameraManager: CameraManager? = null;
    var backGroundThread: HandlerThread? = null
    var mhandler: Handler? = null
    var mRatioWidth: Int = 720
    var mRatioHeight: Int = 1280
    var mVideoSize: Size? = null
    var mPreviewSize: Size? = null
    var mCameraDevice: CameraDevice? = null
    var previewSurface: Surface? = null

    /**
     * camera preview
     */
    var mPreviewBuilder: CaptureRequest.Builder? = null

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val mCameraOpenCloseLock: Semaphore = Semaphore(1)

    /**
     * current camera preview session
     */
    var mPreviewSession: CameraCaptureSession? = null

    private val mSurfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture?, w: Int, h: Int) {
            configureTransform(w, h)
        }

        override fun onSurfaceTextureUpdated(mSurfaceTexture: SurfaceTexture?) {
           // Log.d(TAG, "onSurfaceTextureUpdated " + mSurfaceTexture)
        }

        override fun onSurfaceTextureDestroyed(p0: SurfaceTexture?): Boolean {
            return true
        }

        override fun onSurfaceTextureAvailable(mSurfaceTexture: SurfaceTexture?, w: Int, h: Int) {
            openCamera(w, h)
        }
    }

    private val mStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(devic: CameraDevice?) {
            mCameraDevice = devic
            startPreview()
            mCameraOpenCloseLock.release()
            configureTransform(width, height)
        }


        override fun onDisconnected(cameraDevice: CameraDevice?) {
            mCameraOpenCloseLock.release()
            cameraDevice?.close()
        }

        override fun onError(cameraDevice: CameraDevice?, p1: Int) {
            mCameraOpenCloseLock.release();
            cameraDevice?.close();
            (context as Activity).finish()
        }
    }

    init {
        //surfaceHolder = holder;
        //this.camera =camera

        //camera.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        }
    }

    fun onResume() {
        startBackGroundThread()
        if (isAvailable) {
            openCamera(mRatioWidth, mRatioHeight)
        } else {
            surfaceTextureListener = mSurfaceTextureListener

        }
    }

    fun onPause() {
        previewSurface?.release()
        stopBackGroundThread()
    }

    private fun openCamera(width: Int, height: Int) {
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MICROSECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            val cameraId = cameraManager!!.cameraIdList[0]
            val cameraCharacteristics = cameraManager?.getCameraCharacteristics(cameraId)
            val map = cameraCharacteristics!!.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder::class.java))
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java),
                    mVideoSize!!.width, mVideoSize!!.height, mVideoSize!!)

            val orientation = resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                setAspectRatio(mPreviewSize!!.width, mPreviewSize!!.height)
            } else {
                setAspectRatio(mPreviewSize!!.height, mPreviewSize!!.width)
            }
            configureTransform(mPreviewSize!!.width, mPreviewSize!!.height)
            try {

                cameraManager?.openCamera(cameraId, mStateCallback, null)

            } catch (e: SecurityException) {
                e.printStackTrace()
            }

        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * configure textureView width and height
     * @param w
     * @param h
     */
    fun configureTransform(w: Int, h: Int) {
        if (null == mPreviewSize) {
            return
        }
        val rotation = (context as Activity).windowManager.defaultDisplay.rotation
        val matrix: Matrix = Matrix()
        val viewRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val bufferRect = RectF(0f, 0f, mPreviewSize!!.height.toFloat(), mPreviewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale: Float = Math.max((h / mPreviewSize!!.height).toFloat(), (w / mPreviewSize!!.width).toFloat())
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(90 * (rotation - 2).toFloat(), centerX, centerY)
        }
        setTransform(matrix)
    }

    /**
     * use 16 : 9 aspect ratio
     * @param choices
     * @return size
     */
    private fun chooseVideoSize(choices: Array<Size>): Size {
        val size = choices.filter { it.width == it.height * 4 / 3 && it.width <= 1080 }
        Log.d(TAG, "chooseVideoSize size = " + size)
        return if (size.size > 0) size[0] else Size(720, 1280)

    }

    /**
     * get  size support by a camera
     * @param choies
     * @param width
     * @param height
     * @param aspectRatio
     * @return Size
     */
    private fun chooseOptimalSize(choies: Array<Size>, width: Int, height: Int, aspectRatio: Size): Size {
        val w = aspectRatio.width
        val h = aspectRatio.height
        val size = choies.filter { it.height == it.width * h / w && it.width >= w && it.height >= h }
        Log.d(TAG, "chooseOptimalSize size " + size)
        return if (size.size > 0) size[0] else choies[0]

    }

    /**
     * camera start preview
     */
    private fun startPreview() {
        if (null == mCameraDevice || !isAvailable || null == mPreviewSize) {
            return
        }

        try {
            surfaceTexture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
            mPreviewBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            val surfaces = ArrayList<Surface>()
            previewSurface = Surface(surfaceTexture)
            surfaces.add(previewSurface!!)
            mPreviewBuilder!!.addTarget(previewSurface)

            //

            mCameraDevice!!.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(mCameraCaptureSession: CameraCaptureSession?) {
                    Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show()
                }

                override fun onConfigured(mCameraCaptureSession: CameraCaptureSession?) {
                    mPreviewSession = mCameraCaptureSession
                    updatePreview()
                }
            }, mhandler)

        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     *
     */
    fun updatePreview() {
        if (null == mCameraDevice) {
            return
        }
        try {
            mPreviewBuilder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            mPreviewSession?.setRepeatingRequest(mPreviewBuilder?.build(), null, mhandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun startBackGroundThread() {
        backGroundThread = HandlerThread("backgroundThread")
        backGroundThread!!.start()
        mhandler = Handler(backGroundThread!!.looper)

    }

    private fun stopBackGroundThread() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            backGroundThread?.quitSafely()
        } else {
            backGroundThread?.quit()
        }

        try {
            backGroundThread?.join()
            backGroundThread = null
            mhandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

    }

    /**
     * for auto fill (width : height)
     */
    public fun setAspectRatio(width: Int, height: Int) {
        if (width < 0 || height < 0) {
            throw IllegalArgumentException("Size cannot be negative.")
        }
        mRatioWidth = width
        mRatioHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width: Int = MeasureSpec.getSize(widthMeasureSpec)
        val height: Int = MeasureSpec.getSize(heightMeasureSpec)
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(mRatioWidth, mRatioHeight)
        } else if (width < height * mRatioWidth / mRatioHeight) {
            setMeasuredDimension(width, width * mRatioHeight / mRatioWidth)
        } else {
            setMeasuredDimension(height * mRatioWidth / mRatioHeight, height)
        }
    }
}
