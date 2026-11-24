/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.settings;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public final class MatureContentLockTestReceiver extends BroadcastReceiver {
    private static final String ACTION = "app.morphe.extension.shared.settings.TEST_MATURE_CONTENT_LOCK";
    private static final String TAG = "MatureContentLockTest";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) {
            reject("Invalid mature content lock test action.");
            return;
        }

        String operation = intent.getStringExtra("operation");
        SharedPreferences.Editor editor = PatchSettings.preferences(context).edit();
        // Change only Patcheddit's flag so testing doesn't permanently rewrite native preferences.
        if ("set".equals(operation)) {
            editor.putBoolean(MatureContentLock.KEY, true);
        } else if ("remove".equals(operation)) {
            editor.remove(MatureContentLock.KEY);
        } else {
            reject("Specify operation=set or operation=remove.");
            return;
        }
        if (!editor.commit()) {
            reject("Could not save the test lock state.");
        } else if (isOrderedBroadcast()) {
            setResultCode(Activity.RESULT_OK);
        }
    }

    private void reject(String reason) {
        Log.w(TAG, reason);
        if (isOrderedBroadcast()) {
            setResult(Activity.RESULT_CANCELED, reason, null);
        }
    }
}
