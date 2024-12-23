package build.wallet.statemachine.moneyhome.card

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import build.wallet.Progress
import build.wallet.pricechart.DataPoint
import build.wallet.pricechart.PriceDirection
import build.wallet.statemachine.core.ComposableRenderedModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.ui.components.card.PendingClaimContent
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
data class MoneyHomeCardsModel(
  val cards: ImmutableList<CardModel>,
) : Model()

/**
 * Model representing a card that can be rendered on the Money Home screen via [MoneyHomeCardsUiStateMachine]
 *
 * @property heroImage - an optional image to be shown above the title
 * @property title - the title to be rendered on the card
 * @property subtitle - an optional subtitle field to be rendered in the card
 * @property leadingImage - an optional image that can be shown to the left of the title + subtitle
 * @property content - the optional contents of the card that can render in various forms
 * @property animation - optional list of animations to play out on the card
 */
@Immutable
data class CardModel(
  val heroImage: Icon? = null,
  val title: LabelModel.StringWithStyledSubstringModel?,
  val subtitle: String? = null,
  val leadingImage: CardImage? = null,
  val trailingButton: ButtonModel? = null,
  val content: CardContent?,
  val style: CardStyle,
  val onClick: (() -> Unit)? = null,
  val animation: ImmutableList<AnimationSet>? = null,
) : Model() {
  /** The style of the card */
  sealed class CardStyle {
    data object Plain : CardStyle()

    data object Outline : CardStyle()

    data class Gradient(val backgroundColor: BackgroundColor? = null) : CardStyle() {
      enum class BackgroundColor {
        Warning,
      }
    }

    data class Callout(val model: CalloutModel) : CardStyle()
  }

  /** The optional image for the card */
  sealed interface CardImage {
    /** A static image that doesn't change */
    data class StaticImage(val icon: Icon, val iconTint: IconTint? = null) : CardImage {
      enum class IconTint {
        Warning,
      }
    }

    /** A dynamic image that does change and is dynamically drawn */
    sealed interface DynamicImage : CardImage {
      data class HardwareReplacementStatusProgress(
        val progress: Progress,
        val remainingSeconds: Long,
      ) : DynamicImage
    }
  }

  /** The optional content for the card */
  sealed interface CardContent {
    /**
     * A [CardContent] body that shows a list of actionable items in a drill list
     *
     * @property items - the list of items to render
     */
    data class DrillList(val items: ImmutableList<ListItemModel>) : CardContent

    data class BitcoinPrice(
      val isLoading: Boolean,
      val price: String,
      val priceChange: String,
      val priceDirection: PriceDirection,
      val lastUpdated: String,
      val data: ImmutableList<DataPoint>,
    ) : CardContent

    data class PendingClaim(
      val title: String,
      val subtitle: String,
      val isPendingClaim: Boolean,
      val timeRemaining: Duration,
      val progress: Progress,
      val onClick: (() -> Unit)?,
      override val key: String = "pending-claim",
    ) : CardContent, ComposableRenderedModel {
      @Composable
      override fun render(modifier: Modifier) {
        PendingClaimContent(
          model = this
        )
      }
    }
  }

  /** Describes a set of animation actions that should happen concurrently */
  data class AnimationSet(
    val animations: Set<Animation>,
    val durationInSeconds: Double,
  ) {
    /** Describes various supported animations */
    sealed interface Animation {
      /** Describes an animation of the scale factor of the card view. */
      data class Scale(val value: Float) : Animation

      /** Describes an animation of the height of the card view. */
      data class Height(val value: Float) : Animation
    }
  }
}
