/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.patcheddit.fixture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public final class RifProfilePostingDeviceTest {
    private static final String SUBMIT = "com.andrewshu.android.reddit.submit.SubmitActivity";

    public static void run(Context context, Bundle arguments) throws Exception {
        Activity activity = PatchedditInstrumentation.instance.startActivitySync(
                new Intent().setClassName(context, SUBMIT).putExtra("subreddit", "patcheddit_fixture")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        PatchedditInstrumentation.instance.waitForIdleSync();
        try {
            main(() -> {
                boolean paid = context.getPackageName().endsWith("donation");
                Class<?> prefsType = context.getClassLoader().loadClass(paid ? "l4.v" : "k4.v");
                Object prefs = prefsType.getMethod("C").invoke(null);
                Field username = field(prefsType, "Z0");
                Object original = username.get(prefs);
                try {
                    form(activity, prefs, username, paid);
                    menu(activity, prefs, username, paid);
                } finally {
                    username.set(prefs, original);
                }
                return null;
            });
        } finally {
            main(() -> { activity.finish(); return null; });
        }
    }

    private static void form(Activity activity, Object prefs, Field username, boolean paid) throws Exception {
        Class<?> type = activity.getClassLoader().loadClass("com.andrewshu.android.reddit.submit.m");
        Method getter = Arrays.stream(activity.getClass().getDeclaredMethods())
                .filter(method -> method.getReturnType() == type && method.getParameterCount() == 0)
                .findFirst().orElseThrow();
        getter.setAccessible(true);
        Object form = getter.invoke(activity);
        Method select = type.getMethod("morpheSelectDestination", String.class);
        Method update = type.getDeclaredMethod(paid ? "N5" : "Y5", String.class);
        update.setAccessible(true);
        Field destination = field(type, paid ? "j0" : "m0");
        Field media = field(type, paid ? "l0" : "o0");
        Field kind = field(type, paid ? "i0" : "l0");
        Field flair = field(type, paid ? "k0" : "n0");
        Object oldDestination = destination.get(form), oldMedia = media.get(form), oldKind = kind.get(form);
        Object oldUsername = username.get(prefs);
        TextView title = activity.findViewById(id(activity, "submit_title"));
        TextView body = activity.findViewById(id(activity, "submit_text"));
        CharSequence oldTitle = title.getText().toString(), oldBody = body.getText().toString();
        View button = activity.findViewById(id(activity, "patcheddit_my_profile"));
        ViewGroup tabs = activity.findViewById(id(activity, "tab_layout"));
        ViewGroup tabButtons = (ViewGroup) tabs.getChildAt(0);
        check(tabButtons.getChildCount() == 3, "Missing shared Submit tabs");
        try {
            username.set(prefs, null);
            update.invoke(form, (Object) null);
            check(button.getVisibility() == View.GONE, "Guest profile button was visible");
            username.set(prefs, "FixtureA");
            update.invoke(form, "FixtureA");
            check(button.getVisibility() == View.VISIBLE, "Signed-in profile button was hidden");
            username.set(prefs, null);
            button.performClick();
            check(oldDestination.equals(destination.get(form)) && button.getVisibility() == View.GONE,
                    "Late logout selected a stale destination");
            for (int tab = 0; tab < 3; tab++) {
                tabButtons.getChildAt(tab).performClick();
                select.invoke(form, "patcheddit_fixture_" + tab);
                Object selectedKind = kind.get(form);
                title.setText("Fixture title");
                body.setText("Fixture body with ![image](https://example.invalid/image.png)");
                Uri attachment = Uri.parse("content://patcheddit.fixture/image");
                media.set(form, attachment);
                flair.set(form, "fixture-flair");
                username.set(prefs, " FixtureA ");
                update.invoke(form, " FixtureA ");
                button.performClick();
                check("u_FixtureA".equals(destination.get(form)), "Wrong native profile destination");
                check(flair.get(form) == null, "Native flair refresh was bypassed");
                check(title.getText().toString().equals("Fixture title")
                                && body.getText().toString().contains("![image]")
                                && attachment.equals(media.get(form)) && selectedKind.equals(kind.get(form)),
                        "Changing destination changed draft content or media kind");
                username.set(prefs, "FixtureB");
                update.invoke(form, "FixtureB");
                button.performClick();
                check("u_FixtureB".equals(destination.get(form)), "Account switch used a cached username");
            }
            layout((ViewGroup) button.getParent());
        } finally {
            username.set(prefs, oldUsername);
            update.invoke(form, oldUsername);
            tabButtons.getChildAt(0).performClick();
            title.setText(oldTitle);
            body.setText(oldBody);
            media.set(form, oldMedia);
            kind.set(form, oldKind);
            select.invoke(form, oldDestination);
        }
    }

    private static void layout(ViewGroup row) {
        TextView picker = (TextView) row.getChildAt(0), profile = (TextView) row.getChildAt(1);
        float pickerSize = picker.getTextSize(), profileSize = profile.getTextSize();
        try {
            for (float scale : new float[]{1, 1.6f}) {
                picker.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, pickerSize * scale);
                profile.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, profileSize * scale);
                int width = Math.round(280 * row.getResources().getDisplayMetrics().density);
                row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                row.layout(0, 0, width, row.getMeasuredHeight());
                for (TextView button : new TextView[]{picker, profile}) {
                    check(button.getWidth() > 0 && button.getRight() <= width, "Narrow profile row overflowed");
                    for (int line = 0; line < button.getLayout().getLineCount(); line++) {
                        check(button.getLayout().getEllipsisCount(line) == 0
                                        && button.getLayout().getLineWidth(line) <= button.getWidth()
                                        - button.getCompoundPaddingLeft() - button.getCompoundPaddingRight() + 1,
                                "Scaled profile button text was clipped");
                    }
                }
            }
        } finally {
            picker.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, pickerSize);
            profile.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, profileSize);
            row.requestLayout();
        }
    }

    private static void menu(Activity activity, Object prefs, Field username, boolean paid) throws Exception {
        ClassLoader loader = activity.getClassLoader();
        Class<?> profileType = loader.loadClass("com.andrewshu.android.reddit.user.c");
        Object profile = profileType.getConstructor().newInstance();
        Field viewed = field(profileType, paid ? "g1" : "j1");
        Class<?> callbackType = loader.loadClass("com.andrewshu.android.reddit.user.c$c");
        Constructor<?> constructor = callbackType.getDeclaredConstructor(profileType);
        constructor.setAccessible(true);
        Object callback = constructor.newInstance(profile);
        Method prepare = callbackType.getDeclaredMethod("d", Menu.class);
        prepare.setAccessible(true);
        Menu menu = new PopupMenu(activity, new View(activity)).getMenu();
        activity.getMenuInflater().inflate(activity.getResources().getIdentifier("profile", "menu",
                activity.getPackageName()), menu);
        MenuItem submit = menu.findItem(id(activity, "patcheddit_profile_submit"));
        username.set(prefs, "FixtureA");
        viewed.set(profile, "fixturea");
        prepare.invoke(callback, menu);
        check(submit.isVisible(), "Native case-insensitive ownership did not show Submit");
        int index = 0;
        while (menu.getItem(index) != submit) index++;
        check(menu.getItem(index + 1).getItemId() == id(activity, "menu_inbox"), "Submit is not above Inbox");
        viewed.set(profile, "SomeoneElse");
        prepare.invoke(callback, menu);
        check(!submit.isVisible(), "Another user's profile showed Submit");
        username.set(prefs, null);
        prepare.invoke(callback, menu);
        check(!submit.isVisible(), "Logged-out profile showed Submit");

        Class<?> hook = loader.loadClass("app.morphe.extension.redditisfun.profile.ProfilePosting");
        Method open = hook.getMethod("open", Activity.class, MenuItem.class, String.class);
        Capture capture = new Capture(activity);
        username.set(prefs, "FixtureB");
        open.invoke(null, capture, submit, "FixtureA");
        check(capture.intent == null, "Stale profile menu opened another account's destination");
        open.invoke(null, capture, submit, "fixtureb");
        check(capture.intent != null && capture.intent.getComponent().getClassName().equals(SUBMIT)
                        && capture.intent.getComponent().getPackageName().equals(activity.getPackageName())
                        && "u_FixtureB".equals(capture.intent.getStringExtra("subreddit")),
                "Own-profile menu did not construct the native Submit intent");
        Method click = callbackType.getDeclaredMethod("a", MenuItem.class);
        click.setAccessible(true);
        check(Boolean.TRUE.equals(click.invoke(callback, submit)), "Detached profile click was not safely consumed");
    }

    private static final class Capture extends Activity {
        Intent intent;
        Capture(Context context) { attachBaseContext(context); }
        @Override public void startActivity(Intent intent) { this.intent = intent; }
    }

    private static int id(Context context, String name) {
        int id = context.getResources().getIdentifier(name, "id", context.getPackageName());
        check(id != 0, "Missing resource " + name);
        return id;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T main(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        PatchedditInstrumentation.instance.runOnMainSync(task);
        return task.get();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
