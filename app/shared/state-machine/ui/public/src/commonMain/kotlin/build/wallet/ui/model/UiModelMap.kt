package build.wallet.ui.model

import build.wallet.statemachine.core.ComposeBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
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
  fun <ModelT : Model> getUiModelFor(
    type: KClass<out ModelT>,
    model: ModelT? = null,
  ): UiModel? {
    return when (model) {
      is ComposeBodyModel -> uiModels[ComposeBodyModel::class]
      is FormBodyModel -> uiModels[FormBodyModel::class]
      else -> uiModels[type]
    }
  }
}
