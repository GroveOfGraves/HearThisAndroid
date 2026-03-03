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
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

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
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class SyncActivity extends AppCompatActivity implements AcceptNotificationHandler.NotificationListener,
        AcceptFileHandler.IFileReceivedNotification,
        RequestFileHandler.IFileSentNotification {

    private static final String TAG = "SyncActivity";
    Button scanBtn;
    Button continueButton;
    TextView ipView;
    PreviewView previewView;
    int desktopPort = 11007; // port on which the desktop is listening for our IP address.
    private static final int REQUEST_CAMERA_PERMISSION = 201;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 202;
    boolean scanning = false;
    TextView progressView;

    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;

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
        previewView = findViewById(R.id.preview_view);
        previewView.setVisibility(View.INVISIBLE);
        continueButton.setEnabled(false);
        final SyncActivity thisActivity = this;
        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                thisActivity.finish();
            }
        });

        cameraExecutor = Executors.newSingleThreadExecutor();
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);
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
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            stopSyncServer();
        }
        cameraExecutor.shutdown();
        barcodeScanner.close();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_sync, menu);
        ipView = findViewById(R.id.ip_address);
        scanBtn = findViewById(R.id.scan_button);
        scanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ActivityCompat.checkSelfPermission(SyncActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startCamera();
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

    private void startCamera() {
        scanning = true;
        previewView.setVisibility(View.VISIBLE);

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, new ImageAnalysis.Analyzer() {
                    @Override
                    @androidx.annotation.OptIn(markerClass = androidx.camera.core.ExperimentalGetImage.class)
                    public void analyze(@NonNull ImageProxy imageProxy) {
                        if (!scanning) {
                            imageProxy.close();
                            return;
                        }

                        @SuppressLint("UnsafeOptInUsageError")
                        android.media.Image mediaImage = imageProxy.getImage();
                        if (mediaImage != null) {
                            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                            barcodeScanner.process(image)
                                    .addOnSuccessListener(barcodes -> {
                                        if (scanning && !barcodes.isEmpty()) {
                                            handleBarcode(barcodes.get(0));
                                        }
                                    })
                                    .addOnFailureListener(e -> Log.e(TAG, "Barcode scanning failed", e))
                                    .addOnCompleteListener(task -> imageProxy.close());
                        } else {
                            imageProxy.close();
                        }
                    }
                });

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void handleBarcode(Barcode barcode) {
        String contents = barcode.getDisplayValue();
        if (contents == null) return;
        
        scanning = false;
        runOnUiThread(() -> {
            ipView.setText(contents);
            previewView.setVisibility(View.INVISIBLE);
            
            SendMessage sendMessageTask = new SendMessage();
            sendMessageTask.ourIpAddress = getOurIpAddress();
            sendMessageTask.desktopIpAddress = contents;
            sendMessageTask.execute();
            
            ProcessCameraProvider cameraProvider;
            try {
                cameraProvider = ProcessCameraProvider.getInstance(this).get();
                cameraProvider.unbindAll();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Failed to unbind camera", e);
            }
        });
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
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startCamera();
                }
                break;
            case REQUEST_NOTIFICATION_PERMISSION:
                startSyncServer();
                break;
        }
    }

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
            e.printStackTrace();
            ip = getString(R.string.ip_error, e.toString());
        }
        return ip;
    }

    @Override
    public void onNotification(String message) {
        AcceptNotificationHandler.removeNotificationListener(this);
        setProgress(getString(R.string.sync_success));
        runOnUiThread(() -> continueButton.setEnabled(true));
    }

    void setProgress(final String text) {
        runOnUiThread(() -> progressView.setText(text));
    }

    Date lastProgress = new Date();

    @Override
    public void receivingFile(final String name) {
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
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
