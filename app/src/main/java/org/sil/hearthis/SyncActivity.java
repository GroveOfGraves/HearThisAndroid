package org.sil.hearthis;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Enumeration;


public class SyncActivity extends AppCompatActivity implements AcceptNotificationHandler.NotificationListener,
        AcceptFileHandler.IFileReceivedNotification,
        RequestFileHandler.IFileSentNotification {

    Button scanBtn;
    Button continueButton;
    TextView ipView;
    SurfaceView preview;
    int desktopPort = 11007; // port on which the desktop is listening for our IP address.
    private static final int REQUEST_CAMERA_PERMISSION = 201;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 202;
    boolean scanning = false;
    TextView progressView;

    private BarcodeDetector barcodeDetector;
    private CameraSource cameraSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync);

        View syncLayout = findViewById(R.id.sync_layout);
        if (syncLayout != null) {
            // Get original padding from XML to preserve it
            int paddingLeft = syncLayout.getPaddingLeft();
            int paddingTop = syncLayout.getPaddingTop();
            int paddingRight = syncLayout.getPaddingRight();
            int paddingBottom = syncLayout.getPaddingBottom();

            ViewCompat.setOnApplyWindowInsetsListener(syncLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                
                // We add the system bar insets to the original XML padding.
                // Note: On many devices, systemBars.top will include the status bar.
                // If the Action Bar is still covering text, we might need to account 
                // for its height specifically or use a NoActionBar theme with a Toolbar.
                v.setPadding(
                    paddingLeft + systemBars.left,
                    paddingTop + systemBars.top,
                    paddingRight + systemBars.right,
                    paddingBottom + systemBars.bottom
                );
                return insets;
            });
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.sync_title);
        }
        requestNotificationPermissionAndStartSync();
        progressView = findViewById(R.id.progress);
        continueButton = findViewById(R.id.continue_button);
        preview = findViewById(R.id.surface_view);
        preview.setVisibility(View.INVISIBLE);
        continueButton.setEnabled(false);
        final SyncActivity thisActivity = this;
        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                thisActivity.finish();
            }
        });
    }

    private void requestNotificationPermissionAndStartSync() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.need_permissions)
                            .setMessage(R.string.notification_for_sync)
                            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    ActivityCompat.requestPermissions(SyncActivity.this,
                                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                            REQUEST_NOTIFICATION_PERMISSION);
                                }
                            })
                            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // User denied, start anyway and hope for the best (or service might not show notification)
                                    startSyncServer();
                                }
                            })
                            .create().show();
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATION_PERMISSION);
                }
                return;
            }
        }
        startSyncServer();
    }

    private void startSyncServer() {
        Intent serviceIntent = new Intent(this, SyncService.class);
        startService(serviceIntent);
    }

    private void stopSyncServer() {
        Intent serviceIntent = new Intent(this, SyncService.class);
        stopService(serviceIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AcceptFileHandler.requestFileReceivedNotification(this);
        RequestFileHandler.requestFileSentNotification((this));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraSource != null) {
            cameraSource.release();
            cameraSource = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            stopSyncServer();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_sync, menu);
        ipView = findViewById(R.id.ip_address);
        scanBtn = findViewById(R.id.scan_button);
        scanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // This approach is deprecated, but the new approach (using ML_Kit)
                // requires us to increase MinSdk from 18 to 19 (4.4) and barcode scanning is
                // not important enough for us to do that. This works fine on an app that targets
                // SDK 33, at least while running on Android 12.
                barcodeDetector = new BarcodeDetector.Builder(SyncActivity.this)
                        .setBarcodeFormats(Barcode.QR_CODE)
                        .build();
                if (cameraSource != null)
                {
                    //cameraSource.stop();
                    cameraSource.release();
                    cameraSource = null;
                }

                cameraSource = new CameraSource.Builder(SyncActivity.this, barcodeDetector)
                        .setRequestedPreviewSize(1920, 1080)
                        .setAutoFocusEnabled(true)
                        .build();

                barcodeDetector.setProcessor(new Detector.Processor<Barcode>() {
                    @Override
                    public void release() {
                        // Toast.makeText(getApplicationContext(), "To prevent memory leaks barcode scanner has been stopped", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void receiveDetections(@NonNull Detector.Detections<Barcode> detections) {
                        final SparseArray<Barcode> barcodes = detections.getDetectedItems();
                        if (scanning && barcodes.size() != 0) {
                            String contents = barcodes.valueAt(0).displayValue;
                            if (contents != null) {
                                scanning = false; // don't want to repeat this if it finds the image again
                                runOnUiThread(new Runnable() {
                                                  @Override
                                                  public void run() {
                                                      // Enhance: do something (add a magic number or label?) so we can tell if they somehow scanned
                                                      // some other QR code. We've reduced the chances by telling the BarCodeDetector to
                                                      // only look for QR codes, but conceivably the user could find something else.
                                                      // It's only used for one thing: we will try to use it as an IP address and send
                                                      // a simple DataGram to it containing our own IP address. So if it's no good,
                                                      // there'll probably be an exception, and it will be ignored, and nothing will happen
                                                      // except that whatever text the QR code represents shows on the screen, which might
                                                      // provide some users a clue that all is not well.
                                                      ipView.setText(contents);
                                                      preview.setVisibility(View.INVISIBLE);
                                                      SendMessage sendMessageTask = new SendMessage();
                                                      sendMessageTask.ourIpAddress = getOurIpAddress();
                                                      sendMessageTask.desktopIpAddress = contents;
                                                      sendMessageTask.execute();
                                                      cameraSource.stop();
                                                      cameraSource.release();
                                                      cameraSource = null;
                                                  }
                                              });

                            }
                        }
                    }
                });

                if (ActivityCompat.checkSelfPermission(SyncActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        scanning = true;
                        preview.setVisibility(View.VISIBLE);
                        cameraSource.start(preview.getHolder());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else {
                    ActivityCompat.requestPermissions(SyncActivity.this, new
                            String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                }
            }
        });
        String ourIpAddress = getOurIpAddress();
        TextView ourIpView = findViewById(R.id.our_ip_address);
        ourIpView.setText(ourIpAddress);
        AcceptNotificationHandler.addNotificationListener(this);
        return true;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION:
                if (grantResults.length > 0) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        try {
                            scanning = true;
                            preview.setVisibility(View.VISIBLE);
                            cameraSource.start(preview.getHolder());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
            case REQUEST_NOTIFICATION_PERMISSION:
                // Regardless of the result, start the service.
                // If denied, the user just won't see the notification.
                startSyncServer();
                break;
        }
    }

    // Get the IP address of this device (on the WiFi network) to transmit to the desktop.
    private String getOurIpAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        return inetAddress.getHostAddress();
                    }

                }

            }

        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            ip = getString(R.string.ip_error, e.toString());
        }

        return ip;
    }

    @Override
    public void onNotification(String message) {
        AcceptNotificationHandler.removeNotificationListener(this);
        setProgress(getString(R.string.sync_success));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                continueButton.setEnabled(true);
            }
        });
    }

    void setProgress(final String text) {
        runOnUiThread(new Runnable() {
            public void run() {
                progressView.setText(text);
            }
        });
    }

    Date lastProgress = new Date();

    @Override
    public void receivingFile(final String name) {
        // To prevent excess flicker and wasting compute time on progress reports,
        // only change once per second.
        if (new Date().getTime() - lastProgress.getTime() < 1000)
            return;
        lastProgress = new Date();
        setProgress(getString(R.string.receiving_file, name));
    }

    @Override
    public void sendingFile(final String name) {
        if (new Date().getTime() - lastProgress.getTime() < 1000)
            return;
        lastProgress = new Date();
        setProgress(getString(R.string.sending_file, name));
    }

    // This class is responsible to send one message packet to the IP address we
    // obtained from the desktop, containing the Android's own IP address.
    private static class SendMessage extends AsyncTask<Void, Void, Void> {

        public String ourIpAddress;
        public String desktopIpAddress;

        @Override
        protected Void doInBackground(Void... params) {
            try (DatagramSocket socket = new DatagramSocket()) {
                InetAddress receiverAddress = InetAddress.getByName(desktopIpAddress);
                byte[] buffer = ourIpAddress.getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, receiverAddress, 11007);
                socket.send(packet);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
