package build.wallet.statemachine.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.native.HiddenFromObjC

/**
 * Applied to any Presentation Model that is capable of
 * being rendered directly from the Model instance.
 */
interface ComposableRenderedModel {
  val key: String

  /**
   * Render the Model's Composable content.
   */
  @HiddenFromObjC
  @Composable
  fun render(modifier: Modifier)
}
