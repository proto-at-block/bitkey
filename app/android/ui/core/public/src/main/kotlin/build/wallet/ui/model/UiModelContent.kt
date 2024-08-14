package build.wallet.ui.model

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Render UI for the given [Model], assuming that [LocalUiModelMap] provides a [UiModelMap] with
 * relevant [UiModel]s.
 *
 * Renders [UiModelNotImplemented] if we don't know how to render the given model.
 *
 * @param [model] - the [Model] for which we are trying to produce UI.
 */
@Composable
fun UiModelContent(
  model: Model,
  modifier: Modifier = Modifier,
) {
  /** Lookup [UiModel] using [model]s type, if any. */
  val uiModel =
    LocalUiModelMap.current
      .getUiModelFor(model::class, model)

  when (uiModel) {
    null -> {
      val errorMessage = "UiModel not found for model $model, forgot to update AppUiModelMap?"
      error(errorMessage)
    }

    else -> {
      Box(modifier) {
        uiModel.Content(model)
      }
    }
  }
}
