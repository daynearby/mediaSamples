package com.github.daynearby.javacvsamples

import android.content.pm.PackageManager
import android.hardware.Camera
import android.hardware.Camera.*
import android.os.Build
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.support.constraint.ConstraintLayout
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.Button


/**
 *
 * @date 2018-07-29
 * @author yangfujin
 * @describe
 * @email daynearby@hotmail.com
 */
public class RecordActivity : AppCompatActivity() {
    val TAG = RecordActivity::class.java.simpleName

    val allPermissions: Array<String> =
            arrayOf("android.permission.CAMERA",
                    "android.permission.RECORD_AUDIO",
                    "android.permission.WRITE_EXTERNAL_STORAGE")
    val mRequestCode: Int = 11
    var cameraDevice: Camera? = null
    var cameraView: CameraView? = null
    var buttonContrl: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        }
        setContentView(R.layout.acty_record)

        buttonContrl = findViewById<View>(R.id.btn_ctrl) as Button
        buttonContrl!!.setOnClickListener {
            if (cameraView!!.recording) {
                cameraView?.stopRecording()
                buttonContrl?.text = "开始"
            } else {
                cameraView?.startRecording()
                buttonContrl?.text = "停止"

            }
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            getPermission()
        }


    }

    override fun onResume() {
        super.onResume()
        // cameraView?.onResume()

    }


    override fun onPause() {
        cameraView?.onPause()
        super.onPause()
    }


    @RequiresApi(Build.VERSION_CODES.M)
    fun getPermission() {
        val filter = allPermissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (filter.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, allPermissions, mRequestCode)
        } else {
            addTextureView()
        }

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == mRequestCode) {
            grantResults.forEachIndexed { index, value ->
                if (value != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "get permission fail,${permissions.get(index)}")
                    this.finish()
                } else {
                    Log.d(TAG, "get permission success,${permissions.get(index)}")
                    if (index == permissions.size - 1) {
                        addTextureView()
                    }
                }
            }
        }
    }

    /**
     * add cameraView
     */
    fun addTextureView() {
        val numberOfCameras = getNumberOfCameras()
        val cameraInfo = CameraInfo()
        var cameraId = -1
        var rotation = 0
        val orientation = windowManager.defaultDisplay.orientation
        var degrees = -1
        var result = -1
        when (orientation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }
        for (i in 0 until numberOfCameras) {
            getCameraInfo(i, cameraInfo)
            if (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK) {
                cameraId = i
                result = (cameraInfo.orientation - degrees + 360) % 360;
                break
            }/* else {
                result = (cameraInfo.orientation + degrees) % 360;
                result = (360 - result) % 360;  // compensate the mirror
            }*/
        }

        cameraDevice = Camera.open(cameraId);
        cameraDevice?.setDisplayOrientation(result)
        cameraView = CameraView(this, cameraDevice, cameraId)
        findViewById<ConstraintLayout>(R.id.record_constain).addView(cameraView, 0)
        cameraView?.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (cameraView != null) {
            cameraView?.stopPreview();
        }

        if (cameraDevice != null) {
            cameraDevice?.stopPreview();
            cameraDevice?.release();
            cameraDevice = null;
        }
    }
}