package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.statemachine.core.input.EmailInputScreenModel

/**
 * Skip model to show when the customer is allowed to skip phone number input.
 */
fun PhoneNumberInputSkipAllowedSheetModel(
  onGoBack: () -> Unit,
  onSkip: () -> Unit,
) = SheetModel(
  onClosed = onGoBack,
  body =
    ErrorFormBodyModel(
      title = "Are you sure you want to skip?",
      subline =
        "Providing a phone number significantly enhances your wallet’s security" +
          " and your ability to recover your funds in case of any unforeseen issues.",
      primaryButton =
        ButtonDataModel(
          text = "Go Back",
          onClick = onGoBack
        ),
      secondaryButton =
        ButtonDataModel(
          text = "Skip",
          onClick = onSkip
        ),
      renderContext = Sheet,
      eventTrackerScreenId = NotificationsEventTrackerScreenId.SMS_SKIP_SHEET
    )
)

/**
 * Creates a sheet model associated with [EmailInputScreenModel] to display information as an
 * overlay
 *
 * @param onGoBack - invoked once the user chooses to go back to email input
 * @param onSkip - invoked once the user chooses to skip email entry
 */
fun EmailInputSkipAllowedSheetModel(
  onGoBack: () -> Unit,
  onSkip: () -> Unit,
) = SheetModel(
  onClosed = onGoBack,
  body =
    ErrorFormBodyModel(
      title = "Are you sure you want to skip?",
      subline =
        "Providing an email address significantly enhances your wallet’s security and your" +
          " ability to recover your funds in case of any unforeseen issues.",
      primaryButton =
        ButtonDataModel(
          text = "Go Back",
          onClick = onGoBack
        ),
      secondaryButton =
        ButtonDataModel(
          text = "Skip",
          onClick = onSkip
        ),
      renderContext = Sheet,
      eventTrackerScreenId = NotificationsEventTrackerScreenId.EMAIL_SKIP_SHEET
    )
)

/**
 * Skip model to show when the customer is NOT allowed to skip the contact input.
 */
fun SkipNotAllowedSheetModel(
  enterOtherContactButtonText: String,
  onGoBack: () -> Unit,
  onEnterOtherContact: () -> Unit,
) = SheetModel(
  onClosed = onGoBack,
  body =
    ErrorFormBodyModel(
      title = "You need a phone number or email address to continue",
      subline =
        "Providing a contact method significantly enhances your wallet’s security and" +
          " your ability to recover your funds in case of any unforeseen issues.",
      primaryButton =
        ButtonDataModel(
          text = "Go Back",
          onClick = onGoBack
        ),
      secondaryButton =
        ButtonDataModel(
          text = enterOtherContactButtonText,
          onClick = onEnterOtherContact
        ),
      renderContext = Sheet,
      eventTrackerScreenId = NotificationsEventTrackerScreenId.NO_SKIP_ALLOWED_SHEET
    )
)
