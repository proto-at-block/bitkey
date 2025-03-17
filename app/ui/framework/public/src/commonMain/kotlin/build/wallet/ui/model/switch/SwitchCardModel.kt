package build.wallet.ui.model.switch

import kotlinx.collections.immutable.ImmutableList

data class SwitchCardModel(
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
