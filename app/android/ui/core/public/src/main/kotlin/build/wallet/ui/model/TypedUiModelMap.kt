package build.wallet.ui.model

import android.graphics.ColorSpace.Model
import kotlin.reflect.KClass

/**
 * Implementation of [UiModelMap] that uses type of the [Model] as a key for the given [UiModel].
 *
 * [UiModel]s with duplicated keys are not allowed.
 */
class TypedUiModelMap(
  vararg uiModels: UiModel,
) : UiModelMap {
  @Suppress("UNCHECKED_CAST")
  override val uiModels: Map<KClass<*>, UiModel> =
    uiModels.associateBy { it.key }
      .also { uiModelsForMap ->
        // Make sure there are no [UiModel]s with duplicated keys.
        if (uiModels.size != uiModelsForMap.size) {
          val duplicatedUiModels =
            uiModels
              .groupBy { it.key }
              .filter { it.value.size > 1 }
              .keys

          error("UiModels with duplicated keys are not allowed: $duplicatedUiModels")
        }
      } as Map<KClass<*>, UiModel>
}
