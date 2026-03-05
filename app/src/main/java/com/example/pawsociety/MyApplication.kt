package com.example.pawsociety

import android.app.Application
import com.example.pawsociety.util.SocketManager

class MyApplication : Application() {

    companion object {
        lateinit var instance: MyApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onTerminate() {
        super.onTerminate()
        SocketManager.disconnect()
    }
}