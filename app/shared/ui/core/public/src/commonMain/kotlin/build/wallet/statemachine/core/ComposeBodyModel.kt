package build.wallet.statemachine.core

import androidx.compose.runtime.Composable
import kotlin.native.HiddenFromObjC

/**
 * A [BodyModel] which provides shared Compose UI using the [render] function.
 */
abstract class ComposeBodyModel : BodyModel() {
  @HiddenFromObjC
  @Composable
  abstract fun render()
}
