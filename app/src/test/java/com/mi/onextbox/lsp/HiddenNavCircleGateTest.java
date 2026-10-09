package com.mi.onextbox.lsp;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class HiddenNavCircleGateTest {
    public static final class Navigation {
        public static final Holder u0 = new Holder();
        boolean gesture, hidden, circle, broken;
        public boolean E() { return gesture; }
        public boolean w() { return hidden; }
        public boolean q() { if (broken) throw new IllegalStateException("unavailable"); return circle; }
    }
    public static final class Holder {
        Navigation nav;
        public Navigation get() { return nav; }
    }
    public static final class MissingField {}
    public static final class WrongSignature {
        public static final Holder u0 = new Holder();
        public int E() { return 3; }
        public boolean w() { return true; }
        public boolean q() { return true; }
    }

    @Before public void reset() { Navigation.u0.nav = new Navigation(); }

    @Test public void requiresAllThreeNativeConditions() throws Exception {
        HiddenNavCircleGate gate = HiddenNavCircleGate.bind(Navigation.class);
        Navigation nav = Navigation.u0.nav;
        for (int mask = 0; mask < 8; mask++) {
            nav.gesture = (mask & 1) != 0;
            nav.hidden = (mask & 2) != 0;
            nav.circle = (mask & 4) != 0;
            assertEquals("condition mask " + mask, mask == 7, gate.eligible());
        }
    }

    @Test public void followsLiveObserverAndInstanceChanges() throws Exception {
        HiddenNavCircleGate gate = HiddenNavCircleGate.bind(Navigation.class);
        Navigation nav = Navigation.u0.nav;
        nav.gesture = nav.hidden = nav.circle = true;
        assertTrue(gate.eligible());
        nav.circle = false;
        assertFalse(gate.eligible());
        Navigation.u0.nav = new Navigation();
        assertFalse(gate.eligible());
        Navigation.u0.nav = null;
        assertFalse(gate.eligible());
    }

    @Test(expected = ReflectiveOperationException.class)
    public void observerFailureCannotEnableController() throws Exception {
        Navigation nav = Navigation.u0.nav;
        nav.gesture = nav.hidden = nav.broken = true;
        HiddenNavCircleGate.bind(Navigation.class).eligible();
    }

    @Test(expected = ReflectiveOperationException.class)
    public void rejectsMissingObserver() throws Exception { HiddenNavCircleGate.bind(MissingField.class); }

    @Test(expected = ReflectiveOperationException.class)
    public void rejectsChangedPredicateSignature() throws Exception { HiddenNavCircleGate.bind(WrongSignature.class); }
}
