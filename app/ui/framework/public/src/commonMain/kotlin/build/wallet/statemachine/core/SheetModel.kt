package build.wallet.statemachine.core

import build.wallet.statemachine.core.SheetSize.DEFAULT

/**
 * Model for the sheet, this should be used in along with [ScreenModel].
 *
 * @param [treatment] defines appearance of the sheet, currently just the background color.
 * @param [onClosed] called whenever the sheet gets closed. A state machine should always implement
 * @param [body] model content of the sheet.
 */
data class SheetModel(
  val size: SheetSize = DEFAULT,
  val treatment: SheetTreatment = SheetTreatment.STANDARD,
  val onClosed: () -> Unit,
  val body: BodyModel,
)

enum class SheetSize {
  // This uses the size of the contents on the sheet to determine the sheet size
  DEFAULT,

  /* This uses a min size of 40% of the screen and uses the `DEFAULT` sizing if the contents
   * takes up > 40%, this can be used if there is series of bottom sheets that need to be displayed
   * and we want to keep sizing consistent */
  MIN40,

  // This brings the sheet to the full size
  FULL,
}

/**
 * Specifies how we want content to be aligned vertically
 */
enum class VerticalAlignment {
  /**
   * Contents are aligned to the top of the sheet
   */
  TOP,

  /**
   * Contents are aligned to the center of the sheet
   */
  CENTER,
}

enum class SheetTreatment {
  // Default background color
  STANDARD,

  // Inheritance background color
  INHERITANCE,
}
