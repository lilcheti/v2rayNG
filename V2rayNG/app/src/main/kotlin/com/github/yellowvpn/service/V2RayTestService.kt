package com.github.yellowvpn.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.github.yellowvpn.AppConfig.MSG_MEASURE_CONFIG
import com.github.yellowvpn.AppConfig.MSG_MEASURE_CONFIG_CANCEL
import com.github.yellowvpn.AppConfig.MSG_MEASURE_CONFIG_SUCCESS
import com.github.yellowvpn.util.MessageUtil
import com.github.yellowvpn.util.SpeedtestUtil
import com.github.yellowvpn.util.Utils
import go.Seq
import kotlinx.coroutines.*
import libv2ray.Libv2ray
import java.util.concurrent.Executors

class V2RayTestService : Service() {
    private val realTestScope by lazy { CoroutineScope(Executors.newFixedThreadPool(10).asCoroutineDispatcher()) }

    override fun onCreate() {
        super.onCreate()
        Seq.setContext(this)
        Libv2ray.initV2Env(Utils.userAssetPath(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getIntExtra("key", 0)) {
            MSG_MEASURE_CONFIG -> {
                val contentPair = intent.getSerializableExtra("content") as Pair<String, String>
                realTestScope.launch {
                    val result = SpeedtestUtil.realPing(contentPair.second)
                    MessageUtil.sendMsg2UI(this@V2RayTestService, MSG_MEASURE_CONFIG_SUCCESS, Pair(contentPair.first, result))
                }
            }
            MSG_MEASURE_CONFIG_CANCEL -> {
                realTestScope.coroutineContext[Job]?.cancelChildren()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}