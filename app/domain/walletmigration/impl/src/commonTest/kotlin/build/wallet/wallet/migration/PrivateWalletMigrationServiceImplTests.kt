package build.wallet.wallet.migration

import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.onboarding.CreateAccountKeysetV2F8eClientFake
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.ktor.result.HttpError
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PrivateWalletMigrationServiceImplTests : FunSpec({
  val appKeysGenerator = AppKeysGeneratorMock()
  val createKeysetClient = CreateAccountKeysetV2F8eClientFake()
  val uuidGenerator = UuidGeneratorFake()

  val service = PrivateWalletMigrationServiceImpl(
    keyGenerator = appKeysGenerator,
    createKeysetClient = createKeysetClient,
    uuidGenerator = uuidGenerator
  )

  val mockAccount = FullAccountMock
  val mockProofOfPossession = HwFactorProofOfPossession("test-proof")
  val mockNewHwKeys = HwKeyBundleMock

  beforeTest {
    appKeysGenerator.reset()
    createKeysetClient.reset()
    uuidGenerator.reset()
  }

  test("initiateMigration successfully creates new keyset") {
    appKeysGenerator.keyBundleResult = Ok(AppKeyBundleMock)
    createKeysetClient.createKeysetResult = Ok(F8eSpendingKeysetMock)

    val result = service.initiateMigration(
      account = mockAccount,
      proofOfPossession = mockProofOfPossession,
      newHwKeys = mockNewHwKeys
    )

    val spendingKeyset = result.shouldBeOk()
    spendingKeyset.localId.shouldBe("uuid-0")
    spendingKeyset.networkType.shouldBe(mockAccount.keybox.config.bitcoinNetworkType)
    spendingKeyset.appKey.shouldBe(AppKeyBundleMock.spendingKey)
    spendingKeyset.hardwareKey.shouldBe(mockNewHwKeys.spendingKey)
    spendingKeyset.f8eSpendingKeyset.shouldBe(F8eSpendingKeysetMock)
  }

  test("initiateMigration fails when app key generation fails") {
    val keyGenerationError = RuntimeException("Key generation failed")
    appKeysGenerator.keyBundleResult = Err(keyGenerationError)

    val result = service.initiateMigration(
      account = mockAccount,
      proofOfPossession = mockProofOfPossession,
      newHwKeys = mockNewHwKeys
    )

    result.shouldBeErrOfType<PrivateWalletMigrationError.KeysetCreationFailed>()
    val error = result.error as PrivateWalletMigrationError.KeysetCreationFailed
    error.error.shouldBe(keyGenerationError)
  }

  test("initiateMigration fails when server keyset creation fails") {
    val networkError = HttpError.UnhandledException(RuntimeException("Network error"))
    appKeysGenerator.keyBundleResult = Ok(AppKeyBundleMock)
    createKeysetClient.createKeysetResult = Err(networkError)

    val result = service.initiateMigration(
      account = mockAccount,
      proofOfPossession = mockProofOfPossession,
      newHwKeys = mockNewHwKeys
    )

    result.shouldBeErrOfType<PrivateWalletMigrationError.KeysetCreationFailed>()
      .error
      .shouldBe(networkError)
  }
})
