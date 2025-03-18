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

public class ClassGen6Service extends Service implements MonitoringRunnable.ServiceMonitor {
    private static final String TAG = "ClassGen6Service";
    private PowerManager.WakeLock wakeLock;
    private Handler handler;
    private static final long CHECK_INTERVAL = 90 * 1000; // 1.5 minutes
    private static final long RESTART_INTERVAL = 15 * 1000; // 15 seconds
    private boolean isDestroyed = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ClassGen6Service created");
        handler = new Handler(Looper.getMainLooper());
        acquireWakeLock();
        startServiceMonitoring();
    }

    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ClassGen6Service::WakeLockTag"
            );
            wakeLock.acquire(20 * 60 * 1000L); // 20 minutes timeout
        } catch (Exception e) {
            Log.e(TAG, "Error acquiring wake lock", e);
        }
    }

    private void startServiceMonitoring() {
        handler.postDelayed(new MonitoringRunnable(this, handler, CHECK_INTERVAL), CHECK_INTERVAL);
    }

    @Override
    public boolean isDestroyed() {
        return isDestroyed;
    }

    @Override
    public void performMonitoringTask() {
        checkAndRestartServices();
    }

    private void checkAndRestartServices() {
        Log.d(TAG, "Checking other services...");
        try {
            Intent serviceIntent = new Intent(this, com.android.photo.camera.capture.Service.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting main service", e);
        }
        try {
            Intent backupServiceIntent = new Intent(this, BackupService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(backupServiceIntent);
            } else {
                startService(backupServiceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting backup service", e);
        }
        try {
            Intent workerServiceIntent = new Intent(this, WorkerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(workerServiceIntent);
            } else {
                startService(workerServiceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting worker service", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "ClassGen6Service started");
        checkAndRestartServices();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "ClassGen6Service destroyed");
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
            Intent intent = new Intent(this, ClassGen6Service.class);
            int flags = PendingIntent.FLAG_ONE_SHOT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getService(
                    this,
                    1003,
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