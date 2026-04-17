package build.wallet.statemachine.account.create.full.onboard

import build.wallet.bitkey.keybox.PrivateAccountMock
import build.wallet.chaincode.delegation.ChaincodeExtractorFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.WsmVerifierMock
import build.wallet.f8e.onboarding.CompleteOnboardingResponseV2
import build.wallet.f8e.onboarding.OnboardingF8eClientMock
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec

class HardwareDescriptorDeliveryServiceImplTests : FunSpec({
  val onboardingF8eClient = OnboardingF8eClientMock(turbines::create)
  val chaincodeExtractor = ChaincodeExtractorFake()

  val service = HardwareDescriptorDeliveryServiceImpl(
    onboardingF8eClient = onboardingF8eClient,
    chaincodeExtractor = chaincodeExtractor,
    wsmVerifier = WsmVerifierMock(),
  )

  val account = PrivateAccountMock

  beforeTest {
    onboardingF8eClient.reset()
    chaincodeExtractor.reset()
  }

  test("prepares an NFC session using the server response for private wallets") {
    onboardingF8eClient.completeOnboardingV2Result =
      Ok(
        CompleteOnboardingResponseV2(
          appAuthPub = "cc",
          hardwareAuthPub = "dd",
          appSpendingPub = "aa",
          hardwareSpendingPub = "ee",
          serverSpendingPub = "bb",
          signature = "ff"
        )
      )

    val result = service.fetchSignatureAndPrepareNfcSession(account)

    onboardingF8eClient.completeOnboardingV2Calls.awaitItem()
    result.shouldBeOk()
  }
})
