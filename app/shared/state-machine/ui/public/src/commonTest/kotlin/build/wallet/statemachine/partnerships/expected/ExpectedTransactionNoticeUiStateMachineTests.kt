package build.wallet.statemachine.partnerships.expected

import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.partnerships.GetTransferPartnerListService.Success
import build.wallet.f8e.partnerships.GetTransferPartnerListServiceMock
import build.wallet.ktor.result.HttpBodyError
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerInfo
import build.wallet.partnerships.PartnerRedirectionMethod
import build.wallet.partnerships.PartnershipEvent
import build.wallet.partnerships.PartnershipTransactionId
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.time.DateTimeFormatterMock
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

class ExpectedTransactionNoticeUiStateMachineTests : FunSpec({
  val getTransferPartnerListService = GetTransferPartnerListServiceMock(
    turbines::create
  )
  val dateTimeFormatter = DateTimeFormatterMock()
  val stateMachine = ExpectedTransactionNoticeUiStateMachineImpl(
    getTransferPartnerListService = getTransferPartnerListService,
    dateTimeFormatter = dateTimeFormatter
  )
  val onBack = turbines.create<Unit>("on back calls")
  val onViewCalls = turbines.create<PartnerRedirectionMethod>("view in partner app calls")
  val props = ExpectedTransactionNoticeProps(
    fullAccountId = FullAccountIdMock,
    f8eEnvironment = F8eEnvironment.Local,
    partner = PartnerId("test-partner-id"),
    receiveTime = LocalDateTime(2024, 4, 23, 12, 1),
    partnerTransactionId = PartnershipTransactionId("test-transaction-id"),
    onBack = { onBack.add(Unit) },
    onViewInPartnerApp = { method -> onViewCalls.add(method) },
    event = PartnershipEvent("test-event")
  )

  val successPartnershipsCall = Ok(
    Success(
      listOf(
        PartnerInfo("test-logo", "Test Partner", "test-partner-id")
      )
    )
  )

  test("Load Partner Details") {
    getTransferPartnerListService.partnersResult = successPartnershipsCall
    stateMachine.test(props) {
      awaitScreenWithBody<LoadingSuccessBodyModel>()
      getTransferPartnerListService.getTransferPartnersCall.awaitItem()
      awaitScreenWithBody<FormBodyModel> {
        header?.iconModel?.iconImage.shouldBeTypeOf<IconImage.UrlImage>().url.shouldBe("test-logo")
        header?.headline.shouldContain("Test Partner")
        primaryButton.shouldNotBeNull().onClick()
        onBack.awaitItem()
        secondaryButton.shouldBeNull()
      }
    }
  }

  test("Known Partner Deeplink") {
    getTransferPartnerListService.partnersResult = Ok(
      Success(
        listOf(
          PartnerInfo("test-logo", "Test Partner", "CashApp")
        )
      )
    )
    stateMachine.test(
      props.copy(
        partner = PartnerId("CashApp")
      )
    ) {
      awaitScreenWithBody<LoadingSuccessBodyModel>()
      getTransferPartnerListService.getTransferPartnersCall.awaitItem()
      awaitScreenWithBody<FormBodyModel> {
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
    getTransferPartnerListService.partnersResult = Ok(
      Success(
        listOf(
          PartnerInfo("test-logo", "Test Partner", "Coinbase")
        )
      )
    )
    stateMachine.test(
      props.copy(
        partner = PartnerId("Coinbase")
      )
    ) {
      awaitScreenWithBody<LoadingSuccessBodyModel>()
      getTransferPartnerListService.getTransferPartnersCall.awaitItem()
      awaitScreenWithBody<FormBodyModel> {
        secondaryButton.shouldNotBeNull().text.shouldContain("Test Partner")
        secondaryButton.shouldNotBeNull().onClick()
        onViewCalls.awaitItem().should {
          it.shouldBeTypeOf<PartnerRedirectionMethod.Web>()
            .urlString.shouldBe("https://coinbase.com")
        }
      }
    }
  }

  test("Unknown Partner Info") {
    getTransferPartnerListService.partnersResult = successPartnershipsCall
    stateMachine.test(
      props = props.copy(partner = PartnerId("unknown-partner-id"))
    ) {
      awaitScreenWithBody<LoadingSuccessBodyModel>()
      getTransferPartnerListService.getTransferPartnersCall.awaitItem()
      awaitScreenWithBody<FormBodyModel> {
        header?.iconModel?.iconImage.shouldBeTypeOf<IconImage.LocalImage>().icon.shouldBe(Icon.Bitcoin)
        header?.headline.shouldNotContain("Test Partner")
        secondaryButton.shouldBeNull()
      }
    }
  }

  test("Fail Partner list call") {
    getTransferPartnerListService.partnersResult = Err(HttpBodyError.UnhandledError(RuntimeException("test-error")))
    stateMachine.test(props) {
      awaitScreenWithBody<LoadingSuccessBodyModel>()
      getTransferPartnerListService.getTransferPartnersCall.awaitItem()
      awaitScreenWithBody<FormBodyModel> {
        header?.iconModel?.iconImage.shouldBeTypeOf<IconImage.LocalImage>().icon.shouldBe(Icon.Bitcoin)
        header?.headline.shouldNotContain("Test Partner")
        secondaryButton.shouldBeNull()
      }
    }
  }
})
