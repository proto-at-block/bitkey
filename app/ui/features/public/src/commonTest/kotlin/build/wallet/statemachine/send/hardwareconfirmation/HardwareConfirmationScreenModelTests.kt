package build.wallet.statemachine.send.hardwareconfirmation

import build.wallet.analytics.events.screen.id.HardwareConfirmationEventTrackerScreenId
import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.bitcoin.address.BitcoinAddress
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class HardwareConfirmationScreenModelTests : FunSpec({

  test("recipientAddress is null by default on content") {
    val model = HardwareConfirmationScreenModel(
      onBack = {},
      onConfirm = {}
    )
    model.content.recipientAddress.shouldBeNull()
  }

  test("preFooterContent is populated when content has recipientAddress") {
    val address = BitcoinAddress("bc1q42lja79elem0anu8q8s3h2n687re9jax556pcc")
    val model = HardwareConfirmationScreenModel(
      onBack = {},
      onConfirm = {},
      content = HardwareConfirmationContent.SendTransaction.copy(
        recipientAddress = address
      )
    )
    model.content.recipientAddress.shouldBe(address)
    model.designSystemV2Model!!.preFooterMainContentList.shouldHaveSize(1)
    model.designSystemV2Model!!.preFooterMainContentList.first()
      .shouldBeInstanceOf<build.wallet.statemachine.core.form.FormMainContentModel.CollapsibleAddress>()
  }

  test("preFooterContent is empty when content has no recipientAddress") {
    val model = HardwareConfirmationScreenModel(
      onBack = {},
      onConfirm = {},
      content = HardwareConfirmationContent.ConsolidateUtxos
    )
    model.content.recipientAddress.shouldBeNull()
    model.designSystemV2Model!!.preFooterMainContentList.shouldBeEmpty()
  }

  test("each content variant sets the correct screen id on HardwareConfirmationScreenModel") {
    mapOf(
      HardwareConfirmationContent.SignTransaction to
        HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_SIGN_TRANSACTION,
      HardwareConfirmationContent.SendTransaction to
        SendEventTrackerScreenId.SEND_HARDWARE_CONFIRMATION,
      HardwareConfirmationContent.ConsolidateUtxos to
        HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CONSOLIDATE_UTXOS,
      HardwareConfirmationContent.FirmwareUpdate to
        HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_FWUP,
      HardwareConfirmationContent.SignActionProof to
        HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_SIGN_ACTION_PROOF,
      HardwareConfirmationContent.LostAppRecovery to
        HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_RECOVERY,
      HardwareConfirmationContent.WipeDevice to
        HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_WIPE_DEVICE,
      HardwareConfirmationContent.LostAppRecoverySignChallenge to
        HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_RECOVERY_SIGN_CHALLENGE,
      HardwareConfirmationContent.EekRestorationUnseal to
        HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_EEK_RESTORATION,
      HardwareConfirmationContent.CloudBackupRestoration to
        HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CLOUD_BACKUP_RESTORATION
    ).forEach { (content, expectedId) ->
      val model = HardwareConfirmationScreenModel(
        onBack = {},
        onConfirm = {},
        content = content
      )
      model.id.shouldBe(expectedId)
    }
  }

  test("each content variant sets the correct canceled screen id on HardwareConfirmationCanceledScreenModel") {
    mapOf(
      HardwareConfirmationContent.SignTransaction to
        HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_SIGN_TRANSACTION,
      HardwareConfirmationContent.SendTransaction to
        SendEventTrackerScreenId.SEND_HARDWARE_CONFIRMATION_CANCELED,
      HardwareConfirmationContent.ConsolidateUtxos to
        HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_CONSOLIDATE_UTXOS,
      HardwareConfirmationContent.FirmwareUpdate to
        HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_FWUP,
      HardwareConfirmationContent.SignActionProof to
        HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_SIGN_ACTION_PROOF,
      HardwareConfirmationContent.LostAppRecovery to
        HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_RECOVERY,
      HardwareConfirmationContent.WipeDevice to
        HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_WIPE_DEVICE,
      HardwareConfirmationContent.LostAppRecoverySignChallenge to
        HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_RECOVERY_SIGN_CHALLENGE,
      HardwareConfirmationContent.EekRestorationUnseal to
        HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_EEK_RESTORATION,
      HardwareConfirmationContent.CloudBackupRestoration to
        HardwareConfirmationEventTrackerScreenId.HW_CONFIRMATION_CANCELED_CLOUD_BACKUP_RESTORATION
    ).forEach { (content, expectedCanceledId) ->
      val model = HardwareConfirmationCanceledScreenModel(
        onBack = {},
        content = content
      )
      model.id.shouldBe(expectedCanceledId)
    }
  }
})
