package build.wallet.ui.app.nfc

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.notifications.NotificationOperationApprovalInstructionsFormScreenModel
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiState.ActivationApprovalInstructionsUiState.ErrorBottomSheetState
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationContent
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.screen.Screen
import io.kotest.core.spec.style.FunSpec

class NotificationHardwareApprovalFlowSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("notification onboarding hardware approval instructions screen") {
    paparazzi.snapshot {
      Screen(
        model = NotificationOperationApprovalInstructionsFormScreenModel(
          onExit = {},
          headline = "Confirm details on your Bitkey",
          operationDescription = "Your Bitkey must approve changes to your security settings. Review and approve saving asdf@block.xyz as your recovery email.",
          primaryButtonText = "Continue",
          onApprove = {},
          isApproveButtonLoading = false,
          errorBottomSheetState = ErrorBottomSheetState.Hidden
        )
      )
    }
  }

  test("notification action proof hardware confirmation screen") {
    paparazzi.snapshot {
      FormScreen(
        model = HardwareConfirmationScreenModel(
          onBack = {},
          onConfirm = {},
          content = HardwareConfirmationContent.SignActionProof
        )
      )
    }
  }
})
