package org.sil.hearthis;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

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
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Enumeration;
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
    private static final int REQUEST_CAMERA_PERMISSION = 201;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 202;
    boolean scanning = false;
    TextView progressView;

    private ExecutorService cameraExecutor;
    private static final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private BarcodeScanner barcodeScanner;
    private final Handler registrationHandler = new Handler(Looper.getMainLooper());

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
        continueButton.setOnClickListener(view -> thisActivity.finish());

        cameraExecutor = Executors.newSingleThreadExecutor();
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        AcceptNotificationHandler.addNotificationListener(this);
    }

    private void requestNotificationPermissionAndStartSync() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.need_permissions)
                            .setMessage(R.string.notification_for_sync)
                            .setPositiveButton(R.string.ok, (dialog, which) -> ActivityCompat.requestPermissions(SyncActivity.this,
                                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                    REQUEST_NOTIFICATION_PERMISSION))
                            .setNegativeButton(R.string.cancel, (dialog, which) -> startSyncServer())
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
        startRegistrationRetry();
    }

    private void stopSyncServer() {
        Intent serviceIntent = new Intent(this, SyncService.class);
        stopService(serviceIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startRegistrationRetry();
    }

    @Override
    protected void onPause() {
        registrationHandler.removeCallbacks(registrationRunnable);
        unregisterListeners();
        super.onPause();
    }

    private final Runnable registrationRunnable = new Runnable() {
        @Override
        public void run() {
            if (registerListeners()) {
                Log.d(TAG, "Successfully registered sync listeners");
            } else {
                Log.d(TAG, "SyncService not ready yet, retrying registration...");
                registrationHandler.postDelayed(this, 500);
            }
        }
    };

    private void startRegistrationRetry() {
        registrationHandler.removeCallbacks(registrationRunnable);
        registrationHandler.post(registrationRunnable);
    }

    private boolean registerListeners() {
        SyncService service = SyncService.getInstance();
        if (service != null && service.getServer() != null) {
            SyncServer server = service.getServer();
            server.getAcceptFileHandler().setListener(this);
            server.getRequestFileHandler().setListener(this);
            server.getAcceptNotificationHandler().addNotificationListener(this);
            return true;
        }
        return false;
    }

    private void unregisterListeners() {
        SyncService service = SyncService.getInstance();
        if (service != null && service.getServer() != null) {
            SyncServer server = service.getServer();
            server.getAcceptFileHandler().setListener(null);
            server.getRequestFileHandler().setListener(null);
            AcceptNotificationHandler.removeNotificationListener(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AcceptNotificationHandler.removeNotificationListener(this);
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
        scanBtn.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(SyncActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                ActivityCompat.requestPermissions(SyncActivity.this, new
                        String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            }
        });
        String ourIpAddress = getOurIpAddress();
        TextView ourIpView = findViewById(R.id.our_ip_address);
        ourIpView.setText(ourIpAddress);
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
            
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
            cameraProviderFuture.addListener(() -> {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    cameraProvider.unbindAll();
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "Failed to unbind camera", e);
                }
            }, ContextCompat.getMainExecutor(this));
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
            Log.e(TAG, "Error getting IP address", e);
            return getString(R.string.ip_error, e.toString());
        }
        return "";
    }

    @Override
    public void onNotification(String message) {
        SyncService service = SyncService.getInstance();
        if (service != null && service.getServer() != null) {
            service.getServer().getAcceptNotificationHandler().removeNotificationListener(this);
        }
        setProgress(getString(R.string.sync_success));
        runOnUiThread(() -> continueButton.setEnabled(true));
    }

    void setProgress(final String text) {
        runOnUiThread(() -> progressView.setText(text));
    }

    private long lastProgressTime = 0;

    @Override
    public void receivingFile(final String name) {
        long now = System.currentTimeMillis();
        if (now - lastProgressTime < 1000)
            return;
        lastProgressTime = now;
        setProgress(getString(R.string.receiving_file, name));
    }

    @Override
    public void sendingFile(final String name) {
        long now = System.currentTimeMillis();
        if (now - lastProgressTime < 1000)
            return;
        lastProgressTime = now;
        setProgress(getString(R.string.sending_file, name));
    }

    private static class SendMessage extends AsyncTask<Void, Void, Void> {
        public String ourIpAddress;
        public String desktopIpAddress;

        @Override
        protected Void doInBackground(Void... params) {
            if (ourIpAddress == null || desktopIpAddress == null) return null;
            try (DatagramSocket socket = new DatagramSocket()) {
                InetAddress receiverAddress = InetAddress.getByName(desktopIpAddress);
                byte[] buffer = ourIpAddress.getBytes(StandardCharsets.UTF_8);
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, receiverAddress, 11007);
                socket.send(packet);
            } catch (IOException e) {
                Log.e(TAG, "Error sending UDP packet", e);
            }
            return null;
        }
    }
}
