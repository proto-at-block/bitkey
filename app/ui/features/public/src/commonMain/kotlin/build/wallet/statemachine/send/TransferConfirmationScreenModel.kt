package build.wallet.statemachine.send

import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.partnerships.PartnerInfo
import build.wallet.statemachine.core.Icon.Bitcoin
import build.wallet.statemachine.core.Icon.SmallIconInformationFilled
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.LEADING
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.*
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.statemachine.send.TransferConfirmationScreenVariant.*
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.icon.IconImage.UrlImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconSize.Avatar
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.collections.immutable.ImmutableList

/**
 * A function which creates the form screen model for the transfer confirmation screen
 *
 * @param onBack - Handler for back press
 * @param variant - The variant of the transfer confirmation screen (Regular, SpeedUp, Sale)
 * @param recipientAddress - The recipient address of the transaction represented as a string
 * @param transactionDetails [TransactionDetailsModel] - The data associated with the transaction
 * @param requiresHardware - Flag representing if the transaction requires hardware signing
 * @param confirmButtonEnabled - Flag representing if the primary button is enabled
 * @param onConfirmClick - Handler invoked when the primary button is clicked
 * @param onNetworkFeesClick - Handler for network fees click (null when disabled)
 * @param onArrivalTimeClick - Handler for arrival time click (null when disabled)
 */
data class TransferConfirmationScreenModel(
  override val onBack: () -> Unit,
  val variant: TransferConfirmationScreenVariant,
  @Redacted
  val recipientAddress: BitcoinAddress,
  val transactionDetails: TransactionDetailsModel,
  val requiresHardware: Boolean,
  val confirmButtonEnabled: Boolean,
  val requiresHardwareReview: Boolean,
  val onConfirmClick: () -> Unit,
  val onNetworkFeesClick: () -> Unit,
  val onArrivalTimeClick: (() -> Unit)?,
) : FormBodyModel(
    onBack = onBack,
    header =
      when (variant) {
        Regular ->
          FormHeaderModel(
            icon = Bitcoin,
            headline = "Send your transfer",
            subline = recipientAddress.chunkedAddress(),
            sublineTreatment = FormHeaderModel.SublineTreatment.MONO,
            alignment = LEADING
          )
        SpeedUp ->
          FormHeaderModel(
            icon = Bitcoin,
            headline = "Speed up your transfer to",
            subline = recipientAddress.chunkedAddress(),
            sublineTreatment = FormHeaderModel.SublineTreatment.MONO,
            alignment = LEADING
          )
        is Sell ->
          FormHeaderModel(
            iconModel = IconModel(
              iconImage = when (val url = variant.partnerInfo.logoUrl) {
                null -> LocalImage(Bitcoin)
                else -> UrlImage(
                  url = url,
                  fallbackIcon = Bitcoin
                )
              },
              iconSize = Avatar
            ),
            headline = "Confirm ${variant.partnerInfo.name} sale",
            subline = "Arrival times and fees are estimates.",
            alignment = LEADING
          )
        PrivateWalletMigration ->
          FormHeaderModel(
            icon = Bitcoin,
            headline = "Transaction summary",
            subline = recipientAddress.chunkedAddress(),
            sublineTreatment = FormHeaderModel.SublineTreatment.MONO,
            alignment = LEADING
          )
      },
    toolbar = when (variant) {
      PrivateWalletMigration -> null
      else -> ToolbarModel(leadingAccessory = BackAccessory(onBack))
    },
    mainContentList = transactionDetails.toFormContent(
      variant = variant,
      onNetworkFeesClick = onNetworkFeesClick,
      onArrivalTimeClick = onArrivalTimeClick
    ),
    ctaWarning = null,
    primaryButton =
      ButtonModel(
        text = when (variant) {
          is Sell -> "Confirm"
          PrivateWalletMigration -> if (requiresHardwareReview) "Review on Bitkey" else "Send"
          else -> if (requiresHardwareReview) "Review on Bitkey" else "Send"
        },
        requiresBitkeyInteraction = requiresHardware,
        onClick = onConfirmClick,
        isEnabled = confirmButtonEnabled,
        treatment = Primary,
        size = Footer
      ),
    id = SendEventTrackerScreenId.SEND_CONFIRMATION,
    eventTrackerShouldTrack = false
  )

