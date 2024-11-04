package build.wallet.ui.model

import androidx.compose.runtime.Composable
import kotlin.reflect.KClass

/**
 * Acts as a mapping that binds a UI implementation ([Content]) to specific [ModelT] type.
 *
 *
 */
interface UiModel {
  /**
   * Unique key of this [UiModel].
   */
  val key: KClass<out Model>

  /**
   * UI for given [Model].
   */
  @Composable
  fun Content(model: Model)
}

/**
 * Creates [UiModel] using [ModelT] type as [UiModel]'s key.
 */
inline fun <reified ModelT : Model> UiModel(
  crossinline content: @Composable (model: ModelT) -> Unit,
) = object : UiModel {
  override val key: KClass<out Model> = ModelT::class

  @Composable
  override fun Content(model: Model) {
    content(model as ModelT)
  }
}
