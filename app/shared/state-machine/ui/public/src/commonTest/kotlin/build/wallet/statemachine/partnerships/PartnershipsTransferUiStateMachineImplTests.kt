package build.wallet.statemachine.partnerships

import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.partnerships.GetTransferPartnerListServiceMock
import build.wallet.f8e.partnerships.GetTransferRedirectServiceMock
import build.wallet.keybox.wallet.AppSpendingWalletProviderMock
import build.wallet.statemachine.core.awaitSheetWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.Loader
import build.wallet.statemachine.core.test
import build.wallet.statemachine.partnerships.transfer.PartnershipsTransferUiProps
import build.wallet.statemachine.partnerships.transfer.PartnershipsTransferUiStateMachineImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class PartnershipsTransferUiStateMachineImplTests : FunSpec({
  // turbines
  val onBack = turbines.create<Unit>("on back calls")
  val onAnotherWalletOrExchange = turbines.create<Unit>("on another wallet or exchange calls")
  val onExitCalls = turbines.create<Unit>("on exit calls")
  val onPartnerRedirectedCalls =
    turbines.create<PartnerRedirectionMethod>(
      "on partner redirected calls"
    )
  val getTransferPartnerListService = GetTransferPartnerListServiceMock(turbines::create)
  val getTransferRedirectService = GetTransferRedirectServiceMock(turbines::create)
  val appSpendingWalletProviderMock =
    AppSpendingWalletProviderMock(SpendingWalletMock(turbines::create))

  // state machine
  val stateMachine =
    PartnershipsTransferUiStateMachineImpl(
      getTransferPartnerListService = getTransferPartnerListService,
      getTransferRedirectService = getTransferRedirectService,
      appSpendingWalletProvider = appSpendingWalletProviderMock
    )

  fun props() =
    PartnershipsTransferUiProps(
      account = FullAccountMock,
      onBack = {
        onBack.add(Unit)
      },
      onAnotherWalletOrExchange = {
        onAnotherWalletOrExchange.add(Unit)
      },
      onPartnerRedirected = {
        onPartnerRedirectedCalls.add(it)
      },
      onExit = {
        onExitCalls.add(Unit)
      }
    )

  // tests

  test("redirect partner") {
    stateMachine.test(props()) {
      getTransferPartnerListService.getTransferPartnersCall.awaitItem()

      awaitSheetWithBody<FormBodyModel> {
        mainContentList[0].shouldBeTypeOf<Loader>()
      }

      awaitSheetWithBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<FormMainContentModel.ListGroup>()) {
          listGroupModel.items.count().shouldBe(3)
          listGroupModel.items[0].title.shouldBe("Partner 1")
          listGroupModel.items[1].title.shouldBe("Partner 2")
          listGroupModel.items[2].title.shouldBe("Another exchange or wallet")

          listGroupModel.items[1].onClick.shouldNotBeNull().invoke()

          getTransferRedirectService.getTransferPartnersRedirectCall.awaitItem()
          awaitSheetWithBody<FormBodyModel> {
            mainContentList[0].shouldBeTypeOf<Loader>()
          }

          awaitSheetWithBody<FormBodyModel> {
            mainContentList[0].shouldBeTypeOf<Loader>()
            onPartnerRedirectedCalls.awaitItem().shouldBe(
              PartnerRedirectionMethod.Web(
                "http://example.com/redirect_url"
              )
            )
          }
        }
      }
    }
  }

  test("another exchange or wallet clicked") {
    stateMachine.test(props()) {
      getTransferPartnerListService.getTransferPartnersCall.awaitItem()
      awaitSheetWithBody<FormBodyModel>()

      awaitSheetWithBody<FormBodyModel> {
        with(mainContentList[0].shouldBeTypeOf<FormMainContentModel.ListGroup>()) {
          listGroupModel.items[2].title.shouldBe("Another exchange or wallet")
          listGroupModel.items[2].onClick.shouldNotBeNull().invoke()

          onAnotherWalletOrExchange.awaitItem()
        }
      }
    }
  }
})
