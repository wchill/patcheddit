/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Exercises Material's inset callback with and without the behavior Sync removes for top menus. */
public final class SyncToolbarMenuDeviceTest {
    public static void run(Context context, Bundle arguments) throws Exception {
        FutureTask<Void> test = new FutureTask<>(() -> {
            verifyCallback(context);
            return null;
        });
        PatchedditInstrumentation.instance.runOnMainSync(test);
        test.get(10, TimeUnit.SECONDS);
    }

    private static void verifyCallback(Context context) throws Exception {
        ClassLoader loader = context.getClassLoader();
        Class<?> layoutParams = loader.loadClass("androidx.coordinatorlayout.widget.CoordinatorLayout$f");
        Class<?> behaviorType = loader.loadClass("com.google.android.material.bottomsheet.BottomSheetBehavior");
        var setBehavior = layoutParams.getMethod("q",
                loader.loadClass("androidx.coordinatorlayout.widget.CoordinatorLayout$c"));

        View sheet = new FrameLayout(context);
        sheet.setBackgroundColor(Color.WHITE);
        var params = (ViewGroup.LayoutParams) layoutParams.getConstructor(int.class, int.class)
                .newInstance(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sheet.setLayoutParams(params);
        setBehavior.invoke(params, behaviorType.getConstructor().newInstance());

        Class<?> insetsType = loader.loadClass("androidx.core.view.n");
        var insets = insetsType.getMethod("w", WindowInsets.class).invoke(null,
                new WindowInsets.Builder().setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 24, 0, 0)).build());
        Class<?> callback = loader.loadClass("com.google.android.material.bottomsheet.BottomSheetDialog$EdgeToEdgeCallback");
        var constructor = callback.getDeclaredConstructor(View.class, insetsType);
        constructor.setAccessible(true);
        constructor.newInstance(sheet, insets);

        setBehavior.invoke(params, (Object) null);
        var detached = constructor.newInstance(sheet, insets);
        var lightBackground = callback.getDeclaredField("a");
        lightBackground.setAccessible(true);
        if (!Boolean.TRUE.equals(lightBackground.get(detached))) {
            throw new AssertionError("The detached sheet did not use Material's light-background fallback");
        }
    }
}
