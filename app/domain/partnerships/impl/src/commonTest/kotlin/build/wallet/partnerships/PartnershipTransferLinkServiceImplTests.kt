package build.wallet.partnerships

import bitkey.f8e.partnerships.TransferLinkRedirectF8eClientFake
import build.wallet.account.AccountServiceFake
import build.wallet.bitcoin.address.BitcoinAddressServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.f8e.partnerships.AppRestrictions
import build.wallet.f8e.partnerships.RedirectInfo
import build.wallet.f8e.partnerships.RedirectUrlType
import build.wallet.ktor.result.HttpError.ClientError
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.ktor.result.HttpError.ServerError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.http.*

class PartnershipTransferLinkServiceImplTests : FunSpec({
  val transferLinkF8eClient = TransferLinkRedirectF8eClientFake()
  val bitcoinAddressService = BitcoinAddressServiceFake()
  val accountService = AccountServiceFake()

  val service = PartnershipTransferLinkServiceImpl(
    transferLinkF8eClient = transferLinkF8eClient,
    bitcoinAddressService = bitcoinAddressService,
    accountService = accountService
  )

  val partnerInfo = PartnerInfo(
    logoUrl = null,
    logoBadgedUrl = null,
    name = "partner",
    partnerId = PartnerId("partner")
  )

  beforeTest {
    transferLinkF8eClient.reset()
    bitcoinAddressService.reset()
    accountService.reset()
  }

  test("getPartnerInfoForPartner returns partner info when partner found by name") {
    accountService.setActiveAccount(FullAccountMock)

    val result = service.getPartnerInfoForPartner("Partner 1")

    result.shouldBeOk()
    val partnerInfo = result.value
    partnerInfo.name.shouldBe("Partner 1")
    partnerInfo.partnerId.value.shouldBe("partner1")
  }

  test("getPartnerInfoForPartner returns partner info when partner found by ID") {
    accountService.setActiveAccount(FullAccountMock)

    val result = service.getPartnerInfoForPartner("partner2")

    result.shouldBeOk()
    val partnerInfo = result.value
    partnerInfo.name.shouldBe("Partner 2")
    partnerInfo.partnerId.value.shouldBe("partner2")
  }

  test("getPartnerInfoForPartner returns PartnerNotFound error when partner does not exist") {
    accountService.setActiveAccount(FullAccountMock)

    val result = service.getPartnerInfoForPartner("NonExistent")

    result.shouldBeErrOfType<GetPartnerInfoError.PartnerNotFound>()
  }

  test("getPartnerInfoForPartner returns NoFullAccountFound error when no account") {
    val result = service.getPartnerInfoForPartner("Partner 1")
    result.shouldBeErrOfType<GetPartnerInfoError.NoFullAccountFound>()
  }

  test("getPartnerInfoForPartner returns NetworkingError when f8e client fails") {
    accountService.setActiveAccount(FullAccountMock)

    val networkError = NetworkError(RuntimeException("Network error"))
    transferLinkF8eClient.getTransferPartnersResult = Err(networkError)

    val result = service.getPartnerInfoForPartner("Partner 1")

    result.shouldBeErrOfType<GetPartnerInfoError.NetworkingError>()
  }

  test("processTransferLink processes transfer link successfully with deeplink redirect") {
    accountService.setActiveAccount(FullAccountMock)

    val partnerInfo = PartnerInfo(
      logoUrl = "https://example.com/logo.png",
      logoBadgedUrl = "https://example.com/logo-badged.png",
      name = "partner",
      partnerId = PartnerId("partner")
    )

    val redirectInfo = RedirectInfo(
      appRestrictions = AppRestrictions(
        packageName = "com.partner.app",
        minVersion = 123456L
      ),
      url = "partner://transfer?token=abc123",
      redirectType = RedirectUrlType.DEEPLINK,
      partnerTransactionId = PartnershipTransactionId("partner-tx-123")
    )

    transferLinkF8eClient.getTransferLinkRedirectResult = Ok(redirectInfo)

    val result = service.processTransferLink(partnerInfo, "test-token-123")

    result.shouldBeOk()
    val redirectResult = result.value

    redirectResult.partnerName.shouldBe("partner")

    val redirectMethod = redirectResult.redirectMethod
    redirectMethod.shouldBeTypeOf<PartnerRedirectionMethod.Deeplink>()
    redirectMethod.urlString.shouldBe("partner://transfer?token=abc123")
    redirectMethod.partnerName.shouldBe("partner")
    redirectMethod.appRestrictions?.packageName.shouldBe("com.partner.app")
    redirectMethod.appRestrictions?.minVersion.shouldBe(123456L)
  }

  test("processTransferLink processes transfer link successfully with web widget redirect") {
    accountService.setActiveAccount(FullAccountMock)

    val partnerInfo = PartnerInfo(
      logoUrl = "https://example.com/logo.png",
      logoBadgedUrl = "https://example.com/logo-badged.png",
      name = "CashApp",
      partnerId = PartnerId("cashapp")
    )

    val redirectInfo = RedirectInfo(
      appRestrictions = null,
      url = "https://cash.app/widget?token=xyz789",
      redirectType = RedirectUrlType.WIDGET,
      partnerTransactionId = PartnershipTransactionId("cash-tx-456")
    )

    transferLinkF8eClient.getTransferLinkRedirectResult = Ok(redirectInfo)

    val result = service.processTransferLink(partnerInfo, "test-token-456")

    result.shouldBeOk()
    val redirectResult = result.value

    redirectResult.partnerName.shouldBe("CashApp")

    val redirectMethod = redirectResult.redirectMethod
    redirectMethod.shouldBeTypeOf<PartnerRedirectionMethod.Web>()
    redirectMethod.urlString.shouldBe("https://cash.app/widget?token=xyz789")
    redirectMethod.partnerInfo.name.shouldBe("CashApp")
    redirectMethod.partnerInfo.partnerId.value.shouldBe("cashapp")
    redirectMethod.partnerInfo.logoUrl.shouldBe(null)
    redirectMethod.partnerInfo.logoBadgedUrl.shouldBe(null)
  }

  test("processTransferLink returns NotRetryable error when account service fails to get account") {
    val result = service.processTransferLink(partnerInfo, "test-token")
    result.shouldBeErrOfType<ProcessTransferLinkError.NotRetryable>()
  }

  test("processTransferLink returns Retryable error when bitcoin address generation fails") {
    accountService.setActiveAccount(FullAccountMock)

    val addressError = Error("Failed to generate address")
    bitcoinAddressService.result = Err(addressError)

    val result = service.processTransferLink(partnerInfo, "test-token")
    result.shouldBeErrOfType<ProcessTransferLinkError.Retryable>()
  }

  test("processTransferLink returns Retryable error when f8e client call fails with NetworkError") {
    accountService.setActiveAccount(FullAccountMock)

    val networkError = NetworkError(RuntimeException("Network error"))
    transferLinkF8eClient.getTransferLinkRedirectResult = Err(networkError)

    val result = service.processTransferLink(partnerInfo, "test-token")
    result.shouldBeErrOfType<ProcessTransferLinkError.Retryable>()
  }

  test("processTransferLink returns Retryable error when f8e client call fails with ServerError") {
    accountService.setActiveAccount(FullAccountMock)

    val serverError = ServerError(HttpResponseMock(HttpStatusCode.InternalServerError))
    transferLinkF8eClient.getTransferLinkRedirectResult = Err(serverError)

    val result = service.processTransferLink(partnerInfo, "test-token")
    result.shouldBeErrOfType<ProcessTransferLinkError.Retryable>()
  }

  test("processTransferLink returns NotRetryable error when f8e client call fails with client error") {
    accountService.setActiveAccount(FullAccountMock)

    val clientError = ClientError(HttpResponseMock(HttpStatusCode.BadRequest))
    transferLinkF8eClient.getTransferLinkRedirectResult = Err(clientError)

    val result = service.processTransferLink(partnerInfo, "test-token")
    result.shouldBeErrOfType<ProcessTransferLinkError.NotRetryable>()
  }
})
