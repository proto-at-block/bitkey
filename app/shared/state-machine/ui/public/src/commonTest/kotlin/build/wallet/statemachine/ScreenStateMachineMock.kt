package build.wallet.statemachine

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for testing. Produces [BodyModelMock] with the associated id and props.
 *
 * @param id The id included with the [BodyModelMock].
 */
abstract class ScreenStateMachineMock<PropsT : Any>(
  val id: String,
) : StateMachine<PropsT, ScreenModel> {
  @Composable
  override fun model(props: PropsT): ScreenModel {
    return BodyModelMock(
      id = id,
      latestProps = props
    ).asRootScreen()
  }
}
