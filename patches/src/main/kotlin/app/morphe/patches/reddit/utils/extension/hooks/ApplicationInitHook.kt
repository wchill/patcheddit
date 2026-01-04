package app.morphe.patches.reddit.utils.extension.hooks

import app.morphe.patches.shared.extension.extensionHook

internal val applicationInitHook = extensionHook {
    custom { method, _ ->
        method.definingClass.endsWith("/FrontpageApplication;") &&
                method.name == "onCreate"
    }
}
