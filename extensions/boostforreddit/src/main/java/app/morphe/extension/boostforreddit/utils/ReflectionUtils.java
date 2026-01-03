package app.morphe.extension.boostforreddit.utils;

import android.util.Pair;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class ReflectionUtils {
    private static final Map<String, Pair<Class<?>, Map<String, Method>>> cachedMethods = new HashMap<>();

    public static Class<?> findClass(String className) {
        if (cachedMethods.containsKey(className)) {
            return cachedMethods.get(className).first;
        }
        try {
            Class<?> klass = Class.forName(className);
            cachedMethods.put(className, Pair.create(klass, new HashMap<>()));
            return klass;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object construct(String className) {
        return construct(className, new Object[] {}, new Class[] {});
    }

    public static Object construct(String className, Object[] params, Class<?>[] paramClasses) {
        Class<?> klass = findClass(className);
        try {
            Constructor<?> constructor = klass.getConstructor(paramClasses);
            return constructor.newInstance(params);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException |
                 InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    public static Method findMethodByClassNames(String className, String methodName, String... paramClasses) {
        try {
            Class<?> klass = findClass(className);

            Map<String, Method> methodMap = cachedMethods.get(className).second;
            Method method = methodMap.get(methodName);
            if (method == null) {
                Class<?>[] classes = new Class[paramClasses.length];
                for (int i = 0; i < paramClasses.length; i++) {
                    classes[i] = findClass(paramClasses[i]);
                }
                method = klass.getMethod(methodName, classes);
                methodMap.put(methodName, method);
                LoggingUtils.logInfo(true, () -> String.format("Found method %s->%s", className, methodName));
            }
            return method;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static Method findMethodWithClasses(String className, String methodName, Class<?>... classes) {
        try {
            Class<?> klass = findClass(className);

            Map<String, Method> methodMap = cachedMethods.get(className).second;
            Method method = methodMap.get(methodName);
            if (method == null) {
                method = klass.getMethod(methodName, classes);
                methodMap.put(methodName, method);
                LoggingUtils.logInfo(true, () -> String.format("Found method %s->%s", className, methodName));
            }
            return method;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T invoke(Object object, String methodName, Object... params) {
        String className = object.getClass().getName();
        findClass(className);
        Map<String, Method> methodMap = cachedMethods.get(className).second;

        try {
            Method method = methodMap.get(methodName);
            if (method == null) {
                throw new RuntimeException(String.format("Need to instantiate method first: %s->%s", className, methodName));
            }
            return (T) method.invoke(object, params);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T invoke(Object object, Method method, Object... params) {
        try {
            return (T) method.invoke(object, params);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T invokeStatic(String className, String methodName, Object... params) {
        Class<?> klass = findClass(className);

        Map<String, Method> methodMap = cachedMethods.get(className).second;

        try {
            Method method = methodMap.get(methodName);
            if (method == null) {
                Class<?>[] paramClasses = new Class[params.length];
                for (int i = 0; i < params.length; i++) {
                    paramClasses[i] = params.getClass();
                }
                method = klass.getMethod(methodName, paramClasses);
                methodMap.put(methodName, method);
            }
            return (T) method.invoke(null, params);
        } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T invokeStatic(Method method, Object... params) {
        try {
            return (T) method.invoke(null, params);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
