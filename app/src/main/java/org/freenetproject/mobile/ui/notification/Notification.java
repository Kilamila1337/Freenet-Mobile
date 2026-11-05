package org.freenetproject.mobile.ui.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import java.util.HashSet;
import java.util.Set;

import org.freenetproject.mobile.R;
import org.freenetproject.mobile.services.node.Manager;
import org.freenetproject.mobile.ui.main.activity.MainActivity;

/**
 * Class responsible for creating the notification in order to be able to run Freenet on background.
 */
public class Notification {
    private static android.app.Notification.Builder builder;
    private static NotificationManager nm;
    private static Manager manager = Manager.getInstance();
    private static final String CHANNEL_ID = "FREENET SERVICE";

    /** Observers registered through show(), kept around so they can be unregistered in remove(). */
    private static final Set<androidx.lifecycle.Observer<Manager.Status>> observers = new HashSet<>();

    /**
     * Create a notification to remain on background.
     *
     * @param context Application context
     * @return
     */
    public static android.app.Notification show(Context context) {
        nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            nc.setDescription(context.getString(R.string.app_name));
            nc.enableLights(true);
            nc.setLightColor(Color.BLUE);
            nm.createNotificationChannel(nc);
        }

        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder = new android.app.Notification.Builder(context);
        } else {
            builder = new android.app.Notification.Builder(context, CHANNEL_ID);
        }

        builder
            .setContentTitle(context.getString(R.string.node_running))
            .setContentText(context.getString(R.string.connected_to_the_network))
            .setSmallIcon(R.drawable.ic_freenet_logo_notification)
            .setContentIntent(
                // FLAG_IMMUTABLE is mandatory on Android 12+ (targetSdk 31 and above).
                PendingIntent.getActivity(
                    context,
                    0,
                    new Intent(
                        context.getApplicationContext(), MainActivity.class
                    ),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                )
            );

        // observeForever never removes the callback on its own; keep track of it
        // so remove() can unregister it, otherwise it leaks on every restart.
        androidx.lifecycle.Observer<Manager.Status> observer = status -> {
            builder.setContentTitle(context.getString(R.string.node_running));
            builder.setContentText(context.getString(R.string.connected_to_the_network));
            if (status == Manager.Status.PAUSED) {
                builder.setContentTitle(context.getString(R.string.node_paused));
                builder.setContentText(context.getString(R.string.battery_and_data));
            } else if (status == Manager.Status.ERROR) {
                builder.setContentTitle(context.getString(R.string.error_starting_up));
                builder.setContentText(context.getString(R.string.error_detail));
            }

            nm.notify(1, builder.build());
        };
        observers.add(observer);
        manager.getStatus().observeForever(observer);

        return builder.build();
    }

    /**
     * Removes all notifications.
     * @param context
     */
    public static void remove(Context context) {
        for (androidx.lifecycle.Observer<Manager.Status> observer : observers) {
            manager.getStatus().removeObserver(observer);
        }
        observers.clear();

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }
}
