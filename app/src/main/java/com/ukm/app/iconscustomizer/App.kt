package com.ukm.app.iconscustomizer

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.google.android.material.color.DynamicColors
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

class App : Application(), XposedServiceHelper.OnServiceListener {

    companion object {
        var mService: XposedService? = null
            private set

        private val MAIN_HANDLER = Handler(Looper.getMainLooper())

        private val SERVICE_STATE_LISTENERS = CopyOnWriteArraySet<ServiceStateListener>()

        fun addServiceStateListener(listener: ServiceStateListener) {
            SERVICE_STATE_LISTENERS.add(listener)
            dispatchServiceState(listener, mService)
        }

        fun removeServiceStateListener(listener: ServiceStateListener) {
            SERVICE_STATE_LISTENERS.remove(listener)
        }

        private fun notifyServiceStateChanged(service: XposedService?) {
            for (listener in SERVICE_STATE_LISTENERS) {
                dispatchServiceState(listener, service)
            }
        }

        private fun dispatchServiceState(listener: ServiceStateListener, service: XposedService?) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                listener.onServiceStateChanged(service)
            } else {
                MAIN_HANDLER.post { listener.onServiceStateChanged(service) }
            }
        }
    }

    interface ServiceStateListener {
        fun onServiceStateChanged(service: XposedService?)
    }

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        mService = service
        notifyServiceStateChanged(mService)
    }

    override fun onServiceDied(service: XposedService) {
        mService = null
        notifyServiceStateChanged(null)
    }
}