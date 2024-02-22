package build.wallet.statemachine.send

import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.Bitcoin
import build.wallet.statemachine.core.Icon.LargeIconSpeedometer
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer.Statement
import build.wallet.statemachine.core.form.FormMainContentModel.FeeOptionList
import build.wallet.statemachine.core.form.RenderContext.Sheet
import build.wallet.statemachine.send.TransferConfirmationUiProps.Variant
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryDestructive
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList

/**
 * A function which creates the form screen model for the transfer confirmation screen
 *
 * @param onBack - Handler for back press
 * @param onCancel - Handler for cancelling the transaction
 * @param recipientAddress - The recipient address of the transaction represented as a string
 * @param transactionDetails [TransactionDetailsModel] - The data associated with the transaction
 * @param requiresHardware - Flag representing if the transaction requires hardware signing
 * @param confirmButtonEnabled - Flag representing if the primary button is enabled
 * @param onConfirmClick - handler invoked once the primary button is clicked
 */
fun TransferConfirmationScreenModel(
  onBack: () -> Unit,
  onCancel: () -> Unit,
  variant: Variant,
  recipientAddress: String,
  transactionDetails: TransactionDetailsModel,
  requiresHardware: Boolean,
  confirmButtonEnabled: Boolean,
  errorOverlayModel: SheetModel? = null,
  onConfirmClick: () -> Unit,
  onNetworkFeesClick: () -> Unit,
  onArrivalTimeClick: (() -> Unit)?,
) = ScreenModel(
  body =
    FormBodyModel(
      onBack = onBack,
      header =
        when (variant) {
          is Variant.Regular ->
            FormHeaderModel(
              icon = Bitcoin,
              headline = "Send your transfer",
              subline = recipientAddress,
              sublineTreatment = FormHeaderModel.SublineTreatment.MONO,
              alignment = CENTER
            )
          is Variant.SpeedUp ->
            FormHeaderModel(
              icon = LargeIconSpeedometer,
              headline = "Speed up your transfer to",
              subline = recipientAddress,
              sublineTreatment = FormHeaderModel.SublineTreatment.MONO,
              alignment = CENTER
            )
        },
      toolbar =
        ToolbarModel(
          leadingAccessory =
            ToolbarAccessoryModel.ButtonAccessory(
              model =
                ButtonModel(
                  text = "Cancel",
                  treatment = TertiaryDestructive,
                  size = Compact,
                  onClick = Click.standardClick { onCancel() }
                )
            )
        ),
      mainContentList =
        transactionDetails.toDataList(
          variant = variant,
          onNetworkFeesClick = onNetworkFeesClick,
          onArrivalTimeClick = onArrivalTimeClick
        ),
      ctaWarning =
        when (variant) {
          is Variant.SpeedUp -> "You’ll only be charged the additional network fee."
          is Variant.Regular -> null
        },
      primaryButton =
        ButtonModel(
          text = "Send",
          requiresBitkeyInteraction = requiresHardware,
          onClick = onConfirmClick,
          isEnabled = confirmButtonEnabled,
          treatment = Primary,
          size = Footer
        ),
      id = SendEventTrackerScreenId.SEND_CONFIRMATION,
      eventTrackerShouldTrack = false
    ),
  presentationStyle = ScreenPresentationStyle.ModalFullScreen,
  bottomSheetModel = errorOverlayModel
)

fun NetworkFeesInfoSheetModel(onBack: () -> Unit) =
  FormBodyModel(
    onBack = onBack,
    toolbar =
      ToolbarModel(
        leadingAccessory = CloseAccessory(onClick = onBack),
        middleAccessory = ToolbarMiddleAccessoryModel(title = "Network Fees")
      ),
    header = null,
    primaryButton =
      ButtonModel(
        text = "Got it",
        size = Footer,
        onClick = Click.sheetClosingClick { onBack() }
      ),
    mainContentList =
      immutableListOf(
        Explainer(
          items =
            immutableListOf(
              Statement(
                title = null,
                body = FEES_EXPLAINER
              )
            )
        )
      ),
    renderContext = Sheet,
    id = SendEventTrackerScreenId.SEND_NETWORK_FEES_INFO_SHEET,
    eventTrackerShouldTrack = false
  )

fun FeeSelectionSheetModel(
  onBack: () -> Unit,
  feeOptionList: FeeOptionList,
) = FormBodyModel(
  onBack = onBack,
  toolbar =
    ToolbarModel(
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

private val FEES_EXPLAINER =
  """
  Network fees are small amounts paid by users when sending bitcoin transactions.
  
  These fees compensate miners for validating and adding transactions to the blockchain. The higher the fee, the more likely your transaction will be processed quickly, as miners prioritize transactions with higher fees.
  
  It’s important to note that these fees do not go to wallet providers. They are solely for supporting the Bitcoin network.
  """.trimIndent()

private fun TransactionDetailsModel.toDataList(
  variant: Variant,
  onNetworkFeesClick: (() -> Unit)? = null,
  onArrivalTimeClick: (() -> Unit)?,
): ImmutableList<DataList> {
  val mainItems: ImmutableList<DataList.Data> =
    when (transactionDetailModelType) {
      is TransactionDetailModelType.Regular ->
        immutableListOf(
          DataList.Data(
            title = "Recipient receives",
            sideText = transactionDetailModelType.transferAmountText
          ),
          DataList.Data(
            title = "Network Fees",
            onTitle = onNetworkFeesClick,
            titleIcon =
              IconModel(
                icon = Icon.SmallIconInformationFilled,
                iconSize = IconSize.XSmall,
                iconTint = IconTint.On30
              ),
            sideText = transactionDetailModelType.feeAmountText
          )
        )
      is TransactionDetailModelType.SpeedUp ->
        immutableListOf(
          DataList.Data(
            title = "Recipient receives",
            sideText = transactionDetailModelType.transferAmountText
          ),
          DataList.Data(
            title = "Original network fee",
            onTitle = onNetworkFeesClick,
            sideText = transactionDetailModelType.oldFeeAmountText
          ),
          DataList.Data(
            title = "Speed up network fee",
            onTitle = onNetworkFeesClick,
            sideText = transactionDetailModelType.feeDifferenceText
          )
        )
    }

  return immutableListOf(
    DataList(
      items =
        immutableListOf(
          DataList.Data(
            title =
              when (variant) {
                is Variant.Regular -> "Arrival time"
                is Variant.SpeedUp -> "New arrival time"
              },
            sideText = transactionSpeedText,
            onClick = onArrivalTimeClick
          )
        )
    ),
    DataList(
      items = mainItems,
      total =
        DataList.Data(
          title = "Total Cost",
          sideText = totalAmountPrimaryText,
          sideTextType = DataList.Data.SideTextType.BODY2BOLD,
          secondarySideText = totalAmountSecondaryText
        )
    )
  )
}
