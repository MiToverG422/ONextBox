package com.mi.onextbox.lsp;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Bind only the inspected firmware's native observer predicates; do not cache their values. */
final class HiddenNavCircleGate {
    private final Field holder;
    private final Method get, gesture, hidden, circle;

    private HiddenNavCircleGate(Field holder, Method get, Method gesture, Method hidden, Method circle) {
        this.holder = holder;
        this.get = get;
        this.gesture = gesture;
        this.hidden = hidden;
        this.circle = circle;
    }

    static HiddenNavCircleGate bind(Class<?> navigation) throws ReflectiveOperationException {
        Field holder = navigation.getDeclaredField("u0");
        if (!Modifier.isStatic(holder.getModifiers())) throw new NoSuchFieldException("u0 not static");
        holder.setAccessible(true);
        Method get = holder.getType().getMethod("get");
        get.setAccessible(true);
        return new HiddenNavCircleGate(holder, get, predicate(navigation, "E"),
                predicate(navigation, "w"), predicate(navigation, "q"));
    }

    private static Method predicate(Class<?> type, String name) throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(name);
        if (method.getReturnType() != boolean.class || Modifier.isStatic(method.getModifiers())) {
            throw new NoSuchMethodException("Invalid native predicate: " + name);
        }
        method.setAccessible(true);
        return method;
    }

    boolean eligible() throws ReflectiveOperationException {
        Object navigation = get.invoke(holder.get(null));
        if (navigation == null) return false;
        return (boolean) gesture.invoke(navigation)
                && (boolean) hidden.invoke(navigation)
                && (boolean) circle.invoke(navigation);
    }
}
