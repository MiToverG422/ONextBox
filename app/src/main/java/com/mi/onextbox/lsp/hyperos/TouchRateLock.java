package com.mi.onextbox.lsp.hyperos;

import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcel;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import com.mi.onextbox.lsp.compat.AdaptationHookGate;

/** Runtime selection of this ROM's existing 480Hz touch profile; no firmware/config edits. */
final class TouchRateLock {
    static final int GAME = 0;
    static final int SUPER_REPORT = 202;
    static final int FORCE_240 = 1116;
    static final int NORMAL_GAME_RATE = 10110;
    private static final String NAME = "vendor.xiaomi.hw.touchfeature.ITouchFeature/default";
    private static final String DESCRIPTOR = "vendor.xiaomi.hw.touchfeature.ITouchFeature";
    private final boolean enabled;
    private final Consumer<String> info;
    private final Consumer<Throwable> error;
    private final AtomicBoolean pending = new AtomicBoolean();
    private final AtomicBoolean logged = new AtomicBoolean();
    private final AtomicBoolean failed = new AtomicBoolean();
    private volatile Handler handler;
    private int incompleteAttempts;

    TouchRateLock(boolean enabled, Consumer<String> info, Consumer<Throwable> error) {
        this.enabled = enabled;
        this.info = info;
        this.error = error;
    }

    static int forceValue(int mode, int requested) {
        if (mode == GAME || mode == SUPER_REPORT) return 1;
        if (mode == FORCE_240) return 0;
        // HAL values 0/1 select normal game rate, 2/3 release that restriction.
        if (mode == NORMAL_GAME_RATE && requested >= 0 && requested <= 3) return requested | 2;
        return requested;
    }

    static boolean affectsRate(int mode) {
        return mode == GAME || mode == SUPER_REPORT || mode == FORCE_240
                || mode == NORMAL_GAME_RATE || (mode >= 10000 && mode <= 10116);
    }

    synchronized void schedule() {
        if (!enabled) return;
        if (!AdaptationHookGate.isHyperOsActive()) return;
        if (handler == null) {
            HandlerThread thread = new HandlerThread("ONextBoxTouch480");
            thread.start();
            handler = new Handler(thread.getLooper());
        }
        if (!pending.compareAndSet(false, true)) return;
        if (!handler.postDelayed(() -> {
            pending.set(false);
            long identity = Binder.clearCallingIdentity();
            try { apply(); }
            catch (Throwable problem) { if (failed.compareAndSet(false, true)) error.accept(problem); }
            finally { Binder.restoreCallingIdentity(identity); }
        }, 80)) pending.set(false);
    }

    private void apply() throws Exception {
        if (!enabled || !AdaptationHookGate.isHyperOsActive()) return;
        IBinder service = (IBinder) Class.forName("android.os.ServiceManager")
                .getDeclaredMethod("getService", String.class).invoke(null, NAME);
        if (service == null) {
            if (retryIncompleteProfile()) return;
            throw new IllegalStateException("Touch HAL unavailable");
        }
        // Read actual advertised game-mode bounds before writing this ROM-specific profile.
        if (query(service, 4, GAME) != 0 || query(service, 3, GAME) != 1)
            throw new IllegalStateException("Unexpected touch HAL game bounds; skipping");
        write(service, FORCE_240, 0);
        write(service, GAME, 1);
        write(service, NORMAL_GAME_RATE, 3);
        write(service, SUPER_REPORT, 1);
        // Early boot writes can be accepted before the HAL finishes initializing its driver.
        // Retry at most four times; there is no ongoing polling while the phone is idle.
        if (query(service, 1, GAME) != 1) {
            if (retryIncompleteProfile()) return;
            throw new IllegalStateException("Touch game profile readback failed");
        }
        incompleteAttempts = 0;
        if (logged.compareAndSet(false, true))
            info.accept("Touch HAL 480Hz profile requested: game=1, super=1; 135/240Hz limits released");
    }

    private boolean retryIncompleteProfile() {
        return ++incompleteAttempts <= 4 && handler.postDelayed(this::schedule, 250);
    }

    private int query(IBinder service, int code, int mode) throws Exception {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(0); data.writeInt(mode);
            if (!service.transact(code, data, reply, 0)) throw new IllegalStateException("Touch query rejected: " + code);
            reply.readException();
            return reply.readInt();
        } finally { data.recycle(); reply.recycle(); }
    }

    private void write(IBinder service, int mode, int value) throws Exception {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(0); data.writeInt(mode); data.writeInt(value);
            if (!service.transact(9, data, reply, 0)) throw new IllegalStateException("Touch write rejected: " + mode);
            reply.readException();
            int result = reply.readInt();
            if (result != 0) throw new IllegalStateException("Touch write " + mode + " returned " + result);
        } finally { data.recycle(); reply.recycle(); }
    }
}
