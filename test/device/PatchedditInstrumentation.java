/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;

public final class PatchedditInstrumentation extends Instrumentation {
    private Bundle arguments;

    @Override
    public void onCreate(Bundle arguments) {
        this.arguments = arguments;
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            Class<?> suite = Class.forName(arguments.getString("suite"));
            suite.getMethod("run", Context.class, Bundle.class)
                    .invoke(null, getTargetContext(), arguments);
            result.putString("stream", "PASS " + getTargetContext().getPackageName() + "\n");
            finish(-1, result);
        } catch (Throwable failure) {
            java.io.StringWriter output = new java.io.StringWriter();
            failure.printStackTrace(new java.io.PrintWriter(output));
            result.putString("stream", output.toString());
            finish(1, result);
        }
    }
}
