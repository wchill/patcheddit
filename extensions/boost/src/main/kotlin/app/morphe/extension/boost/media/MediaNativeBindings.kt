/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.boost.media

import MorpheBoostNativeStatics
import android.app.Activity
import android.content.Context
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.rubenmayayo.reddit.models.generic.ImageModel
import com.rubenmayayo.reddit.ui.customviews.FormattingBar
import com.rubenmayayo.reddit.ui.customviews.menu.MenuOption
import com.rubenmayayo.reddit.ui.fragments.imagemodel.ImageModelFragment
import com.bumptech.glide.RequestManager
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.load.engine.GlideException
import com.rubenmayayo.reddit.ui.customviews.TableTextView
import android.widget.LinearLayout
import android.widget.TextView

object MediaNativeBindings {
    @JvmStatic
    fun galleryModel(fragment: Any): ImageModel? = (fragment as ImageModelFragment).imageModel

    @JvmStatic
    fun fragmentActivity(fragment: Any): Activity? = (fragment as Fragment).getActivity()

    @JvmStatic
    fun fragmentTag(fragment: Any): String? = (fragment as Fragment).getTag()

    @JvmStatic
    fun fragmentVisible(fragment: Any): Boolean = (fragment as Fragment).getUserVisibleHint()

    @JvmStatic
    fun loadImage(manager: Any?, context: Context, url: String, image: ImageView) {
        requireNotNull(imageManager(manager, context).load(url)).into(image)
    }

    private fun imageManager(manager: Any?, context: Context): RequestManager =
        if (manager == null) requireNotNull(MorpheBoostNativeStatics.Glide_with(context)) else manager as RequestManager

    @JvmStatic
    fun inlineManager(manager: Any?, context: Context): Any = imageManager(manager, context)

    @JvmStatic
    fun inlineMediaEnabled(): Boolean = MorpheBoostNativeStatics.BoostPreferences_getInstance()
        .preferences.getBoolean("pref_comments_inline_media", true)

    @JvmStatic
    fun inlineTextView(body: LinearLayout): TextView = requireNotNull((body as TableTextView).createTextView())

    @JvmStatic
    fun loadInlineImage(manager: Any?, context: Context, url: String, image: ImageView,
                        width: Int, height: Int, failure: InlineMedia.Failure) {
        val request = requireNotNull(imageManager(manager, context).load(url)).override(width, height) as RequestBuilder
        requireNotNull(request.addListener(object : RequestListener {
            override fun onResourceReady(resource: Any?) = Unit
            override fun onLoadFailed(exception: GlideException?): Boolean {
                failure.onFailure()
                return false
            }
        })).into(image)
    }

    @JvmStatic
    fun clearInlineImage(manager: Any?, context: Context, image: ImageView) =
        imageManager(manager, context).clear(image)

    @JvmStatic
    fun openInlineUrl(context: Context, url: String) = MorpheBoostNativeStatics.Navigation_openUrl(context, url)

    @JvmStatic
    fun imageMenuOptionId(option: Any): Int = (option as MenuOption).id

    @JvmStatic
    fun attachCamera(formattingBar: Any) {
        (formattingBar as FormattingBar).attachmentCallback?.attachCamera()
    }

    @JvmStatic
    fun attachGallery(formattingBar: Any) {
        (formattingBar as FormattingBar).attachmentCallback?.attachGallery()
    }

    @JvmStatic
    fun insertRawUrl(formattingBar: Any, url: String) {
        val editor = requireNotNull((formattingBar as FormattingBar).editor) {
            "FormattingBar has no editor"
        }
        editor.text.replace(editor.selectionStart, editor.selectionEnd, url)
    }
}
