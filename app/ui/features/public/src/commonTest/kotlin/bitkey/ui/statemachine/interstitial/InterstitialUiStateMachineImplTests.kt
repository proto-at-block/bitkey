package bitkey.ui.statemachine.interstitial

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.inheritance.InheritanceUpsellServiceFake
import build.wallet.statemachine.core.test
import build.wallet.statemachine.inheritance.InheritanceUpsellBodyModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf

class InterstitialUiStateMachineImplTests : FunSpec({

  val inheritanceUpsellService = InheritanceUpsellServiceFake()

  fun stateMachine() =
    InterstitialUiStateMachineImpl(
      inheritanceUpsellService = inheritanceUpsellService
    )

  val props = InterstitialUiProps(
    account = FullAccountMock,
    isComingFromOnboarding = false
  )

  beforeTest {
    inheritanceUpsellService.reset()
  }

  test("default screen model is null") {
    stateMachine().test(props = props) {
      awaitItem().shouldBeNull()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("inheritance upsell is shown when applicable") {
    stateMachine().test(props = props) {
      // initial loading of the inheritance upsell service
      awaitItem()

      awaitItem().shouldNotBeNull()
        .body
        .shouldBeInstanceOf<InheritanceUpsellBodyModel>()
        .onClose()

      awaitItem().shouldBeNull()

      inheritanceUpsellService.shouldShowUpsell().shouldBeFalse()
    }
  }
})
