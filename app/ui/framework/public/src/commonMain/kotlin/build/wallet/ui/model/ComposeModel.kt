package build.wallet.ui.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.native.HiddenFromObjC

/**
 * A presentation [Model] that can be rendered using Compose UI.
 *
 * Usage:
 *
 * ```kotlin
 * data class SomeCardModel(
 *   val value: String,
 *   val onClick: () -> Unit,
 * ): ComposeModel {
 *   @Composable
 *   override fun render(modifier: Modifier) {
 *     Column(modifier) {
 *       Text(value)
 *       Button(onClick = onClick) { Text("Click me" }
 *     }
 *   }
 * }
 */
interface ComposeModel : Model {
  /**
   * Produces Compose UI content for this presentation model.
   */
  @Composable
  @HiddenFromObjC
  fun render(modifier: Modifier)
}

/**
 * Composable functions in interfaces do not currently support default function parameters.
 * https://issuetracker.google.com/issues/317490247.
 */
@Composable
@HiddenFromObjC
fun ComposeModel.render() = render(Modifier)
