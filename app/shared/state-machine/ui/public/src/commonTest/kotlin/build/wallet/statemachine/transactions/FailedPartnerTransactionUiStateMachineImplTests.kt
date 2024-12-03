package build.wallet.statemachine.transactions

import app.cash.turbine.plusAssign
import build.wallet.activity.Transaction
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryFake
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.partnerships.PartnershipTransactionFake
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.test
import build.wallet.ui.model.icon.IconImage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class FailedPartnerTransactionUiStateMachineImplTests : FunSpec({
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryFake()
  val currencyConverter = CurrencyConverterFake()
  val moneyDisplayFormatter = MoneyDisplayFormatterFake
  val onCloseCalls = turbines.create<Unit>("onClose calls")

  val stateMachine = FailedPartnerTransactionUiStateMachineImpl(
    inAppBrowserNavigator = inAppBrowserNavigator,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
    currencyConverter = currencyConverter,
    moneyDisplayFormatter = moneyDisplayFormatter
  )

  val partnershipTransaction = Transaction.PartnershipTransaction(
    details = PartnershipTransactionFake,
    bitcoinTransaction = null
  )

  val props = FailedPartnerTransactionProps(
    transaction = partnershipTransaction,
    onClose = {
      onCloseCalls += Unit
    }
  )

  test("failed partner transaction with no partner transaction url") {
    val props = props.copy(transaction = partnershipTransaction.copy(details = partnershipTransaction.details.copy(partnerTransactionUrl = null)))
    stateMachine.test(props) {
      awaitScreenWithBody<FailedPartnerTransactionBodyModel> {
        headerIcon.iconImage.shouldBeTypeOf<IconImage.UrlImage>()
        headline.shouldBe("There was an issue with your ${props.transaction.details.partnerInfo.name} transaction")
        subline.shouldBe("Visit ${props.transaction.details.partnerInfo.name} for more information.")
        buttonModel.shouldBeNull()
        onClose()
      }

      // after currency conversion
      awaitItem()

      onCloseCalls.awaitItem()
    }
  }

  test("failed partner transaction with partner transaction url") {
    stateMachine.test(props) {
      awaitScreenWithBody<FailedPartnerTransactionBodyModel> {
        headerIcon.iconImage.shouldBeTypeOf<IconImage.UrlImage>()
        headline.shouldBe("There was an issue with your ${props.transaction.details.partnerInfo.name} transaction")
        subline.shouldBe("Visit ${props.transaction.details.partnerInfo.name} for more information.")
        buttonModel.shouldNotBeNull()
          .text
          .shouldBe("Go to ${props.transaction.details.partnerInfo.name}")
        buttonModel.shouldNotBeNull().onClick()
      }

      // after currency conversion
      awaitItem()

      inAppBrowserNavigator.onOpenCalls.awaitItem().shouldBe(props.transaction.details.partnerTransactionUrl)
    }
  }
})
