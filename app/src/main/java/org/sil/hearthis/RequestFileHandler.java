package org.sil.hearthis;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * Created by Thomson on 12/28/2014.
 */
public class RequestFileHandler {
    Context _parent;
    public RequestFileHandler(Context parent)
    {
        _parent = parent;
    }

    public Response handle(NanoHTTPD.IHTTPSession session) {
        File baseDir = _parent.getExternalFilesDir(null);
        String filePath = session.getParms().get("path");
        if (listener != null)
            listener.sendingFile(filePath);
        
        String path = baseDir + "/" + filePath;
        File file = new File(path);
        if (!file.exists()) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "");
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            Response response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/force-download", fis, file.length());
            response.addHeader("Content-Type", "application/force-download");
            return response;
        } catch (FileNotFoundException e) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "");
        }
    }

    public interface IFileSentNotification {
        void sendingFile(String name);
    }

    static IFileSentNotification listener;
    public static void requestFileSentNotification(IFileSentNotification newListener) {
        listener = newListener; // We only support notifying the most recent for now.
    }
}
