package build.wallet.statemachine.recovery.inprogress

import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.compose.collections.immutableListOf
import build.wallet.recovery.getEventId
import build.wallet.statemachine.core.Icon.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer
import build.wallet.statemachine.core.form.FormMainContentModel.Explainer.Statement
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryDestructive
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.ButtonAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

data class RecoverYourAppKeyBodyModel(
  override val onBack: () -> Unit,
  val onStartRecovery: () -> Unit,
) : FormBodyModel(
    id = DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_INITIATION_INSTRUCTIONS,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
    header = FormHeaderModel(
      headline = "Recover your wallet with a new App Key"
    ),
    mainContentList = immutableListOf(
      Explainer(
        immutableListOf(
          Statement(
            leadingIcon = SmallIconClock,
            title = "7-day security waiting period",
            body = "During this time, you’ll get regular alerts about the recovery.  No action is needed; these are to keep you informed and aware."
          ),
          Statement(
            leadingIcon = SmallIconMinusStroked,
            title = "Cancel anytime",
            body = "You can cancel this process anytime, and return to using your old phone or restore using your existing cloud backup if you find either of them later."
          ),
          Statement(
            leadingIcon = SmallIconBitcoinStroked,
            title = "Small network fee required",
            body = "To recover your key, you’ll need to initiate an on-chain transaction to complete the process."
          )
        )
      )
    ),
    primaryButton = BitkeyInteractionButtonModel(
      text = "Start recovery",
      onClick = StandardClick(onStartRecovery),
      size = Footer
    )
  )

data class DelayAndNotifyNewKeyReady(
  val factorToRecover: PhysicalFactor,
  val onStopRecovery: (() -> Unit)?,
  val onCompleteRecovery: () -> Unit,
  val onExit: (() -> Unit)?,
) : FormBodyModel(
    id = factorToRecover.getEventId(
      DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_READY,
      HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_READY
    ),
    onBack = onExit,
    toolbar = ToolbarModel(
      leadingAccessory = onExit?.let {
        IconAccessory.CloseAccessory(onClick = onExit)
      },
      trailingAccessory = onStopRecovery?.let {
        ButtonAccessory(
          model = ButtonModel(
            text = "Cancel recovery",
            treatment = TertiaryDestructive,
            size = Compact,
            onClick = StandardClick { onStopRecovery() }
          )
        )
      }
    ),
    header = FormHeaderModel(
      icon = LargeIconCheckStroked,
      headline = when (factorToRecover) {
        App -> "Confirm App Key replacement"
        Hardware -> "Confirm your replacement device"
      },
      subline = buildString {
        when (factorToRecover) {
          App -> append(
            "The security waiting period is complete and your new App Key is ready to be created."
          )
          Hardware -> append(
            "The security waiting period is complete and your new Bitkey device is ready to use."
          )
        }
        appendLine()
        appendLine()
        append(
          "To finish your recovery, you’ll need to approve a transaction, including an additional network fee."
        )
        appendLine()
        appendLine()
        append(
          "If this fee appears very high, the network may be very busy. You can try returning later when fees may be lower."
        )
      }
    ),
    primaryButton = BitkeyInteractionButtonModel(
      text = "Confirm replacement",
      onClick = StandardClick(onCompleteRecovery),
      size = Footer
    ),
    eventTrackerShouldTrack = false
  )
