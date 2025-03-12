package com.yiku.yikupayload

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.yiku.yikupayloadSDK.util.OpusUtils

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val opusUtils = OpusUtils.getInstant()
        Log.i("测试", "初始化OpusUtils成功")
        val createEncoder = opusUtils.createEncoder(8000, 1, 1)
        Log.i("测试", "createEncoder成功")
    }
}