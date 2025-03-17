package build.wallet.statemachine

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for testing. Produces [BodyModelMock] with the associated id and props.
 *
 * @param id The id included with the [BodyModelMock].
 */
abstract class BodyStateMachineMock<PropsT : Any>(
  val id: String,
) : StateMachine<PropsT, BodyModel> {
  @Composable
  override fun model(props: PropsT): BodyModel {
    return BodyModelMock(
      id = id,
      latestProps = props
    )
  }
}
