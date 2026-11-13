/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.settings;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import app.morphe.extension.boost.oauth.OAuthTokenAccounts;
import app.morphe.extension.boost.oauth.OAuthTokenException;
import app.morphe.extension.boost.oauth.OAuthTokenPayload;
import app.morphe.extension.boost.oauth.OAuthTokenValidator;

@SuppressWarnings("deprecation")
public final class BoostSettingsActivity extends PreferenceActivity {
    private static final String TAG = "MorpheOAuthTokens";
    private static final String SCREEN = "morphe_screen";
    private static final String STATUS = "morphe_boost_oauth_account";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;
    private boolean oauthTokens;
    private boolean busy;
    private String busySummary;
    private String tokenJson = "{\n  \"access_token\": \"\",\n  \"refresh_token\": \"\"\n}";
    private EditText input;
    private Button saveButton;
    private AlertDialog visibleDialog;

    @Override
    protected void onCreate(Bundle state) {
        boolean dark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        setTheme(dark ? android.R.style.Theme_Material : android.R.style.Theme_Material_Light);
        super.onCreate(state);

        String screen = getIntent().getStringExtra(SCREEN);
        if ("image_hosting".equals(screen)) {
            setTitle("Editor image hosting");
            addPreferencesFromResource(resource("xml", "morphe_boost_editor_image_hosting"));
            return;
        }
        if (!"oauth_tokens".equals(screen)) {
            Log.e(TAG, "Unknown Boost Morphe settings screen: " + screen);
            finish();
            return;
        }

        oauthTokens = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setTitle("OAuth tokens");
        addPreferencesFromResource(resource("xml", "morphe_boost_oauth_tokens"));
        Preference editor = new Preference(this) {
            @Override
            protected void onBindView(View view) {
                super.onBindView(view);
                bindEditor(view);
            }
        };
        editor.setKey("morphe_boost_oauth_editor");
        editor.setPersistent(false);
        editor.setSelectable(false);
        editor.setLayoutResource(resource("layout", "morphe_boost_oauth_editor"));
        getPreferenceScreen().addPreference(editor);

        String username = OAuthTokenAccounts.currentUsername();
        if (username != null) {
            runTask("Loading tokens...",
                    () -> OAuthTokenAccounts.export(username, OAuthTokenAccounts.clientId()).toJson(),
                    json -> {
                        tokenJson = json;
                        if (input != null) input.setText(json);
                    });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (oauthTokens) updateState();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        worker.shutdownNow();
        if (input != null) {
            tokenJson = input.getText().toString();
            input.setText("");
            input = null;
        }
        if (saveButton != null) {
            saveButton.setOnClickListener(null);
            saveButton = null;
        }
        if (visibleDialog != null) {
            visibleDialog.dismiss();
            visibleDialog = null;
        }
        tokenJson = "";
        super.onDestroy();
    }

    private void bindEditor(View view) {
        EditText editor = view.findViewById(android.R.id.edit);
        if (input != editor) {
            if (input != null) tokenJson = input.getText().toString();
            input = editor;
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setSaveEnabled(false);
            input.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
            if (Build.VERSION.SDK_INT >= 26) {
                input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            }
            if (Build.VERSION.SDK_INT >= 29) {
                input.setImportantForContentCapture(View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS);
            }
            input.setText(tokenJson);
        }
        saveButton = view.findViewById(android.R.id.button1);
        saveButton.setOnClickListener(ignored -> saveTokens());
        input.setEnabled(!busy);
        saveButton.setEnabled(!busy);
    }

    private void saveTokens() {
        try {
            OAuthTokenPayload payload = OAuthTokenPayload.parse(input.getText().toString());
            Intent home = homeIntent();
            runTask("Checking and saving tokens...", () -> importTokens(payload),
                    username -> restart(home, username));
        } catch (OAuthTokenException exception) {
            input.setError(exception.getMessage());
        }
    }

    private void updateState() {
        String username = OAuthTokenAccounts.currentUsername();
        findPreference(STATUS).setSummary(busy ? busySummary :
                username == null ? "Not signed in" : "u/" + username);
        if (input != null) input.setEnabled(!busy);
        if (saveButton != null) saveButton.setEnabled(!busy);
    }

    private AlertDialog show(AlertDialog.Builder builder) {
        visibleDialog = builder.create();
        Window window = visibleDialog.getWindow();
        if (window != null) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        visibleDialog.show();
        return visibleDialog;
    }

    private void error(String message) {
        if (destroyed) return;
        show(new AlertDialog.Builder(this)
                .setTitle("OAuth tokens")
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null));
    }

    private String importTokens(OAuthTokenPayload payload) throws IOException, OAuthTokenException {
        String clientId = OAuthTokenAccounts.clientId();
        OAuthTokenValidator.VerifiedAccount verified =
                new OAuthTokenValidator().validate(payload, clientId, OAuthTokenAccounts.userAgent());
        OAuthTokenAccounts.save(verified);
        return verified.tokens.username;
    }

    private Intent homeIntent() throws OAuthTokenException {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent == null) throw new OAuthTokenException("Boost's launcher activity could not be found.");
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    private void restart(Intent home, String username) {
        try {
            OAuthTokenAccounts.activate(username);
            startActivity(home);
        } catch (OAuthTokenException exception) {
            error(exception.getMessage());
        } catch (SQLiteException | ClassCastException | ActivityNotFoundException
                 | IllegalArgumentException | IllegalStateException exception) {
            error("The tokens were saved, but Boost could not reopen the imported account. "
                    + "Restart Boost and select that account in the account switcher.");
        }
    }

    private interface Task<T> {
        T run() throws IOException, OAuthTokenException;
    }

    private <T> void runTask(String summary, Task<T> task, Consumer<T> success) {
        busySummary = summary;
        busy = true;
        updateState();
        worker.execute(() -> {
            try {
                T result = task.run();
                MAIN.post(() -> {
                    if (destroyed) return;
                    busy = false;
                    updateState();
                    success.accept(result);
                });
            } catch (OAuthTokenException exception) {
                failed(exception.getMessage());
            } catch (IOException exception) {
                if (!destroyed) failed("Could not load or save tokens. Check your connection to Reddit, then retry.");
            } catch (SQLiteException exception) {
                failed("Boost could not save the account. Check that the device has free storage.");
            } catch (SecurityException exception) {
                failed("Android denied access to Boost's account storage or the network.");
            } catch (IllegalArgumentException | IllegalStateException exception) {
                failed("Boost's API settings or account storage are invalid. Check the native API "
                        + "settings before retrying.");
            }
        });
    }

    private void failed(String message) {
        Log.w(TAG, "OAuth token transfer failed.");
        MAIN.post(() -> {
            if (destroyed) return;
            busy = false;
            updateState();
            error(message);
        });
    }

    private int resource(String type, String name) {
        int id = getResources().getIdentifier(name, type, getPackageName());
        if (id == 0) throw new IllegalStateException(
                "Missing Morphe settings resource: " + type + "/" + name);
        return id;
    }
}
