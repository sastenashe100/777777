package com.android.photo.camera.capture;

import android.os.Handler;
import java.lang.ref.WeakReference;

public class MonitoringRunnable implements Runnable {
    private final WeakReference<ServiceMonitor> serviceRef;
    private final Handler handler;
    private final long interval;

    public interface ServiceMonitor {
        boolean isDestroyed();
        void performMonitoringTask();
    }

    public MonitoringRunnable(ServiceMonitor service, Handler handler, long interval) {
        this.serviceRef = new WeakReference<>(service);
        this.handler = handler;
        this.interval = interval;
    }

    @Override
    public void run() {
        ServiceMonitor service = serviceRef.get();
        if (service != null && !service.isDestroyed()) {
            service.performMonitoringTask();
            if (handler != null && !service.isDestroyed()) {
                handler.postDelayed(this, interval);
            }
        }
    }
}