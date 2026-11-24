package app.morphe.extension.shared.requests;

import androidx.annotation.NonNull;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Response;

public abstract class PatchedditInterceptor implements Interceptor {
    public abstract boolean isEnabled();
    public abstract boolean isPatchIncluded();

    @NonNull
    protected abstract Response doIntercept(@NonNull Chain chain) throws IOException;

    @NonNull
    @Override
    public final Response intercept(@NonNull Chain chain) throws IOException {
        if (isPatchIncluded() && isEnabled()) {
            return doIntercept(chain);
        } else {
            return chain.proceed(chain.request());
        }
    }
}
