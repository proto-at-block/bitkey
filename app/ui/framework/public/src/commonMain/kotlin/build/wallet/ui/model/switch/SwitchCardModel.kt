package build.wallet.ui.model.switch

import build.wallet.statemachine.core.Icon
import kotlinx.collections.immutable.ImmutableList

data class SwitchCardModel(
  val icon: Icon? = null,
  val title: String,
  val subline: String,
  val switchModel: SwitchModel,
  val actionRows: ImmutableList<ActionRow>,
) {
  data class ActionRow(
    val title: String,
    val sideText: String,
    val onClick: () -> Unit,
  )
}
