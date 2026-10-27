package com.dms.webview;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CameraActivity extends Activity implements SurfaceHolder.Callback {

    private static final int CAMERA_PERMISSION_CODE = 2001;
    private static final int FOCUS_TIMEOUT_MS = 2500;
    private static final int FOCUS_AREA_HALF = 100;
    private static final int METERING_AREA_HALF = 150;
    private Camera camera;
    private SurfaceView surfaceView;
    private FrameLayout root;
    private android.widget.Button captureBtn;
    private boolean previewRunning = false;
    private int currentCameraId = -1;
    private SurfaceHolder holder;
    private int surfaceW;
    private int surfaceH;
    private boolean focusPending;
    private boolean captureAfterFocus;
    private View focusRing;
    private final Handler focusHandler = new Handler(Looper.getMainLooper());
    private OrientationEventListener orientationListener;

    private final Runnable hideRingRunnable = this::hideFocusRing;

    private final Runnable resetFocusRunnable = () -> {
        focusPending = false;
        try {
            if (camera != null) camera.cancelAutoFocus();
        } catch (RuntimeException ignored) {}
        restoreContinuousFocus();
        hideFocusRing();
        runPendingCapture();
    };

    private final Camera.AutoFocusCallback autoFocusCallback = (success, cam) -> {
        focusPending = false;
        focusHandler.removeCallbacks(resetFocusRunnable);
        showFocusResult(success);
        restoreContinuousFocus();
        runPendingCapture();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        surfaceView = new SurfaceView(this);
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        surfaceView.getHolder().addCallback(this);
        surfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        captureBtn = new android.widget.Button(this);
        captureBtn.setText("CAPTURAR");
        captureBtn.setTextSize(22);
        captureBtn.setTextColor(0xFFFFFFFF);
        captureBtn.setBackgroundColor(0x88000000);
        captureBtn.setSingleLine(true);
        captureBtn.setAllCaps(false);
        root.addView(captureBtn, getButtonParams());
        captureBtn.setOnClickListener(v -> takePicture());

        android.widget.Button backBtn = new android.widget.Button(this);
        backBtn.setText("VOLTAR");
        backBtn.setTextSize(16);
        backBtn.setTextColor(0xFFFFFFFF);
        backBtn.setBackgroundColor(0x88000000);
        backBtn.setSingleLine(true);
        backBtn.setAllCaps(false);
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

        root.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                focusAt(event.getX(), event.getY());
                v.performClick();
            }
            return true;
        });

        orientationListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (camera == null || orientation == ORIENTATION_UNKNOWN) return;
                updateCameraOrientation();
            }
        };

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{ Manifest.permission.CAMERA }, CAMERA_PERMISSION_CODE);
        } else {
            openCamera();
        }
    }

    private FrameLayout.LayoutParams getButtonParams() {
        if (Config.isSideCaptureButtonEnabled(this)) {
            int margin = dp(16);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END | Gravity.CENTER_VERTICAL);
            params.rightMargin = margin;
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

    private void updateCameraOrientation() {
        if (camera == null || currentCameraId == -1) return;
        camera.setDisplayOrientation(computeDisplayRotationDegrees());
    }

    private int computeDisplayRotationDegrees() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(currentCameraId, info);

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (info.orientation + degrees) % 360;
        }
        return (info.orientation - degrees + 360) % 360;
    }

    private void focusAt(float viewX, float viewY) {
        if (camera == null || surfaceW <= 0 || surfaceH <= 0) return;

        showFocusRing((int) viewX, (int) viewY);

        // Toque em coordenadas da tela (-1000..1000) -> coordenadas do sensor
        float dx = (viewX / surfaceW) * 2000f - 1000f;
        float dy = (viewY / surfaceH) * 2000f - 1000f;
        double rad = Math.toRadians(computeDisplayRotationDegrees());
        int sx = clampCoordinate((int) Math.round(dx * Math.cos(rad) + dy * Math.sin(rad)));
        int sy = clampCoordinate((int) Math.round(-dx * Math.sin(rad) + dy * Math.cos(rad)));

        focusHandler.removeCallbacks(resetFocusRunnable);
        try {
            Camera.Parameters params = camera.getParameters();
            if (params.getMaxNumFocusAreas() > 0) {
                params.setFocusAreas(Collections.singletonList(
                    new Camera.Area(makeArea(sx, sy, FOCUS_AREA_HALF), 1000)));
            }
            if (params.getMaxNumMeteringAreas() > 0) {
                params.setMeteringAreas(Collections.singletonList(
                    new Camera.Area(makeArea(sx, sy, METERING_AREA_HALF), 1000)));
            }
            List<String> modes = params.getSupportedFocusModes();
            if (modes != null && modes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }
            camera.setParameters(params);

            focusPending = true;
            camera.autoFocus(autoFocusCallback);
            focusHandler.postDelayed(resetFocusRunnable, FOCUS_TIMEOUT_MS);
        } catch (RuntimeException e) {
            focusPending = false;
            showFocusResult(false);
            restoreContinuousFocus();
            runPendingCapture();
        }
    }

    private static int clampCoordinate(int value) {
        return Math.max(-1000, Math.min(1000, value));
    }

    private static Rect makeArea(int centerX, int centerY, int half) {
        return new Rect(
                Math.max(-1000, centerX - half),
                Math.max(-1000, centerY - half),
                Math.min(1000, centerX + half),
                Math.min(1000, centerY + half));
    }

    private void applyDefaultFocusMode() {
        if (camera == null) return;
        try {
            Camera.Parameters params = camera.getParameters();
            List<String> modes = params.getSupportedFocusModes();
            if (modes == null || modes.isEmpty()) return;

            String desired = null;
            if (modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                desired = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE;
            } else if (modes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                desired = Camera.Parameters.FOCUS_MODE_AUTO;
            }
            if (desired != null) {
                params.setFocusMode(desired);
                camera.setParameters(params);
            }
        } catch (RuntimeException ignored) {}
    }

    private void applyPhotoSettings() {
        if (camera == null) return;
        try {
            Camera.Parameters params = camera.getParameters();
            params.setJpegQuality(Config.getPhotoQuality(this));
            Camera.Size largest = null;
            for (Camera.Size size : params.getSupportedPictureSizes()) {
                if (largest == null || (long) size.width * size.height > (long) largest.width * largest.height) {
                    largest = size;
                }
            }
            if (largest != null) {
                params.setPictureSize(largest.width, largest.height);
            }
            camera.setParameters(params);
        } catch (RuntimeException ignored) {}
    }

    private void restoreContinuousFocus() {
        if (camera == null) return;
        try {
            Camera.Parameters params = camera.getParameters();
            List<String> modes = params.getSupportedFocusModes();
            if (modes != null && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                camera.setParameters(params);
            } else {
                camera.cancelAutoFocus();
            }
        } catch (RuntimeException ignored) {}
    }

    private void showFocusRing(int x, int y) {
        if (focusRing == null) {
            focusRing = new View(this);
            focusRing.setBackground(getResources().getDrawable(R.drawable.focus_ring, getTheme()));
            focusRing.setVisibility(View.INVISIBLE);
            root.addView(focusRing);
        }
        int size = (int) (84 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.leftMargin = x - size / 2;
        params.topMargin = y - size / 2;
        focusRing.setLayoutParams(params);
        focusRing.setBackgroundTintList(ColorStateList.valueOf(0xFFFFFFFF));
        focusRing.animate().cancel();
        focusRing.setAlpha(1f);
        focusRing.setVisibility(View.VISIBLE);

        focusHandler.removeCallbacks(hideRingRunnable);
        focusHandler.postDelayed(hideRingRunnable, FOCUS_TIMEOUT_MS);
    }

    private void showFocusResult(boolean success) {
        if (focusRing == null) return;
        focusRing.setBackgroundTintList(ColorStateList.valueOf(
                success ? 0xFF3DDC84 : 0xFFFF5252));
    }

    private void hideFocusRing() {
        if (focusRing == null) return;
        focusRing.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> focusRing.setVisibility(View.INVISIBLE))
                .start();
    }

    private void runPendingCapture() {
        if (!captureAfterFocus) return;
        captureAfterFocus = false;
        captureNow();
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
        if (camera != null) return;
        try {
            int back = -1, front = -1, any = -1;
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) back = i;
                else if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) front = i;
                any = i;
            }
            currentCameraId = back != -1 ? back : front != -1 ? front : any;
            if (currentCameraId == -1) throw new Exception("Nenhuma câmera encontrada");
            camera = Camera.open(currentCameraId);
            updateCameraOrientation();
            applyDefaultFocusMode();
            applyPhotoSettings();
            startPreviewIfReady();
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao abrir câmera", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private void startPreviewIfReady() {
        if (camera == null || holder == null) return;
        try {
            camera.setPreviewDisplay(holder);
            camera.startPreview();
            previewRunning = true;
        } catch (IOException e) {
            Toast.makeText(this, "Erro ao iniciar preview", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        this.holder = holder;
        Rect frame = holder.getSurfaceFrame();
        surfaceW = frame.width();
        surfaceH = frame.height();
        startPreviewIfReady();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        this.holder = holder;
        surfaceW = width;
        surfaceH = height;
        if (previewRunning && camera != null) {
            try {
                camera.stopPreview();
            } catch (Exception ignored) {}
            previewRunning = false;
        }
        startPreviewIfReady();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        this.holder = null;
        releaseCamera();
    }

    private void takePicture() {
        if (camera == null) return;
        if (focusPending) {
            // Espera o resultado do foco antes de capturar
            captureAfterFocus = true;
            return;
        }
        captureNow();
    }

    private void captureNow() {
        if (camera == null) return;
        try {
            camera.takePicture(null, null, (data, cam) -> {
                File photoFile = createPhotoFile();
                if (photoFile != null) {
                    try (FileOutputStream fos = new FileOutputStream(photoFile)) {
                        fos.write(data);
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("photo_path", photoFile.getAbsolutePath());
                        setResult(RESULT_OK, resultIntent);
                    } catch (IOException e) {
                        setResult(RESULT_CANCELED);
                    }
                } else {
                    setResult(RESULT_CANCELED);
                }
                finish();
            });
        } catch (RuntimeException e) {
            Toast.makeText(this, "Erro ao capturar foto", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
        }
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

    private void releaseCamera() {
        focusHandler.removeCallbacksAndMessages(null);
        focusPending = false;
        captureAfterFocus = false;
        hideFocusRing();
        if (camera != null) {
            try {
                if (previewRunning) camera.stopPreview();
                camera.release();
            } catch (Exception ignored) {}
            camera = null;
            previewRunning = false;
            currentCameraId = -1;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (orientationListener != null) orientationListener.disable();
        releaseCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (orientationListener != null) orientationListener.enable();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        }
    }
}
