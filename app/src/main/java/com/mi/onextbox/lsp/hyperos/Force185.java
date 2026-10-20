package com.mi.onextbox.lsp.hyperos;

import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import com.mi.onextbox.lsp.compat.ModernHookRuntime;
import com.mi.onextbox.lsp.compat.ModernHookRegistry;
import com.mi.onextbox.lsp.compat.AdaptationHookGate;
import static io.github.libxposed.api.XposedInterface.*;

/** ROM-specific system_server policy. Does not fake Display#getRefreshRate or modify panel modes. */
public final class Force185 {
    private static final String TAG = "ONextBox-HyperOS185";
    private static final String ROM = "OS3.0.310.0.WGNCNXM";
    private static final float TARGET = 185f;
    private final ConcurrentHashMap<String, Field> fields = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Method> methods = new ConcurrentHashMap<>();
    private final AtomicBoolean errorLogged = new AtomicBoolean();
    private volatile Object localDevice;
    private volatile Object displayToken;
    private volatile Mode highMode;
    private volatile Mode lockMode;
    private volatile boolean keyguardShowing = true;
    private volatile Object directorInstance;
    private volatile long physicalDisplayId;
    private final AtomicBoolean nativeAppliedLogged = new AtomicBoolean();
    private final AtomicBoolean vendorLockLogged = new AtomicBoolean();
    private final AtomicBoolean nativeLockPending = new AtomicBoolean();
    private volatile boolean ready;
    private int hookCount;
    private final boolean refreshEnabled;
    private final boolean touchEnabled;
    private final boolean idleDisabled;
    private final TouchRateLock touch;

    public Force185(boolean refreshEnabled, boolean touchEnabled, boolean idleDisabled) {
        this.refreshEnabled = refreshEnabled;
        this.touchEnabled = touchEnabled;
        this.idleDisabled = idleDisabled;
        this.touch = new TouchRateLock(touchEnabled, this::info,
                error -> log(Log.ERROR, TAG, "Touch profile failed", error));
    }

