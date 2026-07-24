package com.testloop.app.ui

/** Keeps backend and platform details out of messages shown to members. */
fun userFriendlyError(error: Throwable?, fallback: String): String {
    val details = error?.let { root ->
        generateSequence(root) { it.cause }
            .joinToString(" ") { "${it::class.simpleName.orEmpty()} ${it.message.orEmpty()}" }
    }.orEmpty()

    return when {
        details.contains("owned_apps_package_name_key", ignoreCase = true) ||
            (details.contains("package name", ignoreCase = true) && details.contains("duplicate", ignoreCase = true)) ->
            "This app is already listed. Open My apps to use the existing listing, or check the package ID and try again."
        details.contains("already joined", ignoreCase = true) ->
            "You have already joined this app's test. Open Today to continue where you left off."
        details.contains("not available", ignoreCase = true) || details.contains("no longer available", ignoreCase = true) ->
            "This app is no longer available to join. Go back and choose another app."
        details.contains("not signed in", ignoreCase = true) || details.contains("sign in again", ignoreCase = true) ->
            "Your session has ended. Sign in again and retry."
        details.contains("cancel", ignoreCase = true) ->
            "Sign-in was cancelled. Tap Continue with Google when you're ready to try again."
        else -> fallback
    }
}

fun friendlyPublishError(error: Throwable): String = userFriendlyError(
    error,
    "We couldn't publish your app yet. Check your links and package ID, then try again."
)
