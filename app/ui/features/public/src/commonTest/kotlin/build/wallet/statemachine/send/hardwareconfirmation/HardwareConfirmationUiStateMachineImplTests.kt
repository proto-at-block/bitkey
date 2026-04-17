package build.wallet.statemachine.send.hardwareconfirmation

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.core.form.FormDesignSystemV2Model
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import build.wallet.ui.model.icon.IconBackgroundType.Circle
import build.wallet.ui.model.icon.IconBackgroundType.Circle.CircleColor.Foreground10
import build.wallet.ui.model.icon.IconBackgroundType.Circle.CircleColor.Secondary
import build.wallet.ui.model.icon.IconSize.Regular
import build.wallet.ui.model.icon.IconTint.Foreground
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class HardwareConfirmationUiStateMachineImplTests : FunSpec({

  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)

  val stateMachine = HardwareConfirmationUiStateMachineImpl()

  val onBackCalls = turbines.create<Unit>("on back calls")
  val onCancelCalls = turbines.create<Unit>("on cancel calls")
  val onConfirmCalls = turbines.create<Unit>("on confirm calls")

  val props =
    HardwareConfirmationUiProps(
      onBack = { onBackCalls.add(Unit) },
      onCancel = { onCancelCalls.add(Unit) },
      onConfirm = { onConfirmCalls.add(Unit) }
    )

  beforeTest {
    inAppBrowserNavigator.reset()
  }

  test("shows confirmation screen initially") {
    stateMachine.test(props) {
      awaitBody<HardwareConfirmationScreenModel> {
        onConfirm.shouldNotBeNull()
        onBack.shouldNotBeNull()
      }
    }
  }

  test("all hardware confirmation variants use unified confirmation copy") {
    listOf(
      HardwareConfirmationContent.SignTransaction,
      HardwareConfirmationContent.SendTransaction,
      HardwareConfirmationContent.ConsolidateUtxos,
      HardwareConfirmationContent.SignActionProof,
      HardwareConfirmationContent.FirmwareUpdate,
      HardwareConfirmationContent.LostAppRecovery,
      HardwareConfirmationContent.WipeDevice,
      HardwareConfirmationContent.LostAppRecoverySignChallenge,
      HardwareConfirmationContent.EekRestorationUnseal,
      HardwareConfirmationContent.CloudBackupRestoration,
    ).forEach { content ->
      stateMachine.test(props.copy(content = content)) {
        awaitBody<HardwareConfirmationScreenModel> {
          header.shouldNotBeNull().apply {
            headline.shouldBe(content.title)
            sublineModel.shouldNotBeNull().string.shouldBe(content.body)
          }
          primaryButton.shouldNotBeNull().text.shouldBe(content.confirmButtonText)
          secondaryButton.shouldNotBeNull().text.shouldBe(content.cancelButtonText)

          val designSystemV2Model = designSystemV2Model.shouldNotBeNull()
          designSystemV2Model.useDesignSystemV2ScreenLayout.shouldBe(true)
          designSystemV2Model.scrollable.shouldBe(false)
          designSystemV2Model.mainContentVerticalAlignment.shouldBe(
            FormDesignSystemV2Model.MainContentVerticalAlignment.CENTER
          )
          designSystemV2Model.header.shouldBeNull()
          designSystemV2Model.mainContentList.shouldNotBeNull()[0]
            .shouldBeInstanceOf<FormMainContentModel.HeaderBlock>()
            .header.apply {
              headline.shouldBe(content.title)
              sublineModel.shouldNotBeNull().string.shouldBe(content.body)
            }
          designSystemV2Model.primaryButton.shouldNotBeNull().text.shouldBe(content.confirmButtonText)
          designSystemV2Model.secondaryButton.shouldNotBeNull().text.shouldBe(content.cancelButtonText)
        }
      }
    }
  }

  test("confirmation screen keeps legacy toolbar styling while DSV2 uses standard accessories") {
    stateMachine.test(props.copy(content = HardwareConfirmationContent.SignTransaction)) {
      awaitBody<HardwareConfirmationScreenModel> {
        toolbar.shouldNotBeNull().leadingAccessory.shouldBeInstanceOf<IconAccessory>()
          .model.iconModel.apply {
            iconBackgroundType.shouldBe(Circle(circleSize = Regular, color = Secondary))
            iconTint.shouldBe(Foreground)
          }

        toolbar.shouldNotBeNull().trailingAccessory.shouldBeInstanceOf<IconAccessory>()
          .model.iconModel.apply {
            iconBackgroundType.shouldBe(Circle(circleSize = Regular, color = Secondary))
            iconTint.shouldBe(Foreground)
          }

        designSystemV2Model.shouldNotBeNull().toolbar.shouldNotBeNull().leadingAccessory.shouldBeInstanceOf<IconAccessory>()
          .model.iconModel.apply {
            iconBackgroundType.shouldBe(Circle(circleSize = Regular, color = Foreground10))
            iconTint.shouldBeNull()
          }

        designSystemV2Model.shouldNotBeNull().toolbar.shouldNotBeNull().trailingAccessory.shouldBeInstanceOf<IconAccessory>()
          .model.iconModel.apply {
            iconBackgroundType.shouldBe(Circle(circleSize = Regular, color = Foreground10))
            iconTint.shouldBeNull()
          }
      }
    }
  }

  test("transaction confirmation can open dedicated help content") {
    stateMachine.test(props.copy(content = HardwareConfirmationContent.SignTransaction)) {
      awaitBody<HardwareConfirmationScreenModel> {
        onHelpClick.shouldNotBeNull().invoke()
      }

      awaitBody<HardwareConfirmationHelpBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("How it works")
        mainContentList.first().shouldBeInstanceOf<FormMainContentModel.Explainer>().items.first().apply {
          title.shouldBe("CHECK THE ADDRESS")
          body.string.shouldBe("Compare the address shown on your Bitkey to the source where the recipient address was obtained, not to what’s shown in the Bitkey app.")
        }
      }
    }
  }

  test("closing transaction help returns to confirmation screen") {
    stateMachine.test(props.copy(content = HardwareConfirmationContent.SignTransaction)) {
      awaitBody<HardwareConfirmationScreenModel> {
        onHelpClick.shouldNotBeNull().invoke()
      }

      awaitBody<HardwareConfirmationHelpBodyModel> {
        onBack.shouldNotBeNull().invoke()
      }

      awaitBody<HardwareConfirmationScreenModel>()
    }
  }

  test("clicking Continue calls onConfirm") {
    stateMachine.test(props) {
      awaitBody<HardwareConfirmationScreenModel> {
        onConfirm()
      }

      onConfirmCalls.awaitItem()
    }
  }

  test("clicking back calls onBack directly") {
    stateMachine.test(props) {
      awaitBody<HardwareConfirmationScreenModel> {
        onBack()
      }

      onBackCalls.awaitItem()
    }
  }

  test("clicking Cancel shows cancellation screen") {
    stateMachine.test(props) {
      awaitBody<HardwareConfirmationScreenModel> {
        onCancel()
      }

      awaitBody<HardwareConfirmationCanceledScreenModel> {
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Done")
        }

        val designSystemV2Model = designSystemV2Model.shouldNotBeNull()
        designSystemV2Model.useDesignSystemV2ScreenLayout.shouldBe(true)
        designSystemV2Model.scrollable.shouldBe(false)
        designSystemV2Model.mainContentVerticalAlignment.shouldBe(
          FormDesignSystemV2Model.MainContentVerticalAlignment.CENTER
        )
        designSystemV2Model.header.shouldBeNull()
        designSystemV2Model.mainContentList.shouldNotBeNull().single()
          .shouldBeInstanceOf<FormMainContentModel.HeaderBlock>()
          .header.apply {
            headline.shouldBe(HardwareConfirmationContent.SignTransaction.canceledTitle)
            sublineModel.shouldNotBeNull().string.shouldBe(
              HardwareConfirmationContent.SignTransaction.canceledBody
            )
          }
      }
    }
  }

  test("clicking Done on cancellation screen calls onCancel") {
    stateMachine.test(props) {
      awaitBody<HardwareConfirmationScreenModel> {
        onCancel()
      }

      awaitBody<HardwareConfirmationCanceledScreenModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      onCancelCalls.awaitItem()
    }
  }

  test("full cancellation flow") {
    stateMachine.test(props) {
      // Start at confirmation
      awaitBody<HardwareConfirmationScreenModel> {
        onCancel()
      }

      // Show cancellation screen
      awaitBody<HardwareConfirmationCanceledScreenModel> {
        primaryButton.shouldNotBeNull().apply {
          text.shouldBe("Done")
          onClick()
        }
      }

      // Verify callback was invoked
      onCancelCalls.awaitItem()
    }
  }

  test("recipientAddress on content is passed through to confirmation screen model") {
    val address = BitcoinAddress("bc1q42lja79elem0anu8q8s3h2n687re9jax556pcc")
    val contentWithAddress = HardwareConfirmationContent.SendTransaction.copy(
      recipientAddress = address
    )
    stateMachine.test(props.copy(content = contentWithAddress)) {
      awaitBody<HardwareConfirmationScreenModel> {
        content.recipientAddress.shouldBe(address)
      }
    }
  }

  test("recipientAddress is null when not set on content") {
    stateMachine.test(props) {
      awaitBody<HardwareConfirmationScreenModel> {
        content.recipientAddress.shouldBeNull()
      }
    }
  }
})
