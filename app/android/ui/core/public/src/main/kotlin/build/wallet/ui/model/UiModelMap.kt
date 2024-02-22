package build.wallet.ui.model

import kotlin.reflect.KClass

/**
 * Map of [UiModel]s, usually used via [LocalUiModelMap].
 *
 * Can be implemented as [TypedUiModelMap].
 */
interface UiModelMap {
  val uiModels: Map<KClass<*>, UiModel>

  /**
   * Retrieve a [UiModel] using given [Model] type, if any.
   */
  fun <ModelT : Model> getUiModelFor(type: KClass<out ModelT>): UiModel? = uiModels[type]
}
