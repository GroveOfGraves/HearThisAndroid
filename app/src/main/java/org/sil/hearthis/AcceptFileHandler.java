package org.sil.hearthis;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;

public class AcceptFileHandler {
    private static final String TAG = "AcceptFileHandler";
    private final Context _parent;
    private IFileReceivedNotification listener;

    public AcceptFileHandler(Context parent) {
        _parent = parent;
    }

    public Response handle(NanoHTTPD.IHTTPSession session) {
        File baseDir = _parent.getExternalFilesDir(null);
        if (baseDir == null) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "External storage not available");
        }

        String filePath = session.getParms().get("path");
        if (filePath != null) {
            filePath = filePath.replace('\\', '/');
        } else {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Missing path parameter");
        }

        // Fix Path Traversal Vulnerability
        File file = new File(baseDir, filePath);
        try {
            if (!file.getCanonicalPath().startsWith(baseDir.getCanonicalPath())) {
                Log.w(TAG, "Attempted path traversal: " + filePath);
                return NanoHTTPD.newFixedLengthResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Access denied");
            }
        } catch (IOException e) {
            return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Error validating path");
        }

        if (listener != null)
            listener.receivingFile(filePath);

        Map<String, String> files = new HashMap<>();
        try {
            session.parseBody(files);
            
            // NanoHTTPD might put the content directly in the map or provide a path to a temp file.
            String contentOrPath = files.get("content");
            if (contentOrPath == null) {
                contentOrPath = files.get("postData");
            }

            if (contentOrPath != null) {
                File dir = file.getParentFile();
                if (dir != null && !dir.exists()) {
                    if (!dir.mkdirs()){
                        Log.e("Recorder","Error creating directory at " + dir.getAbsolutePath());
                    }
                }

                // Check if it's a file path or raw content.
                // Raw content (XML or info.txt) won't start with / and be a valid path.
                // We also limit the length check for path strings to avoid ENAMETOOLONG on the exists() call.
                boolean isPath = false;
                if (contentOrPath.length() < 1024 && contentOrPath.startsWith("/")) {
                    File srcFile = new File(contentOrPath);
                    if (srcFile.exists() && srcFile.isFile()) {
                        isPath = true;
                    }
                }

                if (isPath) {
                    copyFile(new File(contentOrPath), file);
                } else {
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        out.write(contentOrPath.getBytes(StandardCharsets.UTF_8));
                    }
                }
                Log.d(TAG, "Successfully saved file to: " + file.getAbsolutePath());
                return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "success\n");
            } else {
                Log.e(TAG, "No content found in request body");
                return NanoHTTPD.newFixedLengthResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "failure: no content\n");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to accept file: " + filePath, e);
            return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "failure: " + e.getMessage() + "\n");
        }
    }

    private void copyFile(File src, File dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src); 
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    public interface IFileReceivedNotification {
        void receivingFile(String name);
    }

    public void setListener(IFileReceivedNotification newListener) {
        listener = newListener;
    }
}
