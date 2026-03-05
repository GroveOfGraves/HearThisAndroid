package org.sil.hearthis;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * Created by Thomson on 1/18/2016.
 */
public class AcceptNotificationHandler {

    public interface NotificationListener {
        void onNotification(String message);
    }
    
    private static final List<NotificationListener> notificationListeners = new CopyOnWriteArrayList<>();

    public static void addNotificationListener(NotificationListener listener) {
        if (listener != null && !notificationListeners.contains(listener)) {
            notificationListeners.add(listener);
        }
    }

    public static void removeNotificationListener(NotificationListener listener) {
        notificationListeners.remove(listener);
    }

    public Response handle(NanoHTTPD.IHTTPSession session) {
        // Enhance: allow the notification to contain a message, and pass it on.
        for (NotificationListener listener : notificationListeners) {
            listener.onNotification("");
        }
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "success");
    }
}
