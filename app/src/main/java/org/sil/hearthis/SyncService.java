package org.sil.hearthis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

// Service that runs a simple 'web server' that HearThis desktop can talk to.
public class SyncService extends Service {
    private static final String CHANNEL_ID = "SyncServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    public SyncService() {
    }

    SyncServer _server;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        _server = new SyncServer(this);
    }

    @Override
    public void onDestroy() {
        if (_server != null) {
            _server.stopThread();
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.sync_title))
                .setContentText(getString(R.string.sync_message))
                .setSmallIcon(R.drawable.ic_launcher)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            // In Android 14+, the system may occasionally refuse to start a foreground service.
            // We catch this to prevent a crash, as the SyncServer can often still run.
        }

        if (_server != null) {
            _server.startThread();
        }

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Sync Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
