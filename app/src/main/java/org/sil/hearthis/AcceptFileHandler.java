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
    Context _parent;

    public AcceptFileHandler(Context parent) {
        _parent = parent;
    }

    public Response handle(NanoHTTPD.IHTTPSession session) {
        File baseDir = _parent.getExternalFilesDir(null);
        String filePath = session.getParms().get("path");
        if (filePath != null) {
            filePath = filePath.replace('\\', '/');
        }
        
        if (listener != null)
            listener.receivingFile(filePath);

        String result = "failure";
        Map<String, String> files = new HashMap<>();
        try {
            session.parseBody(files);
            
            // NanoHTTPD might put the content directly in the map or provide a path to a temp file.
            String contentOrPath = files.get("content");
            if (contentOrPath == null) {
                contentOrPath = files.get("postData");
            }

            if (contentOrPath != null && filePath != null) {
                assert baseDir != null;
                String path = baseDir.getAbsolutePath() + "/" + filePath;
                File file = new File(path);
                
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
                    try {
                        File srcFile = new File(contentOrPath);
                        if (srcFile.exists() && srcFile.isFile()) {
                            isPath = true;
                        }
                    } catch (Exception e) {
                        // Not a valid path
                    }
                }

                if (isPath) {
                    copyFile(new File(contentOrPath), file);
                } else {
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        out.write(contentOrPath.getBytes(StandardCharsets.UTF_8));
                    }
                }
                Log.d(TAG, "Successfully saved file to: " + path);
                result = "success";
            } else {
                Log.e(TAG, "No content found in request body or path is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to accept file: " + filePath, e);
        }

        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, result + "\n");
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

    static IFileReceivedNotification listener;
    public static void requestFileReceivedNotification(IFileReceivedNotification newListener) {
        listener = newListener;
    }
}
