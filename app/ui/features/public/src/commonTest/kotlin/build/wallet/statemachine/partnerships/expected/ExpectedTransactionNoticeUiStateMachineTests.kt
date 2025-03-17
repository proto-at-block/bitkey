package build.wallet.statemachine.partnerships.expected

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.ktor.result.HttpBodyError
import build.wallet.partnerships.*
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.transactions.FailedPartnerTransactionProps
import build.wallet.statemachine.transactions.FailedPartnerTransactionUiStateMachine
import build.wallet.statemachine.transactions.TransactionDetailsUiProps
import build.wallet.statemachine.transactions.TransactionDetailsUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.time.DateTimeFormatterMock
import build.wallet.time.MinimumLoadingDuration
import build.wallet.ui.model.icon.IconImage
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.datetime.LocalDateTime
import kotlin.time.Duration.Companion.milliseconds

class ExpectedTransactionNoticeUiStateMachineTests : FunSpec({
  val dateTimeFormatter = DateTimeFormatterMock()
  val partnershipTransactionsService = PartnershipTransactionsServiceMock(
    clearCalls = turbines.create("clear calls"),
    syncCalls = turbines.create("sync calls"),
    createCalls = turbines.create("create calls"),
    fetchMostRecentCalls = turbines.create("fetch most recent calls"),
    updateRecentTransactionStatusCalls = turbines.create("update recent transaction status calls"),
    getCalls = turbines.create("get transaction by id calls")
  )
  val stateMachine = ExpectedTransactionNoticeUiStateMachineImpl(
    dateTimeFormatter = dateTimeFormatter,
    partnershipTransactionsService = partnershipTransactionsService,
    minimumLoadingDuration = MinimumLoadingDuration(0.milliseconds),
    failedPartnerTransactionUiStateMachine = object : FailedPartnerTransactionUiStateMachine, ScreenStateMachineMock<FailedPartnerTransactionProps>("failed-partner-transaction") {},
    transactionDetailsUiStateMachine = object : TransactionDetailsUiStateMachine, ScreenStateMachineMock<TransactionDetailsUiProps>("transaction-details") {}
  )
  val onBack = turbines.create<Unit>("on back calls")
  val onViewCalls = turbines.create<PartnerRedirectionMethod>("view in partner app calls")
  val props = ExpectedTransactionNoticeProps(
    partner = PartnerId("test-partner-id"),
    receiveTime = LocalDateTime(2024, 4, 23, 12, 1),
    partnerTransactionId = PartnershipTransactionId("test-transaction-id"),
    onBack = { onBack.add(Unit) },
    onViewInPartnerApp = { method -> onViewCalls.add(method) },
    event = PartnershipEvent.TransactionCreated,
    account = FullAccountMock
  )
  val testPartner =
    PartnerInfo("test-logo", "test-logo-badged", "Test Partner", PartnerId("test-partner-id"))

  afterTest {
    partnershipTransactionsService.reset()
  }

  test("Load Partner Details") {
    partnershipTransactionsService.updateRecentTransactionStatusResponse = Ok(
      FakePartnershipTransaction.copy(
        partnerInfo = testPartner
      )
    )
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      partnershipTransactionsService.updateRecentTransactionStatusCalls.awaitItem()
      awaitBody<FormBodyModel> {
        header?.iconModel?.iconImage.shouldBeTypeOf<IconImage.UrlImage>().url.shouldBe("test-logo")
        header?.headline.shouldContain("Test Partner")
        primaryButton.shouldNotBeNull().onClick()
        onBack.awaitItem()
        secondaryButton.shouldBeNull()
      }
    }
  }

  test("Known Partner Deeplink") {
    partnershipTransactionsService.updateRecentTransactionStatusResponse = Ok(
      FakePartnershipTransaction.copy(
        partnerInfo = PartnerInfo(
          "test-logo",
          "test-logo-badged",
          "Test Partner",
          PartnerId("CashApp")
        )
      )
    )

    stateMachine.test(
      props.copy(
        partner = PartnerId("CashApp")
      )
    ) {
      awaitBody<LoadingSuccessBodyModel>()
      partnershipTransactionsService.updateRecentTransactionStatusCalls.awaitItem()
      awaitBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull().text.shouldContain("Test Partner")
        secondaryButton.shouldNotBeNull().onClick()
        onViewCalls.awaitItem().should {
          it.shouldBeTypeOf<PartnerRedirectionMethod.Deeplink>()
            .urlString.shouldBe("cashme://cash.app/launch/activity")
        }
      }
    }
  }

  test("Known Partner Weblink") {
    partnershipTransactionsService.updateRecentTransactionStatusResponse = Ok(
      FakePartnershipTransaction.copy(
        partnerInfo = PartnerInfo(
          "test-logo",
          "test-logo-badged",
          "Test Partner",
          PartnerId("Coinbase")
        )
      )
    )
    stateMachine.test(
      props.copy(
        partner = PartnerId("Coinbase")
      )
    ) {
      awaitBody<LoadingSuccessBodyModel>()
      partnershipTransactionsService.updateRecentTransactionStatusCalls.awaitItem()
      awaitBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull().text.shouldContain("Test Partner")
        secondaryButton.shouldNotBeNull().onClick()
        onViewCalls.awaitItem().should {
          it.shouldBeTypeOf<PartnerRedirectionMethod.Web>()
            .urlString.shouldBe("https://coinbase.com")
        }
      }
    }
  }

  test("Web Flow Completed w/ Pending transaction") {
    partnershipTransactionsService.fetchMostRecentResult = FakePartnershipTransaction.copy(
      status = PartnershipTransactionStatus.PENDING,
      partnerInfo = PartnerInfo(
        partnerId = PartnerId("Coinbase"),
        name = "Test Partner",
        logoUrl = null,
        logoBadgedUrl = null
      )
    ).let(::Ok)
    stateMachine.test(
      props.copy(event = PartnershipEvent.WebFlowCompleted)
    ) {
      awaitBody<LoadingSuccessBodyModel>()
      partnershipTransactionsService.fetchMostRecentCalls.awaitItem()
      awaitBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull().text.shouldContain("Test Partner")
        secondaryButton.shouldNotBeNull().onClick()
        onViewCalls.awaitItem().should {
          it.shouldBeTypeOf<PartnerRedirectionMethod.Web>()
            .urlString.shouldBe("https://coinbase.com")
        }
      }
    }
  }

  test("Web Flow Completed w/ Failed transaction") {
    partnershipTransactionsService.fetchMostRecentResult = FakePartnershipTransaction.copy(
      status = PartnershipTransactionStatus.FAILED,
      partnerInfo = PartnerInfo(
        partnerId = PartnerId("Coinbase"),
        name = "Test Partner",
        logoUrl = null,
        logoBadgedUrl = null
      )
    ).let(::Ok)
    stateMachine.test(
      props.copy(event = PartnershipEvent.WebFlowCompleted)
    ) {
      awaitBody<LoadingSuccessBodyModel>()
      partnershipTransactionsService.fetchMostRecentCalls.awaitItem()
      awaitBodyMock<FailedPartnerTransactionProps>()
    }
  }

  test("Web Flow Completed w/ Successful transaction") {
    partnershipTransactionsService.fetchMostRecentResult = FakePartnershipTransaction.copy(
      status = PartnershipTransactionStatus.SUCCESS,
      partnerInfo = PartnerInfo(
        partnerId = PartnerId("Coinbase"),
        name = "Test Partner",
        logoUrl = null,
        logoBadgedUrl = null
      )
    ).let(::Ok)
    stateMachine.test(
      props.copy(event = PartnershipEvent.WebFlowCompleted)
    ) {
      awaitBody<LoadingSuccessBodyModel>()
      partnershipTransactionsService.fetchMostRecentCalls.awaitItem()
      awaitBodyMock<TransactionDetailsUiProps>()
    }
  }

  test("Web Flow Completed w/ no status exits immediately") {
    partnershipTransactionsService.fetchMostRecentResult = FakePartnershipTransaction.copy(
      status = null,
      partnerInfo = PartnerInfo(
        partnerId = PartnerId("Coinbase"),
        name = "Test Partner",
        logoUrl = null,
        logoBadgedUrl = null
      )
    ).let(::Ok)
    stateMachine.test(
      props.copy(event = PartnershipEvent.WebFlowCompleted)
    ) {
      awaitBody<LoadingSuccessBodyModel>()
      partnershipTransactionsService.fetchMostRecentCalls.awaitItem()
      onBack.awaitItem()
    }
  }

  test("No Transaction Found") {
    partnershipTransactionsService.fetchMostRecentResult = Ok(null)
    stateMachine.test(
      props.copy(event = PartnershipEvent.WebFlowCompleted)
    ) {
      awaitBody<LoadingSuccessBodyModel>()
      partnershipTransactionsService.fetchMostRecentCalls.awaitItem()
      onBack.awaitItem()
    }
  }

  test("Transaction Fetch Error") {
    partnershipTransactionsService.fetchMostRecentResult = Err(Error())
    stateMachine.test(
      props.copy(event = PartnershipEvent.WebFlowCompleted)
    ) {
      awaitBody<LoadingSuccessBodyModel>()
      partnershipTransactionsService.fetchMostRecentCalls.awaitItem()
      onBack.awaitItem()
    }
  }

  test("Unknown Event") {
    stateMachine.test(
      props.copy(event = PartnershipEvent("unknown-event"))
    ) {
      awaitBody<LoadingSuccessBodyModel>()
      onBack.awaitItem()
    }
  }

  test("Fail Partner call") {
    partnershipTransactionsService.updateRecentTransactionStatusResponse =
      Err(HttpBodyError.UnhandledError(RuntimeException("test-error")))
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
      partnershipTransactionsService.updateRecentTransactionStatusCalls.awaitItem()
      awaitBody<FormBodyModel> {
        header?.iconModel?.iconImage.shouldBeTypeOf<IconImage.LocalImage>().icon.shouldBe(Icon.Bitcoin)
        header?.headline.shouldNotContain("Test Partner")
        secondaryButton.shouldBeNull()
      }
    }
  }
})
