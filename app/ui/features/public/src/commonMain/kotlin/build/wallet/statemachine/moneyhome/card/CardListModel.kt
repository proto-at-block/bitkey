package build.wallet.statemachine.moneyhome.card

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.Progress
import build.wallet.pricechart.DataPoint
import build.wallet.pricechart.PriceDirection
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.ui.app.moneyhome.card.BitcoinPriceCardContainer
import build.wallet.ui.app.moneyhome.card.DrillListCardContainer
import build.wallet.ui.app.moneyhome.card.HeroCardContainer
import build.wallet.ui.app.moneyhome.card.StatusCardContainer
import build.wallet.ui.components.callout.Callout
import build.wallet.ui.components.card.PendingClaimContent
import build.wallet.ui.model.ComposeModel
import build.wallet.ui.model.Model
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.list.ListItemModel
import kotlinx.collections.immutable.ImmutableList
import kotlin.time.Duration

/**
 * Model representing cards to be shown in Money Home screen
 *
 * @property cards - A list of [CardModel] objects that will render in Money Home. When this list is
 * empty, there are no cards to show
 */
data class CardListModel(
  val cards: ImmutableList<CardModel>,
) : Model

/**
 * Sealed model representing a card that can be rendered on the Money Home screen via
 * [MoneyHomeCardsUiStateMachine].
 *
 * Each variant represents a distinct, rendering-meaningful card shape. Renderers should switch on
 * the concrete subtype rather than inspecting optional fields. Every variant supplies its own
 * [render] implementation; the host (`Card`) wraps it in click handling via
 * [cardOnClick].
 */
@Immutable
sealed interface CardModel : ComposeModel {
  /**
   * Card displaying the current Bitcoin price with a sparkline chart.
   */
  data class BitcoinPrice(
    val isLoading: Boolean,
    val price: String,
    val priceValue: Long? = null,
    val priceAnimationKey: Long = 0L,
    val priceChange: String,
    val priceDirection: PriceDirection,
    val data: ImmutableList<DataPoint>,
    val onClick: (() -> Unit)? = null,
  ) : CardModel {
    @Composable
    override fun render(modifier: Modifier) {
      BitcoinPriceCardContainer(modifier = modifier, model = this)
    }
  }

  /**
   * Beneficiary inheritance pending claim card with a countdown timer.
   */
  data class PendingClaim(
    val id: String,
    val title: String,
    val subtitle: String,
    val state: State,
    val timeRemaining: Duration,
    val progress: Progress,
    val onClick: (() -> Unit)?,
  ) : CardModel {
    override val key: String = "PendingClaim:$id"

    /** Visual/interaction state of a pending-claim card. */
    enum class State {
      /** The claim is in the delay period, counting down. Renders dismiss/cancel control. */
      Pending,

      /** The claim's delay period has elapsed and is ready to be completed. Renders CTA. */
      Locked,
    }

    // PendingClaim wires its onClick to explicit controls inside the rendered content
    // (CTA / dismiss), so the card background is intentionally not tappable.

    @Composable
    override fun render(modifier: Modifier) {
      PendingClaimContent(model = this, modifier = modifier)
    }
  }

  /**
   * Callout-style card (e.g. warnings, alerts) wrapping a [CalloutModel].
   */
  data class Callout(
    val id: String,
    val callout: CalloutModel,
  ) : CardModel {
    override val key: String = "Callout:$id"

    @Composable
    override fun render(modifier: Modifier) {
      Box(modifier = modifier.padding(horizontal = 16.dp)) {
        Callout(model = callout)
      }
    }
  }

  /**
   * Emphasis "row" card with a gradient/highlight background. Used for hardware recovery,
   * fingerprint reset, and recovery-contact callouts that need to stand out.
   */
  data class Status(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val leadingImage: Image,
    /** When true, the card uses the inverse/highlight background treatment. */
    val inverse: Boolean = false,
    val onClick: (() -> Unit)? = null,
  ) : CardModel {
    override val key: String = "Status:$id"

    @Composable
    override fun render(modifier: Modifier) {
      StatusCardContainer(modifier = modifier, model = this)
    }

    /** An image rendered on the leading edge of a [Status] card. */
    sealed interface Image {
      /** A static image that doesn't change. */
      data class StaticImage(val icon: Icon) : Image

      /** Hardware replacement progress indicator. */
      data class HardwareReplacementStatusProgress(
        val progress: Progress,
        val remainingSeconds: Long,
      ) : Image
    }
  }

  /**
   * Hero card with a top image, title, optional subtitle, and optional primary/secondary CTAs.
   */
  data class Hero(
    val heroImage: Icon,
    val title: LabelModel.StringWithStyledSubstringModel,
    val subtitle: String? = null,
    val primaryButton: ButtonModel? = null,
    val secondaryButton: ButtonModel? = null,
    val onClick: (() -> Unit)? = null,
    val surfaceTreatment: SurfaceTreatment = SurfaceTreatment.ContainerBackground,
  ) : CardModel {
    override val key: String = "Hero:${title.string}"

    @Composable
    override fun render(modifier: Modifier) {
      HeroCardContainer(modifier = modifier, model = this)
    }
  }

  /**
   * Card whose body is a list of actionable drill-down rows underneath a title.
   */
  data class DrillList(
    val title: LabelModel.StringWithStyledSubstringModel,
    val items: ImmutableList<ListItemModel>,
  ) : CardModel {
    @Composable
    override fun render(modifier: Modifier) {
      DrillListCardContainer(modifier = modifier, model = this)
    }
  }

  enum class SurfaceTreatment {
    ContainerBackground,
    Background,
  }
}

/**
 * Card-level tap handler for variants that want their whole background tappable.
 *
 * Variants whose taps are handled by inner controls (e.g. [CardModel.PendingClaim]'s CTA /
 * dismiss buttons, [CardModel.Callout]) return null so the host doesn't stack a second clickable.
 */
val CardModel.cardOnClick: (() -> Unit)?
  get() = when (this) {
    is CardModel.Hero -> onClick
    is CardModel.Status -> onClick
    is CardModel.BitcoinPrice -> onClick
    is CardModel.PendingClaim,
    is CardModel.Callout,
    is CardModel.DrillList,
    -> null
  }
