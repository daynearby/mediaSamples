package com.github.daynearby.javacvsamples

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.annotation.RequiresApi
import android.support.constraint.ConstraintLayout
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View


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
    var camerView: CamerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.acty_record)

        findViewById<View>(R.id.btn_ctrl).setOnClickListener { }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            getPermission()
        }

    }

    override fun onResume() {
        super.onResume()
        camerView?.onResume()

    }


    override fun onPause() {
        camerView?.onPause()
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
                    if (index == permissions.size) {
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
        camerView = CamerView(this)
        findViewById<ConstraintLayout>(R.id.record_constain).addView(camerView, 0)
        //camerView?.onResume()
    }


}