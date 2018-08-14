package com.github.daynearby.javacvsamples

import android.content.Context
import android.content.res.Configuration
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import java.io.IOException
import java.util.*
import java.util.concurrent.Semaphore
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.view.WindowManager
import android.graphics.Rect
import kotlin.collections.ArrayList


/**
 *
 * @date 2018-07-29
 * @author yangfujin
 * @describe
 * @email daynearby@hotmail.com
 */
class CameraView(context: Context, camera: Camera?, cameraId: Int) : GLSurfaceView(context), SurfaceHolder.Callback,
        Camera.PreviewCallback, GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener, Camera.AutoFocusCallback {


    val TAG = CameraView::class.java.simpleName
    var camera: Camera? = null
    var cameraId: Int = -1
    var backGroundThread: HandlerThread? = null
    var mhandler: Handler? = null
    var mRatioWidth: Int = 9
    var mRatioHeight: Int = 16
    var mCameraDevice: CameraDevice? = null
    // var previewSurface: Surface? = null
    var isPreviewOn: Boolean = false
    val frameRate = 30//frame rate

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

    init {
        this.camera = camera
        this.cameraId = cameraId
        val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        mRatioWidth = display!!.width
        mRatioHeight = display.height
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
        holder.addCallback(this)
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        this.camera!!.setPreviewCallback(this)

    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        try {
            stopPreview()
            camera?.setPreviewDisplay(holder)
        } catch (e: IOException) {
            camera?.release()
            camera = null
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, w: Int, h: Int) {
        stopPreview()

        val cameraParams = camera?.parameters
        val sizes = cameraParams?.supportedPreviewSizes
        var previewWidth = 0
        var previewHeight = 0
        Collections.sort(sizes, object : Comparator<Camera.Size> {
            override fun compare(a: Camera.Size, b: Camera.Size): Int {
                return a.width * a.height - b.width * b.height
            }
        })
        val sizeList = sizes?.filter { it?.width == (it.height * 16 / 9) && it.width <= 1080 }
        if (sizeList!!.isNotEmpty()) {
            previewHeight = sizeList.get(0).height
            previewWidth = sizeList.get(0).width
        }

        Log.d(TAG, "changed to supported resolution :${previewWidth} x ${previewHeight}")


        cameraParams.setPreviewSize(previewWidth, previewHeight)
        cameraParams.previewFrameRate = frameRate
        val focusMode = cameraParams.supportedFocusModes.filter { Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO.equals(it) }.isNotEmpty()
        if (focusMode) {
            cameraParams.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
        }
        val orientation = resources.configuration.orientation

        //onOrientationChanged(orientation, cameraParams)
        camera?.setDisplayOrientation(90)
        camera?.parameters = cameraParams

        //init layout w * h
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setAspectRatio(previewWidth, previewHeight)
        } else {
            setAspectRatio(previewWidth, previewHeight)
        }

        try {
            camera?.setPreviewDisplay(holder)
            camera?.setPreviewCallback(this)
            startPreview()
        } catch (e: Exception) {
            Log.e(TAG, "Could not set preview display in surfaceChanged")
        }
    }

    override fun surfaceDestroyed(holder: android.view.SurfaceHolder?) {
        try {
            holder?.addCallback(null)
            camera?.setPreviewCallback(null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onSurfaceChanged(p0: GL10?, p1: Int, p2: Int) {

    }

    override fun onSurfaceCreated(p0: GL10?, p1: EGLConfig?) {

    }

    override fun onDrawFrame(p0: GL10?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onFrameAvailable(p0: SurfaceTexture?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {

    }

    override fun onAutoFocus(focus: Boolean, camera: Camera?) {
        Log.d(TAG, "onAutoFocus focus:${focus}")
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (MotionEvent.ACTION_DOWN == event!!.action) {
            val x = event.x
            val y = event.y
            val touchMajor = event.touchMajor
            val toucgMinor = event.touchMinor
            val touchRect = Rect((x - touchMajor / 2).toInt(), (y - toucgMinor / 2).toInt(),
                    (x + toucgMinor / 2).toInt(), (y + toucgMinor / 2).toInt())
            focusAreaRect(touchRect)
        }
        return super.onTouchEvent(event)
    }

    private fun focusAreaRect(touchRect: Rect): Boolean {
        val cameraParams = camera?.parameters
        if (cameraParams!!.maxNumFocusAreas == 0) {
            Log.d(TAG,"maxNumFocusAreas == 0")
            return false
        }

        val focusArea = Rect(touchRect.left * 2000 / width - 1000,
                touchRect.top * 2000 / height - 1000,
                touchRect.right * 2000 / width - 1000,
                touchRect.bottom * 2000 / height - 1000)
        val focusAreas = ArrayList<Camera.Area>()
        focusAreas.add(Camera.Area(focusArea, 1000))
        cameraParams.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
        cameraParams.focusAreas = focusAreas
        camera?.parameters = cameraParams
        camera?.autoFocus(this)

        return true
    }

    /**
     * camera start preview
     */
    private fun startPreview() {
        if (!isPreviewOn) {
            isPreviewOn = true
            camera?.startPreview()

        }
    }

    public fun stopPreview() {
        if (isPreviewOn) {
            isPreviewOn = false
            camera?.stopPreview()
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
     * set camera orientation
     * @param orientation current orientation
     * @param cameraParams cameraParams
     */
    private fun onOrientationChanged(orientation: Int, cameraParams: Camera.Parameters) {
        if (orientation == Configuration.ORIENTATION_UNDEFINED) return;
        val info = Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        val tempOrientation = (orientation + 45) / 90 * 90;
        var rotation = 0
        if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
            rotation = (info.orientation - tempOrientation + 360) % 360;
        } else {  // back-facing camera
            rotation = (info.orientation + tempOrientation) % 360;
        }
        cameraParams.setRotation(90);
    }

    /**
     * for auto fill (width : height)
     */
    public fun setAspectRatio(width: Int, height: Int) {
        if (width < 0 || height < 0) {
            throw IllegalArgumentException("Size cannot be negative.")
        }
        //mRatioWidth = width
        // mRatioHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width: Int = MeasureSpec.getSize(widthMeasureSpec)
        val height: Int = MeasureSpec.getSize(heightMeasureSpec)
        /* if (0 == mRatioWidth || 0 == mRatioHeight) {
             setMeasuredDimension(mRatioWidth, mRatioHeight)
         } else */if (width < height * mRatioWidth / mRatioHeight) {
            setMeasuredDimension(width, width * mRatioHeight / mRatioWidth)
        } else {
            setMeasuredDimension(height * mRatioWidth / mRatioHeight, height)
        }
    }

}
