package build.wallet.ui.model

import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.reflect.KClass

/**
 * [CompositionLocal] for [UiModelMap]. Use this to implicitly provide a [UiModelMap] to composable
 * functions.
 *
 * This is used by [UiModelContent] to access the [UiModelMap] implicitly in order to be able to
 * render a UI for a given [Model].
 *
 * Generally, a [UiModelMap] with all of the app's [UiModel]s should be provided once at app's top
 * level.
 */
val LocalUiModelMap =
  staticCompositionLocalOf<UiModelMap> {
    EmptyUiModelMap
  }

/** Default empty map for [LocalUiModelMap]. */
private object EmptyUiModelMap : UiModelMap {
  override val uiModels: Map<KClass<*>, UiModel> = emptyMap()
}