    public void installSystem(ClassLoader loader) {
        if (!supported()) return;
        installTouchPolicy(loader, false);
        try {
            Class<?> keyguard = Class.forName("com.android.server.policy.keyguard.KeyguardStateMonitor", false, loader);
            for (Constructor<?> ctor : keyguard.getDeclaredConstructors()) {
                installExecutable(ctor, "keyguard-initial-state", chain -> {
                    Object result = chain.proceed();
                    safely(() -> keyguardShowing = (Boolean) get(chain.getThisObject(), "mIsShowing"));
                    return result;
                });
                hookCount++;
            }
            install(keyguard, "onShowingStateChanged", chain -> {
                Object result = chain.proceed();
                safely(() -> {
                    boolean showing = (Boolean) get(chain.getThisObject(), "mIsShowing");
                    if (keyguardShowing != showing) {
                        keyguardShowing = showing;
                        info("Keyguard=" + showing);
                        refreshPolicy();
                        touch.schedule();
                    }
                });
                return result;
            });
            Class<?> device = Class.forName("com.android.server.display.LocalDisplayAdapter$LocalDisplayDevice", false, loader);
            for (Constructor<?> ctor : device.getDeclaredConstructors()) {
                installExecutable(ctor, "capture-panel", chain -> {
                    Object result = chain.proceed();
                    safely(() -> capture(chain.getThisObject()));
                    return result;
                });
                hookCount++;
            }
            install(device, "setDesiredDisplayModeSpecsLocked", chain -> {
                safely(() -> {
                    capture(chain.getThisObject());
                    if (isOurDevice(chain.getThisObject()) && policyEnabled()) lockDirectorSpec(chain.getArg(0));
                });
                return chain.proceed();
            });
            install(device, "updateDesiredDisplayModeSpecsLocked", chain -> {
                safely(() -> {
                    capture(chain.getThisObject());
                    if (isOurDevice(chain.getThisObject()) && policyEnabled()) lockDirectorSpec(chain.getArg(0));
                });
                return chain.proceed();
            });
            install(device, "setDesiredDisplayModeSpecsAsync", chain -> {
                safely(() -> {
                    capture(chain.getThisObject());
                    if (isOurDevice(chain.getThisObject()) && policyEnabled()) lockSurfaceSpec(chain.getArg(1));
                });
                return chain.proceed();
            });
            install(device, "requestDisplayStateLocked", chain -> {
                Object owner = chain.getThisObject();
                int previous = (Integer) get(owner, "mState");
                Object result = chain.proceed();
                safely(() -> {
                    capture(owner);
                    if (isOurDevice(owner) && previous != (Integer) get(owner, "mState") && policyEnabled()) reapply(owner);
                    if (isOurDevice(owner) && previous != (Integer) get(owner, "mState")) touch.schedule();
                    if (isOurDevice(owner) && enabled()) scheduleNativeLock();
                });
                return result;
            });

            // FOD/HWC can overwrite the compositor without making any Java policy request.
            // Observe the real native mode event, then repair through Xiaomi's runtime lock API.
            Class<?> events = Class.forName("com.android.server.display.LocalDisplayAdapter$LocalDisplayEventListener", false, loader);
            install(events, "onModeChanged", chain -> {
                Object result = chain.proceed();
                safely(() -> {
                    if (ready && ((Long) chain.getArg(1)) == physicalDisplayId && enabled()) {
                        int modeId = (Integer) chain.getArg(2);
                        long period = (Long) chain.getArg(3);
                        if (modeId != highMode.nativeId || (period > 0 && Math.abs(1e9 / period - TARGET) > 0.5))
                            lockVendorDisplay();
                    }
                });
                return result;
            });

            Class<?> director = Class.forName("com.android.server.display.mode.DisplayModeDirector", false, loader);
            install(director, "getDesiredDisplayModeSpecs", chain -> {
                Object result = chain.proceed();
                directorInstance = chain.getThisObject();
                if (((Integer) chain.getArg(0)) == Display.DEFAULT_DISPLAY) {
                    safely(() -> {
                        if (policyEnabled()) lockDirectorSpec(result);
                    });
                }
                return result;
            });

            // This is the final Java boundary used by both AOSP and Xiaomi's direct SmartPower path.
            Class<?> surface = Class.forName("android.view.SurfaceControl", false, loader);
            install(surface, "setDesiredDisplayModeSpecs", chain -> {
                safely(() -> {
                    if (displayToken != null && displayToken.equals(chain.getArg(0)) && policyEnabled())
                        lockSurfaceSpec(chain.getArg(1));
                });
                return chain.proceed();
            });
            install(surface, "nativeSetDesiredDisplayModeSpecs", chain -> {
                safely(() -> {
                    if (displayToken != null && displayToken.equals(chain.getArg(0)) && policyEnabled())
                        lockSurfaceSpec(chain.getArg(1));
                });
                Object result = chain.proceed();
                if (Boolean.TRUE.equals(result) && enabled() && displayToken.equals(chain.getArg(0))
                        && nativeAppliedLogged.compareAndSet(false, true))
                    info("Native compositor accepted policy: " + chain.getArg(1));
                return result;
            });
            install(surface, "nativeSetDisplayPowerMode", chain -> {
                Object[] args = chain.getArgs().toArray();
                // SurfaceFlinger NORMAL=2. Keep the AOD surface visible but prevent panel DOZE
                // and DOZE_SUSPEND from selecting an independent 30/60Hz low-power mode.
                // OFF=0 remains OFF: this does not turn on a fully powered-off display.
                if (refreshEnabled && ready && displayToken.equals(args[0]) && ((Integer) args[1] == 1 || (Integer) args[1] == 3)) {
                    args[1] = 2;
                    info("AOD panel low-power refresh disabled; using NORMAL power mode at 185Hz");
                }
                Object result = chain.proceed(args);
                safely(() -> { if (refreshEnabled && ready && displayToken.equals(args[0]) && (Integer) args[1] != 0) scheduleNativeLock(); });
                return result;
            });

            Class<?> policy = Class.forName("com.android.server.wm.RefreshRatePolicy", false, loader);
            for (String name : new String[]{"getPreferredMinRefreshRate", "getPreferredMaxRefreshRate"}) {
                install(policy, name, chain -> {
                    if (windowOnPanel(chain.getArg(0)) && enabled()) return selected().hz;
                    return chain.proceed();
                });
            }
            install(policy, "getPreferredModeId", chain -> {
                if (windowOnPanel(chain.getArg(0)) && enabled()) return selected().frameworkId;
                return chain.proceed();
            });
            install(policy, "updateFrameRateVote", chain -> {
                if (windowOnPanel(chain.getArg(0)) && enabled()) {
                    try {
                        Object vote = get(chain.getArg(0), "mFrameRateVote");
                        // Use the system's change detection; returning true unconditionally would cause a traversal loop.
                        return method(vote.getClass(), "update", float.class, int.class, int.class)
                                .invoke(vote, selected().hz, 100, 1);
                    } catch (Throwable error) { failure(error); }
                }
                return chain.proceed();
            });
            info("Installed " + hookCount + " hooks; refresh=" + refreshEnabled + ", touch=" + touchEnabled);
        } catch (Throwable error) { failure(error); }
    }

