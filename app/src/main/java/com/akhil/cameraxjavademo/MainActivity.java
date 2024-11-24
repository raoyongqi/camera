package com.akhil.cameraxjavademo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.extensions.HdrImageCaptureExtender;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private Executor executor = Executors.newSingleThreadExecutor();
    private int REQUEST_CODE_PERMISSIONS = 1001;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE"};

    PreviewView mPreviewView;
    ImageView captureImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPreviewView = findViewById(R.id.previewView);
        captureImage = findViewById(R.id.captureImg);

        if(allPermissionsGranted()){
            deleteFilesInDCIM();
            startCamera(); //start camera if permission has been granted by user
        } else{
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }
    private static void deleteFilesInDirectory(File directory) {
        // Get all files and subdirectories in the current directory
        File[] files = directory.listFiles();

        if (files != null && files.length > 0) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // If it's a directory, recursively call the method to handle its contents
                    deleteFilesInDirectory(file);
                    Log.d("DCIM", "保留文件夹: " + file.getName());
                } else {
                    // If it's a file, delete it
                    boolean deleted = file.delete();
                    if (deleted) {
                        Log.d("DCIM", "已删除文件: " + file.getName());
                    } else {
                        Log.d("DCIM", "无法删除文件: " + file.getName());
                    }
                }
            }
        }
    }
    private void startCamera() {

        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {

                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                    bindPreview(cameraProvider);

                } catch (ExecutionException | InterruptedException e) {
                    // No errors need to be handled for this Future.
                    // This should never be reached.
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {

        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .build();

        ImageCapture.Builder builder = new ImageCapture.Builder();

        //Vendor-Extensions (The CameraX extensions dependency in build.gradle)
        HdrImageCaptureExtender hdrImageCaptureExtender = HdrImageCaptureExtender.create(builder);

        // Query if extension is available (optional).
        if (hdrImageCaptureExtender.isExtensionAvailable(cameraSelector)) {
            // Enable the extension if available.
            hdrImageCaptureExtender.enableExtension(cameraSelector);
        }

        final ImageCapture imageCapture = builder
                .setTargetRotation(this.getWindowManager().getDefaultDisplay().getRotation())
                .build();

        preview.setSurfaceProvider(mPreviewView.createSurfaceProvider());

        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview, imageAnalysis, imageCapture);

        captureImage.setOnClickListener(v -> {
            deleteFilesInDCIM();
            CameraSelector currentCameraSelector = getCurrentCameraSelector();
            SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
            File file = new File(getBatchDirectoryName(), mDateFormat.format(new Date())+ ".jpg");

            ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(file).build();
            imageCapture.takePicture(outputFileOptions, executor, new ImageCapture.OnImageSavedCallback () {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Image Saved successfully", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                @Override
                public void onError(@NonNull ImageCaptureException error) {
                    error.printStackTrace();
                }
            });
        });
    }
    public static void deleteFilesInDCIM() {
        String path = Environment.getExternalStorageDirectory().getPath();
        Log.d("StoragePath", "根目录路径: " + path);

        // 构建 DCIM 文件夹路径
        File dcimFolder = new File(path, "DCIM");

        if (dcimFolder.exists() && dcimFolder.isDirectory()) {
            Log.d("DCIM", "DCIM 文件夹存在!");
            // 删除 DCIM 文件夹中的所有文件
            deleteFilesInDirectory(dcimFolder);
        } else {
            Log.d("DCIM", "DCIM 文件夹不存在！");
        }
    }
    public String getBatchDirectoryName() {

        String app_folder_path = "";
        app_folder_path = Environment.getExternalStorageDirectory().toString() + "/DCIM";
        File dir = new File(app_folder_path);
        if (!dir.exists() && !dir.mkdirs()) {

        }

        return app_folder_path;
    }

    private boolean allPermissionsGranted(){

        for(String permission : REQUIRED_PERMISSIONS){
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }
        return true;
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if(requestCode == REQUEST_CODE_PERMISSIONS){
            if(allPermissionsGranted()){
                startCamera();
            } else{
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                this.finish();
            }
        }
    }
}
