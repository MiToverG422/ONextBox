package com.mi.onextbox.tools;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.view.Display;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SurfaceFlingerModeTool {
    private static final String SURFACE_FLINGER = "SurfaceFlinger";
    private static final String SURFACE_COMPOSER_TOKEN = "android.ui.ISurfaceComposer";
    private static final int TRANSACTION_SET_ACTIVE_CONFIG = 1035;
    private static final int TRANSACTION_FORCE_REFRESH_RATE = 1034;
    private static final long DUMPSYS_TIMEOUT_SECONDS = 5L;
    private static final long PROCESS_DESTROY_GRACE_MS = 250L;

    private SurfaceFlingerModeTool() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(2);
            return;
        }

        try {
            if ("set".equals(args[0])) {
                setModeIndex(Integer.parseInt(args[1]));
                return;
            }
            if ("reset".equals(args[0])) {
                resetMode();
                return;
            }
            if ("force".equals(args[0]) && args.length >= 2) {
                setForceRefreshEnabled("1".equals(args[1]) || "true".equalsIgnoreCase(args[1]));
                return;
            }
            if ("force-status".equals(args[0])) {
                System.out.println(isForceRefreshEnabled() ? "1" : "0");
                return;
            }
            if ("list".equals(args[0])) {
                for (String line : getSupportedModeLines(createSystemContext())) {
                    System.out.println(line);
                }
                return;
            }
            if ("set-detected".equals(args[0]) && args.length >= 4) {
                setDetectedMode(
                        Integer.parseInt(args[1]),
                        Integer.parseInt(args[2]),
                        Float.parseFloat(args[3])
                );
                return;
            }
            if ("set-rate".equals(args[0]) && args.length >= 2) {
                setCurrentResolutionRate(Float.parseFloat(args[1]));
                return;
            }
            printUsage();
            System.exit(2);
        } catch (NumberFormatException e) {
            System.err.println("Invalid argument: " + e.getMessage());
            System.exit(2);
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.err.println("Usage: SurfaceFlingerModeTool set <modeIndex>");
        System.err.println("       SurfaceFlingerModeTool reset");
        System.err.println("       SurfaceFlingerModeTool force <0|1>");
        System.err.println("       SurfaceFlingerModeTool force-status");
        System.err.println("       SurfaceFlingerModeTool list");
        System.err.println("       SurfaceFlingerModeTool set-detected <width> <height> <refreshRate>");
        System.err.println("       SurfaceFlingerModeTool set-rate <refreshRate>");
    }

    private static void setDetectedMode(int width, int height, float refreshRate) throws Exception {
        List<ModeInfo> modes = readModes();
        if (modes.isEmpty()) {
            throw new IllegalStateException("No hidden display modes");
        }
        ModeInfo matched = null;
        for (ModeInfo mode : modes) {
            if (mode == null) continue;
            if (mode.width == width && mode.height == height && Math.abs(mode.refreshRate - refreshRate) < 0.5f) {
                matched = mode;
                break;
            }
        }
        if (matched == null) {
            throw new IllegalStateException("No matched display mode for " + width + "x" + height + "@" + refreshRate);
        }
        setModeIndex(matched.surfaceFlingerIndex);
        System.out.println("Detected mode: id=" + matched.id
                + ", index=" + matched.surfaceFlingerIndex
                + ", " + matched.width + "x" + matched.height
                + "@" + matched.refreshRate);
    }

    private static void setCurrentResolutionRate(float refreshRate) throws Exception {
        Context context = createSystemContext();
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager == null) {
            throw new IllegalStateException("DisplayManager unavailable");
        }
        Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null || display.getMode() == null) {
            throw new IllegalStateException("Default display mode unavailable");
        }
        Display.Mode currentMode = display.getMode();
        setDetectedMode(currentMode.getPhysicalWidth(), currentMode.getPhysicalHeight(), refreshRate);
    }

    public static String[] getSupportedModeLines(Context context) throws Exception {
        List<ModeInfo> modes = readModes(context);
        String[] lines = new String[modes.size()];
        for (int index = 0; index < modes.size(); index++) {
            ModeInfo mode = modes.get(index);
            lines[index] = "id=" + mode.id
                    + ", config=" + mode.surfaceFlingerIndex
                    + ", " + mode.width + "x" + mode.height
                    + "@" + mode.refreshRate;
        }
        return lines;
    }

    private static List<ModeInfo> readHiddenModes(Context context) throws Exception {
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager == null) {
            throw new IllegalStateException("DisplayManager unavailable");
        }
        Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) {
            throw new IllegalStateException("Default display unavailable");
        }

        Class<?> displayInfoClass = Class.forName("android.view.DisplayInfo");
        Object displayInfo = displayInfoClass.getDeclaredConstructor().newInstance();
        Method getDisplayInfo = Display.class.getDeclaredMethod("getDisplayInfo", displayInfoClass);
        getDisplayInfo.setAccessible(true);
        Boolean hasInfo = (Boolean) getDisplayInfo.invoke(display, displayInfo);
        if (!Boolean.TRUE.equals(hasInfo)) {
            throw new IllegalStateException("Display#getDisplayInfo returned false");
        }

        Object[] rawModes = null;
        for (String fieldName : new String[]{"supportedDisplayModes", "supportedModes"}) {
            try {
                java.lang.reflect.Field field = displayInfoClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(displayInfo);
                if (value instanceof Object[]) {
                    rawModes = (Object[]) value;
                    break;
                }
            } catch (NoSuchFieldException ignored) {
            }
        }
        if (rawModes == null) {
            throw new IllegalStateException("supportedDisplayModes field unavailable");
        }

        ArrayList<ModeInfo> modes = new ArrayList<>();
        for (int index = 0; index < rawModes.length; index++) {
            Object mode = rawModes[index];
            if (mode == null) continue;
            ModeInfo info = new ModeInfo();
            info.id = readInt(mode, "id", "modeId", "mModeId");
            info.width = readInt(mode, "width", "physicalWidth", "mWidth");
            info.height = readInt(mode, "height", "physicalHeight", "mHeight");
            // Android 16's Display.Mode stores the rate as mPeakRefreshRate.
            info.refreshRate = readFloat(mode,
                    "refreshRate", "mRefreshRate", "peakRefreshRate", "mPeakRefreshRate",
                    "vsyncRate", "mVsyncRate");

            // Transaction 1035 takes the native configuration position from
            // DisplayInfo.supportedDisplayModes, not Android's Display.Mode ID.
            // The two sequences are offset on this device (ID 1 is config 0).
            info.surfaceFlingerIndex = index;
            modes.add(info);
        }
        return modes;
    }

    private static List<ModeInfo> readModes() throws Exception {
        return readModes(createSystemContext());
    }

    private static List<ModeInfo> readModes(Context context) throws Exception {
        List<ModeInfo> dumpsysModes = readSurfaceFlingerDumpsysModes();
        if (!dumpsysModes.isEmpty()) {
            return dumpsysModes;
        }
        List<ModeInfo> surfaceControlModes = readSurfaceControlModes();
        return surfaceControlModes.isEmpty() ? readHiddenModes(context) : surfaceControlModes;
    }

    private static List<ModeInfo> readSurfaceFlingerDumpsysModes() {
        Pattern modePattern = Pattern.compile(
                "HwcConfigIndex:(\\d+).*?fps:([0-9.]+).*?id:(\\d+).*?WxH=(\\d+)x(\\d+)"
        );
        ArrayList<ModeInfo> modes = new ArrayList<>();
        HashSet<Integer> seenConfigIndices = new HashSet<>();
        Process process = null;
        Thread timeoutThread = null;
        AtomicBoolean timedOut = new AtomicBoolean(false);
        try {
            process = new ProcessBuilder("sh", "-c", "dumpsys SurfaceFlinger")
                    .redirectErrorStream(true)
                    .start();
            Process watchedProcess = process;
            timeoutThread = new Thread(() -> {
                try {
                    if (!watchedProcess.waitFor(DUMPSYS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        timedOut.set(true);
                        watchedProcess.destroy();
                        if (!watchedProcess.waitFor(PROCESS_DESTROY_GRACE_MS, TimeUnit.MILLISECONDS)) {
                            watchedProcess.destroyForcibly();
                        }
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }, "FluidBox-SurfaceFlingerTimeout");
            timeoutThread.setDaemon(true);
            timeoutThread.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher match = modePattern.matcher(line);
                    if (!match.find()) continue;
                    ModeInfo info = new ModeInfo();
                    info.surfaceFlingerIndex = Integer.parseInt(match.group(1));
                    if (!seenConfigIndices.add(info.surfaceFlingerIndex)) continue;
                    info.refreshRate = Float.parseFloat(match.group(2));
                    info.id = Integer.parseInt(match.group(3));
                    info.width = Integer.parseInt(match.group(4));
                    info.height = Integer.parseInt(match.group(5));
                    modes.add(info);
                }
            }
            boolean finished = process.waitFor(PROCESS_DESTROY_GRACE_MS, TimeUnit.MILLISECONDS);
            if (timedOut.get() || !finished || process.exitValue() != 0) {
                modes.clear();
            }
        } catch (Throwable error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            modes.clear();
        } finally {
            if (timeoutThread != null) {
                timeoutThread.interrupt();
            }
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
        return modes;
    }

    @SuppressLint("BlockedPrivateApi")
    private static List<ModeInfo> readSurfaceControlModes() {
        try {
            Class<?> surfaceControlClass = Class.forName("android.view.SurfaceControl");
            Method getPhysicalDisplayIds = surfaceControlClass.getDeclaredMethod("getPhysicalDisplayIds");
            getPhysicalDisplayIds.setAccessible(true);
            long[] displayIds = (long[]) getPhysicalDisplayIds.invoke(null);
            if (displayIds == null || displayIds.length == 0) {
                return new ArrayList<>();
            }

            Method getPhysicalDisplayToken = surfaceControlClass.getDeclaredMethod(
                    "getPhysicalDisplayToken", long.class
            );
            getPhysicalDisplayToken.setAccessible(true);
            IBinder token = (IBinder) getPhysicalDisplayToken.invoke(null, displayIds[0]);
            if (token == null) {
                return new ArrayList<>();
            }

            Method getDynamicDisplayInfo = surfaceControlClass.getDeclaredMethod(
                    "getDynamicDisplayInfo", IBinder.class
            );
            getDynamicDisplayInfo.setAccessible(true);
            Object dynamicInfo = getDynamicDisplayInfo.invoke(null, token);
            if (dynamicInfo == null) {
                return new ArrayList<>();
            }

            Field modesField = dynamicInfo.getClass().getDeclaredField("supportedDisplayModes");
            modesField.setAccessible(true);
            Object rawValue = modesField.get(dynamicInfo);
            if (!(rawValue instanceof Object[])) {
                return new ArrayList<>();
            }

            ArrayList<ModeInfo> modes = new ArrayList<>();
            for (Object mode : (Object[]) rawValue) {
                if (mode == null) continue;
                ModeInfo info = new ModeInfo();
                info.id = readInt(mode, "id", "modeId", "mModeId");
                info.surfaceFlingerIndex = info.id;
                info.width = readInt(mode, "width", "physicalWidth", "mWidth");
                info.height = readInt(mode, "height", "physicalHeight", "mHeight");
                info.refreshRate = readFloat(mode,
                        "refreshRate", "mRefreshRate", "peakRefreshRate", "mPeakRefreshRate",
                        "vsyncRate", "mVsyncRate");
                modes.add(info);
            }
            return modes;
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }

    private static Context createSystemContext() throws Exception {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Object activityThread = activityThreadClass.getDeclaredMethod("systemMain").invoke(null);
        Method getSystemContext = activityThreadClass.getDeclaredMethod("getSystemContext");
        getSystemContext.setAccessible(true);
        return (Context) getSystemContext.invoke(activityThread);
    }

    private static int readInt(Object instance, String... names) throws Exception {
        for (String name : names) {
            try {
                java.lang.reflect.Field field = instance.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(instance);
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException("int field not found");
    }

    private static float readFloat(Object instance, String... names) throws Exception {
        for (String name : names) {
            try {
                java.lang.reflect.Field field = instance.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(instance);
                if (value instanceof Number) {
                    return ((Number) value).floatValue();
                }
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException("float field not found");
    }

    public static void setModeIndex(int modeIndex) throws Exception {
        IBinder surfaceFlinger = getService(SURFACE_FLINGER);
        if (surfaceFlinger == null) {
            throw new IllegalStateException("SurfaceFlinger service not found");
        }

        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(SURFACE_COMPOSER_TOKEN);
            data.writeInt(modeIndex);
            boolean ok = surfaceFlinger.transact(TRANSACTION_SET_ACTIVE_CONFIG, data, null, 0);
            if (!ok) {
                throw new IllegalStateException("SurfaceFlinger transact returned false");
            }
        } finally {
            data.recycle();
        }

        System.out.println("SurfaceFlinger mode index set: " + modeIndex);
    }

    public static void resetMode() throws Exception {
        try {
            setModeIndex(-1);
        } catch (IllegalArgumentException ignored) {
        }
        System.out.println("SurfaceFlinger default refresh requested");
    }

    public static void setForceRefreshEnabled(boolean enabled) throws Exception {
        IBinder surfaceFlinger = getService(SURFACE_FLINGER);
        if (surfaceFlinger == null) {
            throw new IllegalStateException("SurfaceFlinger service not found");
        }

        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(SURFACE_COMPOSER_TOKEN);
            data.writeInt(enabled ? 1 : 0);
            boolean ok = surfaceFlinger.transact(TRANSACTION_FORCE_REFRESH_RATE, data, null, 0);
            if (!ok) {
                throw new IllegalStateException("SurfaceFlinger force refresh transact returned false");
            }
        } finally {
            data.recycle();
        }
        System.out.println("SurfaceFlinger force refresh enabled: " + enabled);
    }

    public static boolean isForceRefreshEnabled() throws Exception {
        IBinder surfaceFlinger = getService(SURFACE_FLINGER);
        if (surfaceFlinger == null) {
            throw new IllegalStateException("SurfaceFlinger service not found");
        }

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SURFACE_COMPOSER_TOKEN);
            data.writeInt(2);
            boolean ok = surfaceFlinger.transact(TRANSACTION_FORCE_REFRESH_RATE, data, reply, 0);
            if (!ok) {
                throw new IllegalStateException("SurfaceFlinger force refresh query returned false");
            }
            return reply.readInt() != 0;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static IBinder getService(String name) throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method getService = serviceManager.getDeclaredMethod("getService", String.class);
        getService.setAccessible(true);
        return (IBinder) getService.invoke(null, name);
    }

    private static final class ModeInfo {
        int id;
        int surfaceFlingerIndex;
        int width;
        int height;
        float refreshRate;
    }
}