    public static boolean supported() {
        return supported(Build.DEVICE, Build.VERSION.SDK_INT, Build.VERSION.INCREMENTAL);
    }

    public static boolean supported(String device, int sdk, String rom) {
        return "songyuan".equals(device) && sdk == 36 && ROM.equals(rom);
    }

    public void installPackage(String name, ClassLoader loader) {
        if (!supported() || !touchEnabled) return;
        if ("com.xiaomi.touchservice".equals(name) || "com.miui.securitycenter".equals(name)) {
            installTouchPolicy(loader, "com.xiaomi.touchservice".equals(name));
        }
    }

    public void startTouch() { touch.schedule(); }

    private void installTouchPolicy(ClassLoader loader, boolean ownTouchClass) {
        if (!touchEnabled) return;
        try {
            hookTouchClass(Class.forName("miui.util.ITouchFeature", false, loader));
            if (ownTouchClass) hookTouchClass(Class.forName("com.xiaomi.touchservice.ITouchFeature", false, loader));
            if (!ownTouchClass && localDevice == null) {
                // Only system_server contains DisplayContent. Reapply on actual focus changes.
                try {
                    Class<?> content = Class.forName("com.android.server.wm.DisplayContent", false, loader);
                    install(content, "updateFocusedWindowLocked", chain -> {
                        Object owner = chain.getThisObject();
                        Object previous = get(owner, "mCurrentFocus");
                        Object result = chain.proceed();
                        safely(() -> {
                            if ((Integer) get(owner, "mDisplayId") == 0
                                    && previous != get(owner, "mCurrentFocus")) touch.schedule();
                        });
                        return result;
                    });
                } catch (ClassNotFoundException ignored) { }
            }
            info("Touch profile hooks loaded in " + android.app.Application.getProcessName());
        } catch (Throwable error) { log(Log.ERROR, TAG, "Touch hook compatibility failure", error); }
    }

    private void hookTouchClass(Class<?> type) throws Exception {
        install(type, "setTouchMode", chain -> {
            Object[] args = chain.getArgs().toArray();
            int index = args.length - 2;
            int id = args.length == 3 ? (Integer) args[0] : 0;
            int mode = (Integer) args[index];
            if (id == 0) args[index + 1] = TouchRateLock.forceValue(mode, (Integer) args[index + 1]);
            Object result = chain.proceed(args);
            if (id == 0 && TouchRateLock.affectsRate(mode)) touch.schedule();
            return result;
        });
        for (Method candidate : type.getDeclaredMethods()) {
            if (!candidate.getName().equals("resetTouchMode")) continue;
            installExecutable(candidate, "touch480:reset", chain -> {
                Object result = chain.proceed();
                touch.schedule();
                return result;
            });
            hookCount++;
        }
    }

