package com.dantech.skimbrowser.ui.text

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Text that is resolved where it is shown. View models and process-scoped objects describe copy
 * with string resources instead of holding a Context, and the UI turns it into a String in the
 * current configuration.
 */
@Immutable
sealed interface UiText {

    /** A string resource, formatted with [args] when it has placeholders. */
    data class Res(@param:StringRes val id: Int, val args: ImmutableList<Any> = persistentListOf()) : UiText

    /** A plurals resource for [count]; by default [count] also fills its only placeholder. */
    data class Plural(
        @param:PluralsRes val id: Int,
        val count: Int,
        val args: ImmutableList<Any> = persistentListOf(count),
    ) : UiText

    /** Text that is not copy, such as a page title, a host or a formatted date. */
    data class Raw(val text: String) : UiText
}

@Composable
@ReadOnlyComposable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.toTypedArray())
    is UiText.Plural -> pluralStringResource(id, count, *args.toTypedArray())
    is UiText.Raw -> text
}
