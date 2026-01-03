package app.morphe.extension.boostforreddit.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

public class CompatibleRateLimiter {
    private static final Method createInstanceMethod;
    private static final Method tryAcquireMethod;
    private static final Method acquireMethod;
    private static final Method setRateMethod;
    private final Object rateLimiter;
    static {
        try {
            Class<?> klass = Class.forName("com.google.common.util.concurrent.e");
            createInstanceMethod = klass.getMethod("e", double.class);
            tryAcquireMethod = klass.getMethod("q", int.class, long.class, TimeUnit.class);
            acquireMethod = klass.getMethod("b", int.class);
            setRateMethod = klass.getMethod("o", double.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private Object invoke(Method m, Object... params) {
        try {
            if (params == null) {
                return m.invoke(rateLimiter);
            }
            return m.invoke(rateLimiter, params);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private CompatibleRateLimiter(double rate) {
        rateLimiter = invoke(createInstanceMethod, rate);
    }

    public static CompatibleRateLimiter create(double rate) {
        return new CompatibleRateLimiter(rate);
    }

    public boolean tryAcquire() {
        return tryAcquire(1, 0L, TimeUnit.MICROSECONDS);
    }

    public boolean tryAcquire(int permits, long timeout, TimeUnit timeUnit) {
        return (boolean) invoke(tryAcquireMethod, permits, timeout, timeUnit);
    }

    public double acquire() {
        return acquire(1);
    }

    public double acquire(int permits) {
        if (tryAcquire()) {
            return 0;
        }
        return (double) invoke(acquireMethod, permits);
    }

    public void setRate(double rate) {
        invoke(setRateMethod, rate);
    }
}
