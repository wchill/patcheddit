/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.patches.reddit.customclients.joey.fix.video

import app.morphe.patcher.Fingerprint
import app.morphe.patches.reddit.customclients.AppCompatibility
import app.morphe.patches.reddit.customclients.COMMENT_VIDEO_LINKS
import app.morphe.patches.reddit.customclients.ExtensionPatches
import app.morphe.patches.reddit.customclients.fixCommentVideoLinksPatch
import app.morphe.patches.reddit.customclients.wrapMorpheHook

@Suppress("unused")
val fixCommentVideoLinksPatch =
    fixCommentVideoLinksPatch(ExtensionPatches.Joey, AppCompatibility.JoeyForReddit) {
        Fingerprint(
            parameters = listOf("Ljava/lang/String;"),
            returnType = "Ljava/lang/String;",
            strings = listOf("v.redd.it", "https://v.redd.it/", "/[^/]*$"),
        ).wrapMorpheHook(before = """
            invoke-static/range {p0 .. p0}, $COMMENT_VIDEO_LINKS->baseUrl(Ljava/lang/String;)Ljava/lang/String;
            move-result-object v0
            if-eqz v0, :morphe_native_video
            return-object v0
            :morphe_native_video
            nop
        """)
    }
