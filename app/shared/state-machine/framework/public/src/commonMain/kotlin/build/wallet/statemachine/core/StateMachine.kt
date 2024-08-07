package build.wallet.statemachine.core

import androidx.compose.runtime.Composable
import app.cash.molecule.RecompositionMode.ContextClock
import app.cash.molecule.launchMolecule
import build.wallet.molecule.composeFrameClock
import com.rickclephas.kmp.nativecoroutines.NativeCoroutines
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.plus
import kotlin.native.HiddenFromObjC

/**
 * Interface for implementing reactive, deterministic and composable state machines.
 *
 * Uses Compose runtime ([not to confuse with Compose UI](https://jakewharton.com/a-jetpack-compose-by-any-other-name/))
 * to implement the state machine logic, manage the state, perform state updates, execute async work
 * and produce models.
 *
 * Simple "number counter" example:
 * ```kotlin
 *
 * class CounterStateMachine : StateMachine<Props, CounterModel> {
 *
 * data class CounterModel(
 *  val countText: String,
 *  val onCountIncrement: () -> Unit,
 *  val onExit: () -> Unit,
 * )
 *
 * data class Props(
 *  val initialCount: Int,
 *  val exit: () -> Unit,
 * )
 *
 *  @Composable
 *  override fun model(props: Props): CounterModel {
 *    var count: Int by remember { mutableStateOf(props.initialCount) }
 *
 *    return CounterModel(
 *      countText = "Count: $count",
 *      onCountIncrement = {
 *        count += 1
 *      },
 *      onExit = props.exit,
 *    )
 *  }
 * }
 * ```
 *
 * [StateMachine]s are composable, a parent [StateMachine] can "parameterize" its child
 * [StateMachine]'s using [PropsT] function argument.
 *
 * Recommended reads:
 * - [The state of managing state (with Compose)](https://code.cash.app/the-state-of-managing-state-with-compose).
 * - https://developer.android.com/jetpack/compose/state.
 */
interface StateMachine<in PropsT : Any, out ModelT : Any?> {
  /**
   * A [Composable] function that produces [ModelT]s for this state machine.
   *
   * Marked as `@HiddenFromObjC` because
   * 1. We don't consume this API directly from ObjC/Swift code. We consume it as [modelFlow] instead.
   * 2. Kotlin/Native compiler currently breaks if public ABI includes [Composable] functions due
   * to this [issue](https://github.com/JetBrains/compose-jb/issues/2346).
   */
  @Composable
  @HiddenFromObjC
  fun model(props: PropsT): ModelT

  /**
   * A way to consume [StateMachine] models as [Flow], using [Molecule](https://github.com/cashapp/molecule).
   *
   * Resulting [Flow] which will continually recompose [StateMachine.model] to produce a stream of
   * [ModelT] values when collected.
   *
   * This is used in combination with KMP-NativeCoroutines as an interop layer for ObjC/Swift code
   * to use [StateMachine]s.
   */
  @NativeCoroutines
  fun modelFlow(props: PropsT): Flow<ModelT> {
    return MainScope().plus(composeFrameClock()).launchMolecule(mode = ContextClock) {
      model(props)
    }
  }
}