data class FeeSelectionSheetModel(
  override val onBack: () -> Unit,
  val feeOptionList: FeeOptionList,
) : FormBodyModel(
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = CloseAccessory(onClick = onBack),
      middleAccessory = ToolbarMiddleAccessoryModel(title = "Transfer speed")
    ),
    header = null,
    primaryButton = null,
    mainContentList = immutableListOf(feeOptionList),
    renderContext = Sheet,
    id = SendEventTrackerScreenId.SEND_FEES_SELECTION_SHEET,
    eventTrackerShouldTrack = false
  )

private fun TransactionDetailsModel.toFormContent(
  variant: TransferConfirmationScreenVariant,
  onNetworkFeesClick: (() -> Unit)? = null,
  onArrivalTimeClick: (() -> Unit)?,
): ImmutableList<FormMainContentModel> {
  val mainItems: ImmutableList<DataList.Data> =
    when (transactionDetailModelType) {
      is TransactionDetailModelType.Regular ->
        immutableListOf(
          DataList.Data(
            title = "Amount",
            sideText = transactionDetailModelType.transferAmountText,
            secondarySideText = transactionDetailModelType.transferAmountSecondaryText
          ),
          DataList.Data(
            title = "Network fees",
            onTitle = onNetworkFeesClick,
            titleIcon =
              IconModel(
                icon = SmallIconInformationFilled,
                iconSize = IconSize.XSmall,
                iconTint = IconTint.On30
              ),
            sideText = transactionDetailModelType.feeAmountText,
            secondarySideText = transactionDetailModelType.feeAmountSecondaryText
          )
        )
      is TransactionDetailModelType.SpeedUp ->
        immutableListOf(
          DataList.Data(
            title = "Amount",
            sideText = transactionDetailModelType.transferAmountText,
            secondarySideText = transactionDetailModelType.transferAmountSecondaryText
          ),
          DataList.Data(
            title = "Original network fee",
            onTitle = onNetworkFeesClick,
            sideText = transactionDetailModelType.oldFeeAmountText,
            secondarySideText = transactionDetailModelType.oldFeeAmountSecondaryText
          ),
          DataList.Data(
            title = "Speed up network fee",
            onTitle = onNetworkFeesClick,
            sideText = transactionDetailModelType.feeDifferenceText,
            secondarySideText = transactionDetailModelType.feeDifferenceSecondaryText
          )
        )
    }

  return buildImmutableList {
    add(Divider)
    add(
      DataList(
        items = immutableListOf(
          DataList.Data(
            title = when (variant) {
              Regular, is Sell -> "Arrival time"
              SpeedUp -> "New arrival time"
              PrivateWalletMigration -> "Arrival time"
            },
            sideText = transactionSpeedText,
            onClick = onArrivalTimeClick
          )
        )
      )
    )
    add(
      DataList(
        items = mainItems,
        total = DataList.Data(
          title = "Total",
          sideText = transactionDetailModelType.totalAmountPrimaryText,
          sideTextType = DataList.Data.SideTextType.BODY2BOLD,
          secondarySideText = transactionDetailModelType.totalAmountSecondaryText
        )
      )
    )
  }
}

sealed interface TransferConfirmationScreenVariant {
  /**
   * Transaction confirmation for the regular send flow.
   */
  data object Regular : TransferConfirmationScreenVariant

  /**
   * Transaction confirmation when trying to speed up a transaction.
   */
  data object SpeedUp : TransferConfirmationScreenVariant

  /**
   * Transaction confirmation for the sell flow.
   */
  data class Sell(
    val partnerInfo: PartnerInfo,
  ) : TransferConfirmationScreenVariant

  /**
   * Transaction confirmation for private wallet migration sweep.
   */
  data object PrivateWalletMigration : TransferConfirmationScreenVariant
}