    private void install(Class<?> type, String name, XposedInterface.Hooker interceptor) throws Exception {
        int found = 0;
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && !method.isSynthetic()) {
                installExecutable(method, "force185:" + name, interceptor);
                hookCount++;
                found++;
            }
        }
        if (found == 0) throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private void installExecutable(Executable target, String name, XposedInterface.Hooker interceptor) {
        ModernHookRegistry.INSTANCE.installFast("hyperos185:" + name + ":" + target.toGenericString(), target, interceptor);
    }

    private Display.Mode choose(Display.Mode[] modes, float hz) {
        if (modes == null) return null;
        for (Display.Mode mode : modes) {
            if (mode.getPhysicalWidth() == 1200 && mode.getPhysicalHeight() == 2608
                    && Math.abs(mode.getRefreshRate() - hz) < 0.1f) return mode;
        }
        return null;
    }

    private void capture(Object candidate) throws Exception {
        if (ready && candidate == localDevice) return;
        if (candidate == null || !(Boolean) get(candidate, "mIsFirstDisplay")) return;
        if (!(Boolean) get(get(candidate, "mStaticDisplayInfo"), "isInternal")) return;
        SparseArray<?> records = (SparseArray<?>) get(candidate, "mSupportedModes");
        Display.Mode[] modes = new Display.Mode[records.size()];
        for (int i = 0; i < modes.length; i++) modes[i] = (Display.Mode) get(records.valueAt(i), "mMode");
        Display.Mode high = choose(modes, TARGET);
        Display.Mode low = choose(modes, 120f);
        if (high == null || low == null) return;
        Object[] sfModes = (Object[]) get(candidate, "mSfDisplayModes");
        int highNativeId = -1;
        int lowNativeId = -1;
        for (Object mode : sfModes) {
            if ((Integer) get(mode, "width") == 1200 && (Integer) get(mode, "height") == 2608) {
                float hz = (Float) get(mode, "peakRefreshRate");
                if (Math.abs(hz - TARGET) < 0.1f) highNativeId = (Integer) get(mode, "id");
                if (Math.abs(hz - 120f) < 0.1f) lowNativeId = (Integer) get(mode, "id");
            }
        }
        if (highNativeId < 0 || lowNativeId < 0) return;
        highMode = new Mode(high.getModeId(), highNativeId, high.getRefreshRate());
        lockMode = new Mode(low.getModeId(), lowNativeId, low.getRefreshRate());
        displayToken = method(candidate.getClass(), "getDisplayTokenLocked").invoke(candidate);
        physicalDisplayId = (Long) get(candidate, "mPhysicalDisplayId");
        localDevice = candidate;
        if (!ready) {
            ready = true;
            info("Native panel verified: " + highMode.hz + "Hz; refresh override=" + refreshEnabled);
            // Use Xiaomi's ROM-verified native switch instead of faking an LTPO support flag.
            Object adapter = get(candidate, "this$0");
            ((Handler) get(adapter, "mHandler")).post(() -> safely(() -> { disableVendorIdle(); scheduleNativeLock(); }));
            touch.schedule();
        }
    }

    private boolean isOurDevice(Object device) { return ready && device == localDevice; }

    private boolean enabled() {
        return refreshEnabled && policyEnabled();
    }

    private boolean policyEnabled() {
        if (!AdaptationHookGate.isHyperOsActive()) return false;
        if ((!refreshEnabled && !idleDisabled) || !ready) return false;
        try {
            int state = (Integer) get(localDevice, "mState");
            return state == Display.STATE_ON || state == Display.STATE_DOZE || state == Display.STATE_DOZE_SUSPEND;
        }
        catch (Throwable error) { failure(error); return false; }
    }

    private Mode selected() {
        return highMode;
    }

    private IBinder compositor() throws Exception {
        IBinder service = (IBinder) Class.forName("android.os.ServiceManager")
                .getDeclaredMethod("getService", String.class).invoke(null, "SurfaceFlinger");
        if (service == null) throw new IllegalStateException("SurfaceFlinger unavailable");
        return service;
    }

    private void lockVendorDisplay() throws Exception {
        if (!enabled()) return;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("android.ui.ISurfaceComposer");
            data.writeInt(185);
            data.writeLong(physicalDisplayId);
            data.writeInt(0); // native DSI mode group, already verified against this panel
            data.writeInt(0); // no invented DDIC/self-refresh mode
            if (!compositor().transact(31117, data, reply, 0)) throw new IllegalStateException("Native display lock rejected");
            if (vendorLockLogged.compareAndSet(false, true)) info("Xiaomi native display lock active: 185Hz (31117), no root helper");
        } finally { data.recycle(); reply.recycle(); }
    }

    private void scheduleNativeLock() throws Exception {
        if (!refreshEnabled || !ready || !nativeLockPending.compareAndSet(false, true)) return;
        Object adapter = get(localDevice, "this$0");
        Handler handler = (Handler) get(adapter, "mHandler");
        if (!handler.post(() -> {
            nativeLockPending.set(false);
            safely(this::lockVendorDisplay);
        })) nativeLockPending.set(false);
    }

    private void disableVendorIdle() throws Exception {
        if (!idleDisabled) return;
        if (!AdaptationHookGate.isHyperOsActive()) return;
        // libmisurfaceflinger.so on this exact ROM dispatches 31108 to forceSkipIdleTimer(bool).
        IBinder compositor = compositor();
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("android.ui.ISurfaceComposer");
            data.writeInt(1);
            if (!compositor.transact(31108, data, reply, 0)) throw new IllegalStateException("Vendor idle switch rejected");
            info("Xiaomi native forceSkipIdleTimer enabled (31108)");
        } finally { data.recycle(); reply.recycle(); }
    }

    private static final class Mode {
        final int frameworkId, nativeId;
        final float hz;
        Mode(int frameworkId, int nativeId, float hz) { this.frameworkId = frameworkId; this.nativeId = nativeId; this.hz = hz; }
    }

    private void reapply(Object owner) throws Exception {
        Object saved = get(owner, "mDisplayModeSpecs");
        Object copy = saved.getClass().getDeclaredConstructor().newInstance();
        method(copy.getClass(), "copyFrom", saved.getClass()).invoke(copy, saved);
        lockDirectorSpec(copy);
        set(owner, "mDisplayModeSpecsInvalid", true);
        method(owner.getClass(), "setDesiredDisplayModeSpecsLocked", saved.getClass()).invoke(owner, copy);
    }

    private void refreshPolicy() throws Exception {
        if (!AdaptationHookGate.isHyperOsActive()) return;
        if (!refreshEnabled && !idleDisabled) return;
        Object owner = localDevice;
        if (owner == null) return;
        Object adapter = get(owner, "this$0");
        ((Handler) get(adapter, "mHandler")).post(() -> safely(() -> {
            disableVendorIdle();
            synchronized (get(adapter, "mSyncRoot")) { if (policyEnabled()) reapply(owner); }
            Object director = directorInstance;
            if (director != null) method(director.getClass(), "notifyDesiredDisplayModeSpecsChanged").invoke(director);
            lockVendorDisplay();
        }));
    }

    private boolean windowOnPanel(Object window) {
        if (!ready || window == null) return false;
        try { return (Integer) method(window.getClass(), "getDisplayId").invoke(window) == Display.DEFAULT_DISPLAY; }
        catch (Throwable error) { failure(error); return false; }
    }

    private void lockDirectorSpec(Object spec) throws Exception {
        if (spec == null || !ready) return;
        if (idleDisabled) disableIdle(spec, "mIdleScreenRefreshRateConfig");
        if (!refreshEnabled) return;
        Mode target = selected();
        set(spec, "baseModeId", target.frameworkId);
        set(spec, "allowGroupSwitching", false);
        lockRanges(get(spec, "primary"), target.hz);
        lockRanges(get(spec, "appRequest"), target.hz);
    }

    private void lockSurfaceSpec(Object spec) throws Exception {
        if (spec == null || !ready) return;
        if (idleDisabled) disableIdle(spec, "idleScreenRefreshRateConfig");
        if (!refreshEnabled) return;
        Mode target = selected();
        set(spec, "defaultMode", target.nativeId);
        set(spec, "allowGroupSwitching", false);
        lockRanges(get(spec, "primaryRanges"), target.hz);
        lockRanges(get(spec, "appRequestRanges"), target.hz);
    }

    private void disableIdle(Object spec, String name) throws Exception {
        Object config = get(spec, name);
        if (config == null) {
            Class<?> type = field(spec.getClass(), name).getType();
            config = type.getDeclaredConstructor(int.class).newInstance(0);
            set(spec, name, config);
        } else {
            set(config, "timeoutMillis", 0);
        }
    }

    private void lockRanges(Object ranges, float rate) throws Exception {
        for (String component : new String[]{"physical", "render"}) {
            Object range = get(ranges, component);
            set(range, "min", rate);
            set(range, "max", rate);
        }
    }

    private Field field(Class<?> type, String name) throws NoSuchFieldException {
        String key = type.getName() + "#" + name;
        Field cached = fields.get(key);
        if (cached != null) return cached;
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Field value = cursor.getDeclaredField(name);
                value.setAccessible(true);
                fields.put(key, value);
                return value;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(key);
    }

    private Object get(Object object, String name) throws Exception { return field(object.getClass(), name).get(object); }
    private void set(Object object, String name, Object value) throws Exception { field(object.getClass(), name).set(object, value); }

    private Method method(Class<?> type, String name, Class<?>... arguments) throws NoSuchMethodException {
        String key = type.getName() + "#" + name;
        Method cached = methods.get(key);
        if (cached != null) return cached;
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Method value = cursor.getDeclaredMethod(name, arguments);
                value.setAccessible(true);
                methods.put(key, value);
                return value;
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(key);
    }

    private interface CheckedAction { void run() throws Exception; }
    private void safely(CheckedAction action) {
        try { action.run(); } catch (Throwable error) { failure(error); }
    }
    private void failure(Throwable error) {
        if (errorLogged.compareAndSet(false, true)) log(Log.ERROR, TAG, "Hook compatibility failure; inspect this ROM", error);
    }
    private void log(int level, String tag, String message) {
        log(level, tag, message, null);
    }
    private void log(int level, String tag, String message, Throwable error) {
        ModernHookRuntime.INSTANCE.log(level, tag, message, error);
    }
    private void info(String message) { log(Log.INFO, TAG, message); }
}
