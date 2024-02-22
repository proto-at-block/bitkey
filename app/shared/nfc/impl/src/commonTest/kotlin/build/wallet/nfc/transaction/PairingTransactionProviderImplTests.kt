package build.wallet.nfc.transaction

import build.wallet.account.analytics.AppInstallation
import build.wallet.account.analytics.AppInstallationDaoMock
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.CsekGeneratorMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.platform.random.UuidFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import okio.ByteString.Companion.encodeUtf8

class PairingTransactionProviderImplTests : FunSpec({
  val nfcSession = NfcSessionFake()
  val nfcCommands = NfcCommandsMock(turbines::create)
  val csekGenerator = CsekGeneratorMock()
  val csek = csekGenerator.csek
  val csekDao = CsekDaoFake()
  val uuid = UuidFake()
  val appInstallationDao = AppInstallationDaoMock()
  appInstallationDao.appInstallation =
    AppInstallation(localId = "foo", hardwareSerialNumber = null)

  val networkType = BitcoinNetworkType.BITCOIN

  val provider =
    PairingTransactionProviderImpl(
      csekGenerator = csekGenerator,
      csekDao = csekDao,
      uuid = uuid,
      appInstallationDao = appInstallationDao
    )

  test("cancel") {
    val onCancelCalls = mutableListOf<Unit>()

    provider(
      networkType = networkType,
      onCancel = { onCancelCalls.add(Unit) },
      onSuccess = {},
      isHardwareFake = true
    ).onCancel()

    onCancelCalls.shouldContainExactly(Unit)
  }

  test("success") {
    val onSuccessCalls = mutableListOf<Unit>()

    val transaction =
      provider(
        networkType = networkType,
        onCancel = {},
        onSuccess = { onSuccessCalls.add(Unit) },
        isHardwareFake = true
      )
    val activationResult =
      transaction
        .session(nfcSession, nfcCommands)
        .also { transaction.onSuccess(it) }
        .shouldBeTypeOf<PairingTransactionResponse.FingerprintEnrolled>()

    activationResult.keyBundle.shouldBe(
      HwKeyBundle(
        localId = "uuid-0",
        networkType = BitcoinNetworkType.BITCOIN,
        spendingKey = HwSpendingPublicKey(DescriptorPublicKeyMock(identifier = "hardware-dpub-0")),
        authKey = HwAuthSecp256k1PublicKeyMock.copy(pubKey = Secp256k1PublicKey("hw-auth-dpub"))
      )
    )
    activationResult.sealedCsek.shouldBe("sealed-key".encodeUtf8())
    activationResult.serial.shouldBe("serial")

    // Store hardware sealed CSEK to app.
    csekDao.get(activationResult.sealedCsek).shouldBe(Ok(csek))

    // Store hardware serial to app.
    appInstallationDao
      .appInstallation.shouldNotBeNull()
      .hardwareSerialNumber.shouldBe(activationResult.serial)

    onSuccessCalls.shouldContainExactly(Unit)
  }
})
