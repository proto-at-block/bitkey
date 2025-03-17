package build.wallet.statemachine.trustedcontact

import build.wallet.bitkey.promotions.PromotionCode
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.clipboard.ClipItem
import build.wallet.platform.clipboard.ClipboardMock
import build.wallet.platform.links.AppRestrictions
import build.wallet.platform.links.DeepLinkHandler
import build.wallet.platform.links.OpenDeeplinkResult
import build.wallet.platform.sharing.SharingManagerMock
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.ui.model.callout.CalloutModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class PromoCodeUpsellUiStateMachineTests : FunSpec({

  val openDeepLinkCalls = turbines.create<Unit>("Opened Deep Link")
  val deepLinkHandler = object : DeepLinkHandler {
    override fun openDeeplink(
      url: String,
      appRestrictions: AppRestrictions?,
    ): OpenDeeplinkResult {
      openDeepLinkCalls.add(Unit)
      return OpenDeeplinkResult.Opened(OpenDeeplinkResult.AppRestrictionResult.Success)
    }
  }

  val promoCode = PromotionCode("fake-promo-code")
  val sharingManager = SharingManagerMock(turbines::create)
  val clipboard = ClipboardMock(
    plainTextItemToReturn = ClipItem.PlainText(promoCode.value)
  )
  val stateMachine = PromoCodeUpsellUiStateMachineImpl(
    deepLinkHandler = deepLinkHandler,
    clipboard = clipboard,
    sharingManager = sharingManager
  )

  val onExitCalls = turbines.create<Unit>("Exit Remove Relationship State Machine")
  val props = PromoCodeUpsellUiProps(
    promoCode = promoCode,
    onExit = { onExitCalls.add(Unit) }
  )

  test("model functions") {
    stateMachine.testWithVirtualTime(props) {
      awaitSheet<PromoCodeUpsellBodyModel> {
        mainContentList[0].shouldBeTypeOf<CalloutModel>().apply {
          title.shouldBe("fake-promo-code")
        }

        onClick()
        openDeepLinkCalls.awaitItem()

        onShare()
        sharingManager.sharedTextCalls.awaitItem().shouldBe(
          SharingManagerMock.SharedText(
            text = promoCode.value,
            title = "Bitkey Promo Code"
          )
        )

        onCopyCode()
        clipboard.copiedItems.awaitItem().shouldBe(
          ClipItem.PlainText(promoCode.value)
        )

        this.toolbar?.leadingAccessory.shouldNotBeNull().apply {
          onClick()
          onExitCalls.awaitItem()
        }
      }
    }
  }
})
