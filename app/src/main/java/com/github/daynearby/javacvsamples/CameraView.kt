package com.github.daynearby.javacvsamples

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.ExifInterface
import android.media.MediaRecorder
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
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
    var previewHeight: Int = 9
    var previewWidth: Int = 16
    val frameWidth = 360
    val frameHeight = 640
    var mCameraDevice: CameraDevice? = null
    // var previewSurface: Surface? = null
    var isPreviewOn: Boolean = false
    val frameRate = 30//frame rate
    var focus: Boolean = false
    val sampleAudioRateInHz = 44100
    var samplesIndex = 0

    var samples: Array<ShortBuffer>? = null
    var yuvFrame: Frame? = null
    var recorder: FFmpegFrameRecorder? = null
    var audioRecord: AudioRecord? = null
    //var filter: FFmpegFrameFilter? = null
    var audioThread: Thread? = null
    val ffmpeg_link = "/mnt/sdcard/stream.mp4"//video file path
    var startTime = 0L
    var recording = false
    //var addFilter = false
    var runAudioThread: Boolean = false
    var timestamps: LongArray? = null
    var imagesIndex = 0
    var smaplesIndex = 0
    var images: Array<Frame>? = null
    val videoQuality = 3000.0
    var frameLinkList:LinkedList<Frame>?= null

    companion object {
        val RECORD_LENGHT = 0//30 seconds
    }

    /**
     * camera preview
     */
    var mPreviewBuilder: CaptureRequest.Builder? = null


    init {
        this.camera = camera
        this.cameraId = cameraId
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
        holder.addCallback(this)
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        this.camera!!.setPreviewCallback(this)

        frameLinkList = LinkedList<Frame>()
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
        Collections.sort(sizes, object : Comparator<Camera.Size> {
            override fun compare(a: Camera.Size, b: Camera.Size): Int {
                return a.width * a.height - b.width * b.height

            }
        })
        val sizeList = sizes?.filter { it?.width == (it.height * 16 / 9) && it.height >= 1080 }
        if (sizeList!!.isNotEmpty()) {
            previewHeight = sizeList.last().height
            previewWidth = sizeList.last().width
        }

        Log.d(TAG, "changed to supported resolution :${previewWidth} x ${previewHeight}")


        cameraParams.setPreviewSize(previewWidth, previewHeight)
        cameraParams.previewFrameRate = frameRate
        val focusMode = cameraParams.supportedFocusModes.filter { Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO.equals(it) }.isNotEmpty()
        if (focusMode) {
            cameraParams.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
        }

        camera?.parameters = cameraParams

        //init layout w * h
        /*if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setAspectRatio(previewWidth, previewHeight)
        } else {*/
        setAspectRatio(previewWidth, previewHeight)
        //}

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
        Log.d(TAG, "onFrameAvailable")
    }


    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        //record video data
        if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            startTime = System.currentTimeMillis();
            return
        }
        if (RECORD_LENGHT > 0) {
            val i = imagesIndex++ % images!!.size
            yuvFrame = images!!.get(i)
            timestamps!!.set(i, 1000 * (System.currentTimeMillis() - startTime))
        }

        /*get video data*/
        if (null != yuvFrame && recording) {
            (yuvFrame!!.image?.get(0)?.position(0) as ByteBuffer).put(data)

            if (RECORD_LENGHT <= 0) {

                try {
                    Log.v(TAG, "writing frame")
                    val t = 1000 * (System.currentTimeMillis() - startTime)
                    if (t > recorder!!.timestamp) {

                        yuvFrame?.timestamp = t
                        recorder?.timestamp = t
                    }
                    frameLinkList?.offer(yuvFrame)

                    /*if (addFilter) {
                        filter?.push(yuvFrame)
                        var frame2: Frame?
                        do {
                            frame2 = filter!!.pull()
                            if (frame2 == null) {
                                Log.v(TAG, "onPreviewFrame frame2 == null")
                                break
                            }
                            Log.d(TAG, "frame2 ${frame2.image}")

                            recorder?.record(frame2, filter!!.pixelFormat)

                        } while (true)

                    } else {*/

                        recorder?.record(yuvFrame)
                    //}
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onAutoFocus(focus: Boolean, camera: Camera?) {
        Log.d(TAG, "onAutoFocus focus:${focus}")
        this.focus = focus
    }

    fun startRecording() {
        initRecorder()

        try {
            recorder?.start()
            startTime = System.currentTimeMillis()
            recording = true
            runAudioThread = true
            audioThread?.start()
            /*if (addFilter) {
                filter?.start()
            }*/
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    fun stopRecording() {
        runAudioThread = false

        try {
            audioThread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
            Thread.currentThread().interrupt()
            return
        }


        if (recorder != null && recording) {
            if (RECORD_LENGHT > 0) {
                Log.v(TAG, "writing frames ")
                try {
                    var firstIndex = imagesIndex % samples!!.size
                    var lastIndex = (imagesIndex - 1) % samples!!.size
                    if (imagesIndex <= images!!.size) {
                        firstIndex = 0
                        lastIndex = imagesIndex - 1
                    }
                    startTime = (timestamps!!.get(lastIndex) - RECORD_LENGHT * 1000000L)
                    if (startTime < 0) {
                        startTime = 0
                    }

                    if (lastIndex < firstIndex) {
                        lastIndex += images!!.size
                    }

                    for (i in firstIndex..lastIndex) {
                        val t = timestamps!!.get(i % timestamps!!.size) - startTime
                        if (t >= 0) {
                            recorder?.timestamp = t
                        }
                        recorder?.record(images!!.get(i % images!!.size))
                    }
                    firstIndex = samplesIndex % samples!!.size
                    lastIndex = (samplesIndex - 1) % samples!!.size
                    if (samplesIndex <= samples!!.size) {
                        firstIndex = 0
                        lastIndex = samplesIndex - 1

                    }
                    if (lastIndex < firstIndex) {
                        lastIndex += samples!!.size
                    }

                    for (i in firstIndex..lastIndex) {
                        recorder?.recordSamples(samples!!.get(i % samples!!.size))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "save frame error")
                    e.printStackTrace()
                }
            }
            recording = false
            Log.v(TAG, "finish recording ,call stop and release on recorder")

            try {
                recorder?.stop()
                recorder?.release()
                //filter?.stop()
                //filter?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            recorder = null
        }
        audioThread = null
    }

    /**
     * initialize ffmpeg_recorder
     */
    private fun initRecorder() {
        Log.w(TAG, "init ffmpeg recorder")
        if (RECORD_LENGHT > 0) {
            imagesIndex = 0

            images = Array(RECORD_LENGHT * frameRate,
                    { Frame(frameWidth, frameHeight, Frame.DEPTH_UBYTE, 2) })

            timestamps = LongArray(images!!.size, { -1 })


        } else if (yuvFrame == null) {
            //width = getWidth / height = getHeight
            yuvFrame = Frame(previewWidth, previewHeight, Frame.DEPTH_UBYTE, 2)
        }
        Log.v(TAG, "path = ${ffmpeg_link}")

        recorder = FFmpegFrameRecorder(ffmpeg_link, previewWidth, previewHeight, 1)
        recorder?.format = "mp4"
        //设置视频元数据，实现旋转90°
        recorder?.setVideoMetadata("rotate","90")
        recorder?.frameRate = frameRate.toDouble()
       // recorder?.videoQuality = videoQuality
        recorder?.sampleRate = sampleAudioRateInHz
        // Here is the link for a list: https://ffmpeg.org/ffmpeg-filters.html
        //val filterString = "transpose=1:portrait"
        //filter = FFmpegFrameFilter(filterString, previewWidth, previewHeight)
       // filter?.pixelFormat = avutil.AV_PIX_FMT_NV21
        Log.w(TAG, "recorder initilize success")
        audioThread = Thread(AudioRunnable())
        runAudioThread = true

    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (MotionEvent.ACTION_DOWN == event?.action) {
            val x = event.x
            val y = event.y
            val touchMajor = event.touchMajor
            val toucgMinor = event.touchMinor
            val touchRect = Rect((x - touchMajor / 2).toInt(), (y - toucgMinor / 2).toInt(),
                    (x + toucgMinor / 2).toInt(), (y + toucgMinor / 2).toInt())
            return focusAreaRect(touchRect) || super.onTouchEvent(event)
        }
        return super.onTouchEvent(event)
    }

    private fun focusAreaRect(touchRect: Rect): Boolean {
        val cameraParams = camera?.parameters
        if (cameraParams!!.maxNumFocusAreas == 0) {
            Log.d(TAG, "maxNumFocusAreas == 0")
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
        //Unnecessary
        this.camera!!.autoFocus(this)

        return true
    }

    /**
     * camera start preview
     */
    public fun startPreview() {
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
     */
    private fun onOrientationChanged(orientation: Int): Int {
        if (orientation == Configuration.ORIENTATION_UNDEFINED) return 90;
        val info = Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        val tempOrientation = (orientation + 45) / 90 * 90;
        if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
            return (info.orientation - tempOrientation + 360) % 360;
        } else {  // back-facing camera
            return (info.orientation + tempOrientation) % 360;
        }

    }

    /**
     * for auto fill (width : height)
     * camera preview size,camera default orientation Configuration.ORIENTATION_LANDSCAPE
     * @param width
     * @param height
     */
    private fun setAspectRatio(width: Int, height: Int) {
        if (width < 0 || height < 0) {
            throw IllegalArgumentException("Size cannot be negative.")
        }

        /*if (height > width) {//camera preview size width > height
            mRatioHeight = height
            mRatioWidth = width
        } else {
            mRatioHeight = width
            mRatioWidth = height
        }*/
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width: Int = MeasureSpec.getSize(widthMeasureSpec)
        val height: Int = MeasureSpec.getSize(heightMeasureSpec)
        if (0 == previewWidth || 0 == previewWidth) {
            setMeasuredDimension(width, height)
        } else {
            //adjust for 18 : 9 ,or width < height,
            //if (width < height * mRatioWidth / mRatioHeight) {
            setMeasuredDimension(width, width * previewWidth / previewHeight)

            /*} else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height)
            }
            */
        }

    }


    ///////////////////////////////
    /////audio record thread//////
    ///////////////////////////////
    inner class AudioRunnable : Runnable{
        override fun run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            //audio
            var bufferSize = 0
            var audioData: ShortBuffer? = null
            var bufferReadResult = -1
            bufferSize = AudioRecord.getMinBufferSize(sampleAudioRateInHz, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT)
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleAudioRateInHz, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            if (CameraView.RECORD_LENGHT > 0) {
                samplesIndex = 0
                samples = Array(CameraView.RECORD_LENGHT * sampleAudioRateInHz * 2 / bufferSize + 1,
                        { ShortBuffer.allocate(bufferSize) })
            } else {
                audioData = ShortBuffer.allocate(bufferSize)
            }
            Log.d(TAG, "audioRecord starRecording")
            audioRecord!!.startRecording()

            //ffmpeg audio encoding loop
            while (runAudioThread) {
                if (CameraView.RECORD_LENGHT > 0) {
                    audioData = samples?.get(samplesIndex++ % samples!!.size)
                    audioData?.position(0)?.limit(0)

                }

                bufferReadResult = audioRecord!!.read(audioData!!.array(), 0, audioData.capacity())
                audioData.limit(bufferReadResult)
                if (bufferReadResult > 0) {
                    Log.w(TAG, "read buffer success")
                    if (recording) {
                        if (CameraView.RECORD_LENGHT <= 0){
                            try {
                                recorder?.recordSamples(audioData)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
            Log.v(TAG,"audio thread finish ,stop and release audioRecord")
            //release recorder
            if (audioRecord != null){
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                Log.v(TAG,"audio thread release finish!")
            }
        }
    }

    //////////////////////
    //video record thread
    /////////////////
    inner class VideoRecordRunnable : Runnable {

        override fun run() {
            while (recording){
                val frame = frameLinkList?.poll()
                //val fileSteam  = InputStream()
                //val imageExif = ExifInterface()
                ExifInterface.ORIENTATION_ROTATE_90
                if (null != frame) {
                    recorder?.timestamp = frame.timestamp
                    recorder?.record(frame)
                } else{
                    Thread.sleep(50)
                }

            }

        }

    }
}
