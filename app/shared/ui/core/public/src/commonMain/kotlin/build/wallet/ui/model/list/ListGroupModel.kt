package build.wallet.ui.model.list

import build.wallet.ui.model.button.ButtonModel
import kotlinx.collections.immutable.ImmutableList

data class ListGroupModel(
  val header: String? = null,
  val items: ImmutableList<ListItemModel>,
  val style: ListGroupStyle,
  val headerTreatment: HeaderTreatment = HeaderTreatment.SECONDARY,
  val footerButton: ButtonModel? = null,
) {
  enum class HeaderTreatment {
    PRIMARY,
    SECONDARY,
  }
}

/**
 * See ListGroup.kt for visual previews
 */
enum class ListGroupStyle {
  /**
   * Lays out the items in a column with no other styling
   */
  NONE,

  /**
   * Lays out the items in a column with a divider line in between each
   */
  DIVIDER,

  /**
   * List in the form of a singular card, with no divider between items.
   */
  CARD_GROUP,

  /**
   * List in the form of a singular card, with a divider between items.
   */
  CARD_GROUP_DIVIDER,

  /**
   * List in the form of disjoint card views, one card per item, with padding around the item.
   */
  CARD_ITEM,

  /**
   * List in the form of disjoint card views, with up to three cards per row.
   */
  THREE_COLUMN_CARD_ITEM,
}
