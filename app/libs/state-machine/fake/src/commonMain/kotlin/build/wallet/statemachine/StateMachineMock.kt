package build.wallet.statemachine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import build.wallet.statemachine.core.StateMachine
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Mock state machine for testing.
 *
 * Emits [initialModel] by default, then emits any updates to [model] mutable state.
 */
abstract class StateMachineMock<PropsT : Any, ModelT : Any?>(
  private val initialModel: ModelT,
) : StateMachine<PropsT, ModelT> {
  private val model = MutableStateFlow(initialModel)
  lateinit var props: PropsT

  /**
   * Keeps this [model] to emit later.
   */
  fun emitModel(model: ModelT) {
    this.model.value = model
  }

  @Composable
  override fun model(props: PropsT): ModelT {
    this.props = props
    return model.collectAsState().value
  }

  fun reset() {
    model.value = initialModel
  }
}
