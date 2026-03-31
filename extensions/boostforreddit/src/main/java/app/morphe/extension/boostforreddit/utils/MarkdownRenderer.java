/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit.utils;

import java.lang.reflect.Method;

import app.morphe.extension.shared.Logger;

public class MarkdownRenderer {
    private static final Method markdownRenderer;
    static {
        markdownRenderer = ReflectionUtils.findMethodWithClasses(
                "com.rubenmayayo.androidsnudown.Snudown",
                "markdown",
                String.class);
    }

    public static String render(String text) {
        return ReflectionUtils.invokeStatic(markdownRenderer, text.replace("<", "&amp;lt;")) + "&nbsp;";
    }
}
