package org.sil.hearthis;

import android.content.Context;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;

/**
 * Created by Thomson on 12/28/2014.
 */
public class ListDirectoryHandler {
    Context _parent;
    public ListDirectoryHandler(Context parent)
    {
        _parent = parent;
    }

    public Response handle(NanoHTTPD.IHTTPSession session) {
        File baseDir = _parent.getExternalFilesDir(null);
        String filePath = session.getParms().get("path");
        String path = baseDir + "/" + filePath;
        File file = new File(path);
        StringBuilder sb = new StringBuilder();
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                df.setTimeZone(TimeZone.getTimeZone("UTC"));
                for (File f : files) {
                    sb.append(f.getName());
                    sb.append(";");
                    sb.append(df.format(new Date(f.lastModified())));
                    sb.append(";");
                    sb.append(f.isDirectory() ? "d" : "f");
                    sb.append("\n");
                }
            }
            return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, sb.toString());
        } else {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "");
        }
    }
}
