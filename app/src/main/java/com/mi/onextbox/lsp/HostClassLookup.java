package com.mi.onextbox.lsp;

public final class HostClassLookup {
    private HostClassLookup() { }

    public static Class<?> load(String name, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
        return Class.forName(name, initialize, loader);
    }

    public static Class<?> load(String name, ClassLoader loader) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    public static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }
}
