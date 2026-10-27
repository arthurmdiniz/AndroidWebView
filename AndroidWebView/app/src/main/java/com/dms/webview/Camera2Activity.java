package com.dms.webview;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class Camera2Activity extends Activity {

    private static final int CAMERA_PERMISSION_CODE = 2002;
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int FOCUS_AREA_DIVISOR = 20;

    private FrameLayout root;
    private TextureView textureView;
    private Button captureBtn;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewBuilder;
    private ImageReader imageReader;
    private Size previewSize;
    private Rect activeArray;
    private Matrix previewTransform;
    private int afMode;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private final TextureView.SurfaceTextureListener surfaceListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
            fail("Erro ao abrir câmera");
        }
    };

    private final CameraCaptureSession.StateCallback sessionCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            if (cameraDevice == null) return;
            captureSession = session;
            try {
                session.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
            } catch (CameraAccessException | IllegalStateException e) {
                fail("Erro ao iniciar preview");
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            fail("Erro ao iniciar preview");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        textureView = new TextureView(this);
        textureView.setSurfaceTextureListener(surfaceListener);
        root.addView(textureView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        textureView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                focusAt(event.getX(), event.getY());
                v.performClick();
            }
            return true;
        });

        captureBtn = createButton("CAPTURAR", 22);
        root.addView(captureBtn, getButtonParams());
        captureBtn.setOnClickListener(v -> capture());

        Button backBtn = createButton("VOLTAR", 16);
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        backParams.setMargins(dp(16), dp(16), 0, 0);
        root.addView(backBtn, backParams);
        backBtn.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        setContentView(root);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{ Manifest.permission.CAMERA }, CAMERA_PERMISSION_CODE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Permissão de câmera negada", Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                finish();
            }
        }
    }

    private void openCamera() {
        if (cameraDevice != null || !textureView.isAvailable()) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) return;
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String cameraId = findBackCameraId(manager);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            afMode = chooseAfMode(characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES));

            Size largestJpeg = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                    (a, b) -> Long.compare(area(a), area(b)));
            imageReader = ImageReader.newInstance(largestJpeg.getWidth(), largestJpeg.getHeight(),
                    ImageFormat.JPEG, 2);
            imageReader.setOnImageAvailableListener(this::saveImage, backgroundHandler);
            previewSize = choosePreviewSize(map.getOutputSizes(SurfaceTexture.class), largestJpeg);
            configureTransform(textureView.getWidth(), textureView.getHeight());

            manager.openCamera(cameraId, stateCallback, backgroundHandler);
        } catch (CameraAccessException | RuntimeException e) {
            fail("Erro ao abrir câmera");
        }
    }

    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        previewBuilder = null;
    }

    private void createPreviewSession() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(texture);
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(previewSurface);
            previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, afMode);
            cameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface, imageReader.getSurface()),
                    sessionCallback, backgroundHandler);
        } catch (CameraAccessException | RuntimeException e) {
            fail("Erro ao iniciar preview");
        }
    }

    private void focusAt(float viewX, float viewY) {
        if (captureSession == null || previewBuilder == null || previewTransform == null || activeArray == null) return;

        Matrix inverse = new Matrix();
        if (!previewTransform.invert(inverse)) return;
        float[] point = { viewX, viewY };
        inverse.mapPoints(point);

        float nx = Math.max(0f, Math.min(1f, point[0] / previewSize.getWidth()));
        float ny = Math.max(0f, Math.min(1f, point[1] / previewSize.getHeight()));
        int cx = activeArray.left + Math.round(nx * activeArray.width());
        int cy = activeArray.top + Math.round(ny * activeArray.height());
        int half = activeArray.width() / FOCUS_AREA_DIVISOR;
        Rect area = new Rect(cx - half, cy - half, cx + half, cy + half);
        area.intersect(activeArray);

        try {
            previewBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{
                    new MeteringRectangle(area, MeteringRectangle.METERING_WEIGHT_MAX)});
            previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            captureSession.capture(previewBuilder.build(), null, backgroundHandler);
            previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            captureSession.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException | RuntimeException ignored) {}
    }

    private void capture() {
        if (cameraDevice == null || captureSession == null || imageReader == null) return;
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(imageReader.getSurface());
            builder.set(CaptureRequest.CONTROL_AF_MODE, afMode);
            builder.set(CaptureRequest.JPEG_QUALITY, (byte) Config.getPhotoQuality(this));
            builder.set(CaptureRequest.JPEG_ORIENTATION, computeRotation());
            captureSession.capture(builder.build(), null, backgroundHandler);
        } catch (CameraAccessException | IllegalStateException e) {
            Toast.makeText(this, "Erro ao capturar foto", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveImage(ImageReader reader) {
        Image image = reader.acquireNextImage();
        if (image == null) return;

        byte[] bytes;
        try {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
        } finally {
            image.close();
        }

        Intent resultIntent = new Intent();
        boolean saved = false;
        File photoFile = createPhotoFile();
        if (photoFile != null) {
            try (FileOutputStream fos = new FileOutputStream(photoFile)) {
                fos.write(bytes);
                resultIntent.putExtra("photo_path", photoFile.getAbsolutePath());
                saved = true;
            } catch (IOException ignored) {}
        }

        boolean ok = saved;
        runOnUiThread(() -> {
            setResult(ok ? RESULT_OK : RESULT_CANCELED, resultIntent);
            finish();
        });
    }

    private File createPhotoFile() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File storageDir = new File(getExternalFilesDir(null), "camera");
            if (!storageDir.exists()) storageDir.mkdirs();
            return File.createTempFile("IMG_" + timeStamp, ".jpg", storageDir);
        } catch (IOException e) {
            return null;
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (previewSize == null || viewWidth == 0 || viewHeight == 0) return;
        int rotation = computeRotation();
        float bufferWidth = previewSize.getWidth();
        float bufferHeight = previewSize.getHeight();
        boolean swapped = rotation == 90 || rotation == 270;
        float rotatedWidth = swapped ? bufferHeight : bufferWidth;
        float rotatedHeight = swapped ? bufferWidth : bufferHeight;
        float scale = Math.max(viewWidth / rotatedWidth, viewHeight / rotatedHeight);

        previewTransform = new Matrix();
        previewTransform.postTranslate(-bufferWidth / 2f, -bufferHeight / 2f);
        previewTransform.postRotate(rotation);
        previewTransform.postScale(scale, scale);
        previewTransform.postTranslate(viewWidth / 2f, viewHeight / 2f);
        textureView.setTransform(previewTransform);
    }

    private int computeRotation() {
        int degrees;
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
            default: degrees = 0;
        }
        return (360 - degrees) % 360;
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("Camera2Background");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread == null) return;
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
        } catch (InterruptedException ignored) {}
        backgroundThread = null;
        backgroundHandler = null;
    }

    private void fail(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    private static String findBackCameraId(CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            Integer facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id;
            }
        }
        throw new CameraAccessException(CameraAccessException.CAMERA_ERROR, "Nenhuma câmera traseira");
    }

    private static int chooseAfMode(int[] modes) {
        if (modes == null) return CaptureRequest.CONTROL_AF_MODE_OFF;
        for (int mode : modes) {
            if (mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) return mode;
        }
        for (int mode : modes) {
            if (mode == CaptureRequest.CONTROL_AF_MODE_AUTO) return mode;
        }
        return CaptureRequest.CONTROL_AF_MODE_OFF;
    }

    private static Size choosePreviewSize(Size[] choices, Size photoSize) {
        double aspect = (double) photoSize.getWidth() / photoSize.getHeight();
        Size match = null;
        Size fallback = null;
        for (Size size : choices) {
            if (size.getWidth() > MAX_PREVIEW_WIDTH) continue;
            if (fallback == null || area(size) > area(fallback)) fallback = size;
            double ratio = (double) size.getWidth() / size.getHeight();
            if (Math.abs(ratio - aspect) < 0.01 && (match == null || area(size) > area(match))) {
                match = size;
            }
        }
        if (match != null) return match;
        if (fallback != null) return fallback;
        return choices[0];
    }

    private static long area(Size size) {
        return (long) size.getWidth() * size.getHeight();
    }

    private Button createButton(String text, int textSize) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(textSize);
        button.setTextColor(0xFFFFFFFF);
        button.setBackgroundColor(0x88000000);
        button.setSingleLine(true);
        button.setAllCaps(false);
        return button;
    }

    private FrameLayout.LayoutParams getButtonParams() {
        if (Config.isSideCaptureButtonEnabled(this)) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END | Gravity.CENTER_VERTICAL);
            params.rightMargin = dp(16);
            return params;
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                480, 160, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = 80;
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
