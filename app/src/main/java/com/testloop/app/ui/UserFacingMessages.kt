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
        details.contains("owned_apps_group_url", ignoreCase = true) ->
            "The Google Groups link was rejected. Check that it uses the full https://groups.google.com/g/... format and try again."
        details.contains("owned_apps_install_url", ignoreCase = true) ->
            "The Play install link must match the detected package ID. Go back, paste the exact Play link, and try again."
        details.contains("owned_apps_opt_in_url", ignoreCase = true) ->
            "The Play opt-in link must match the detected package ID. Go back and correct the link, then try again."
        details.contains("campaigns_requested_testers", ignoreCase = true) ->
            "Choose a tester target between 1 and 100, then try again."
        details.contains("already joined", ignoreCase = true) ->
            "You have already joined this app's test. Open Today to continue where you left off."
        details.contains("not available", ignoreCase = true) || details.contains("no longer available", ignoreCase = true) ->
            "App not found. It may have been removed or is no longer available. Go back and choose another app."
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
