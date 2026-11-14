package com.mi.onextbox.lsp;

public final class AospVolumeCallbackFixtures {
    public static final class State {}
    public interface Stock { void onStateChanged(State state); }
    public interface Old extends Stock { default void onStateChanged(State state, int stream) {} }
    public interface Current extends Stock { default void onStateChanged(State state, int stream, boolean fromKey) {} }
    public static class StockProducer implements Stock { public void onStateChanged(State state) {} }
    public static class OldProducer implements Old { public void onStateChanged(State state) {} }
    public static class CurrentProducer implements Current { public void onStateChanged(State state) {} }
    public static final class NativeOverride extends CurrentProducer {
        @Override public void onStateChanged(State state, int stream, boolean fromKey) {}
    }
    public static final class Dispatcher {
        public void onStateChanged(State state) {}
        public void onStateChanged(State state, int stream) {}
        public void onStateChanged(State state, int stream, boolean fromKey) {}
        public void onStateChanged(State state, long unrelated) {}
    }
}
