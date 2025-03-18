package com.android.photo.camera.capture;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class ServiceStarterWorker extends Worker {
    public ServiceStarterWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        App app = (App) getApplicationContext();
        if (app != null) {
            app.startAllPersistentServices();
        }
        return Result.success();
    }
}