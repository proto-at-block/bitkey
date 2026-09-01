package build.wallet.statemachine.moneyhome.card.gettingstarted

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import build.wallet.ui.app.moneyhome.gettingstarted.GettingStartedSection
import build.wallet.ui.model.ComposeModel
import build.wallet.ui.model.icon.IconModel
import kotlinx.collections.immutable.ImmutableList

/**
 * Model for the "Getting Started" section on Money Home.
 *
 * This is rendered as a section (header + horizontally scrolling tiles) rather than a card —
 * it doesn't share chrome with [build.wallet.statemachine.moneyhome.card.CardModel] variants, so
 * it lives outside the [CardModel] sealed type and is hosted directly by
 * [build.wallet.statemachine.moneyhome.MoneyHomeBodyModel].
 */
@Immutable
data class GettingStartedSectionModel(
  val title: String,
  val incomplete: ImmutableList<GettingStartedTileModel>,
  val complete: ImmutableList<GettingStartedTileModel>,
) : ComposeModel {
  /** All tiles in display order: incomplete first, then complete. */
  val tiles: List<GettingStartedTileModel> get() = incomplete + complete

  @Composable
  override fun render(modifier: Modifier) {
    GettingStartedSection(modifier = modifier, model = this)
  }
}

data class GettingStartedTileModel(
  val id: Id,
  val title: String,
  val leadingIcon: IconModel?,
  val isEnabled: Boolean,
  val isComplete: Boolean,
  val onClick: (() -> Unit)? = null,
) {
  enum class Id {
    UpdateFirmware,
    AddBitcoin,
    EnableSpendingLimit,
  }
}
