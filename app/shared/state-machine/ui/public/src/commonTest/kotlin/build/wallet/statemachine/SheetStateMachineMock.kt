package build.wallet.statemachine

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for testing state machines that return a [SheetModel].
 * Produces [BodyModelMock] with the associated id and props.
 *
 * @param id The id included with the [BodyModelMock].
 */
abstract class SheetStateMachineMock<PropsT : Any>(
  val id: String,
) : StateMachine<PropsT, SheetModel> {
  @Composable
  override fun model(props: PropsT): SheetModel {
    return BodyModelMock(
      id = id,
      latestProps = props
    ).asSheetModalScreen {}
  }
}
