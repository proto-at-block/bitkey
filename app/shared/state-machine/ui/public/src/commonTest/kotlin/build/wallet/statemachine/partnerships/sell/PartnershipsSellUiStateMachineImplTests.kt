package build.wallet.statemachine.partnerships.sell

import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.SellBitcoinMaxAmountFeatureFlag
import build.wallet.feature.flags.SellBitcoinMinAmountFeatureFlag
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.ExchangeRateServiceFake
import build.wallet.partnerships.*
import build.wallet.platform.links.AppRestrictions
import build.wallet.platform.links.DeepLinkHandler
import build.wallet.platform.links.OpenDeeplinkResult
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.send.ContinueTransferParams
import build.wallet.statemachine.send.TransferAmountEntryUiProps
import build.wallet.statemachine.send.TransferAmountEntryUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Instant

class PartnershipsSellUiStateMachineImplTests : FunSpec({
  // turbines
  val onBack = turbines.create<Unit>("on back calls")
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val sellBitcoinMinAmountFeatureFlag = SellBitcoinMinAmountFeatureFlag(FeatureFlagDaoFake())
  val sellBitcoinMaxAmountFeatureFlag = SellBitcoinMaxAmountFeatureFlag(FeatureFlagDaoFake())
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
  val deepLinkCalls = turbines.create<String>("Deep Links")
  val deepLinkHandler = object : DeepLinkHandler {
    override fun openDeeplink(
      url: String,
      appRestrictions: AppRestrictions?,
    ): OpenDeeplinkResult {
      deepLinkCalls.add(url)

      return OpenDeeplinkResult.Opened(OpenDeeplinkResult.AppRestrictionResult.Success)
    }
  }
  // state machine
  val stateMachine =
    PartnershipsSellUiStateMachineImpl(
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
      partnershipsSellOptionsUiStateMachine = object : PartnershipsSellOptionsUiStateMachine,
        ScreenStateMachineMock<PartnershipsSellOptionsUiProps>(id = "partnerships sell options") {},
      partnershipsSellConfirmationUiStateMachine = object : PartnershipsSellConfirmationUiStateMachine,
        ScreenStateMachineMock<PartnershipsSellConfirmationProps>(id = "partnerships sell confirmation") {},
      transferAmountEntryUiStateMachine = object : TransferAmountEntryUiStateMachine,
        ScreenStateMachineMock<TransferAmountEntryUiProps>(
          "transfer-amount-entry"
        ) {},
      inAppBrowserNavigator = inAppBrowserNavigator,
      sellBitcoinMinAmountFeatureFlag = sellBitcoinMinAmountFeatureFlag,
      sellBitcoinMaxAmountFeatureFlag = sellBitcoinMaxAmountFeatureFlag,
      exchangeRateService = ExchangeRateServiceFake(),
      deepLinkHandler = deepLinkHandler
    )

  val props =
    PartnershipsSellUiProps(
      onBack = {
        onBack.add(Unit)
      },
      account = FullAccountMock,
      confirmedSale = null
    )

  val transaction = PartnershipTransaction(
    id = PartnershipTransactionId("test-id"),
    partnerInfo = PartnerInfo(
      partnerId = PartnerId("test-partner"),
      name = "test-partner-name",
      logoUrl = "test-partner-logo-url",
      logoBadgedUrl = "test-partner-logo-badged-url"
    ),
    context = "test-context",
    type = PartnershipTransactionType.PURCHASE,
    status = PartnershipTransactionStatus.PENDING,
    cryptoAmount = 1.23,
    txid = "test-transaction-hash",
    fiatAmount = 3.21,
    fiatCurrency = IsoCurrencyTextCode("USD"),
    paymentMethod = "test-payment-method",
    created = Instant.fromEpochMilliseconds(248),
    updated = Instant.fromEpochMilliseconds(842),
    sellWalletAddress = "test-sell-wallet-address",
    partnerTransactionUrl = "https://fake-partner.com/transaction/test-id"
  )

  // tests

  test("happy path") {
    stateMachine.testWithVirtualTime(props) {
      awaitBodyMock<TransferAmountEntryUiProps>(id = "transfer-amount-entry") {
        onContinueClick(
          ContinueTransferParams(
            BitcoinTransactionSendAmount.ExactAmount(
              BitcoinMoney.zero()
            )
          )
        )
      }

      awaitBodyMock<PartnershipsSellOptionsUiProps>(id = "partnerships sell options") {
        onPartnerRedirected(
          PartnerRedirectionMethod.Web(
            "",
            PartnerInfo(
              name = "partner",
              logoUrl = "https://logo.url.example.com",
              partnerId = PartnerId("partner"),
              logoBadgedUrl = "https://logo-badged.url.example.com"
            )
          ),
          transaction
        )
      }

      awaitBody<InAppBrowserModel>()
    }
  }
})
