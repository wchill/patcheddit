-dontoptimize
-repackageclasses 'app.morphe.extension.vendored'

-keep,allowrepackage class com.google.common.util.concurrent.RateLimiter {
    public static com.google.common.util.concurrent.RateLimiter create(double);
    public double acquire();
    public boolean tryAcquire(int, long, java.util.concurrent.TimeUnit);
}

# These annotations are not used by the packaged rate limiter at runtime.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn org.jspecify.annotations.Nullable
