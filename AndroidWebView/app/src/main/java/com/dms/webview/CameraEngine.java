package com.dms.webview;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;

public final class CameraEngine {

    private CameraEngine() {}

    public static int hardwareLevel(Context context) {
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    return level != null ? level : CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
                }
            }
        } catch (CameraAccessException | RuntimeException ignored) {}
        return CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
    }

    public static boolean isSupported(Context context) {
        int level = hardwareLevel(context);
        return level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
                || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
                || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
                || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL;
    }

    public static boolean isRecommended(Context context) {
        int level = hardwareLevel(context);
        return level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
                || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
                || level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL;
    }

    public static String levelName(int level) {
        switch (level) {
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL: return "FULL";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3: return "LEVEL_3";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED: return "LIMITED";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY: return "LEGACY";
            case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL: return "EXTERNAL";
            default: return "desconhecido";
        }
    }
}
