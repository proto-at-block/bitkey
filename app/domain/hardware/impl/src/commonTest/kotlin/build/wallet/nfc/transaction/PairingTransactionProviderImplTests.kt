package build.wallet.nfc.transaction

import bitkey.account.AccountConfigServiceFake
import build.wallet.account.analytics.AppInstallation
import build.wallet.account.analytics.AppInstallationDaoMock
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.SekGeneratorMock
import build.wallet.cloud.backup.csek.SsekDaoFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.firmware.HardwareAttestationFake
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.platform.random.UuidGeneratorFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import okio.ByteString.Companion.encodeUtf8

class PairingTransactionProviderImplTests : FunSpec({
  val nfcSession = NfcSessionFake()
  val nfcCommands = NfcCommandsMock(turbine = turbines::create)
  val sekGenerator = SekGeneratorMock()
  val csek = sekGenerator.csek
  val csekDao = CsekDaoFake()
  val ssekDao = SsekDaoFake()
  val uuid = UuidGeneratorFake()
  val appInstallationDao = AppInstallationDaoMock()
  val hardwareAttestation = HardwareAttestationFake()
  val accountConfigService = AccountConfigServiceFake()

  appInstallationDao.appInstallation =
    AppInstallation(localId = "foo", hardwareSerialNumber = null)

  val provider =
    PairingTransactionProviderImpl(
      sekGenerator = sekGenerator,
      csekDao = csekDao,
      ssekDao = ssekDao,
      uuidGenerator = uuid,
      appInstallationDao = appInstallationDao,
      hardwareAttestation = hardwareAttestation,
      accountConfigService = accountConfigService
    )

  beforeTest {
    accountConfigService.reset()
    accountConfigService.setBitcoinNetworkType(BITCOIN)
  }

  test("cancel") {
    val onCancelCalls = mutableListOf<Unit>()

    provider(
      appGlobalAuthPublicKey = AppGlobalAuthPublicKeyMock,
      onCancel = { onCancelCalls.add(Unit) },
      onSuccess = {}
    ).onCancel()

    onCancelCalls.shouldContainExactly(Unit)
  }

  test("success") {
    val onSuccessCalls = mutableListOf<Unit>()

    val transaction =
      provider(
        appGlobalAuthPublicKey = AppGlobalAuthPublicKeyMock,
        onCancel = {},
        onSuccess = { onSuccessCalls.add(Unit) }
      )
    val activationResult =
      transaction
        .session(nfcSession, nfcCommands)
        .also { transaction.onSuccess(it) }
        .shouldBeTypeOf<PairingTransactionResponse.FingerprintEnrolled>()

    activationResult.keyBundle.shouldBe(
      HwKeyBundle(
        localId = "uuid-0",
        networkType = BITCOIN,
        spendingKey = HwSpendingPublicKey(DescriptorPublicKeyMock(identifier = "hardware-dpub-0")),
        authKey = HwAuthSecp256k1PublicKeyMock.copy(pubKey = Secp256k1PublicKey("hw-auth-dpub"))
      )
    )

    // Our mocks return a fixed value when wrapping key command is called.
    activationResult.sealedCsek.shouldBe("sealed-data".encodeUtf8())
    activationResult.sealedSsek.shouldBe("sealed-data".encodeUtf8())
    activationResult.serial.shouldBe("serial")

    // Store hardware sealed CSEK and SSEK to app.
    csekDao.get(activationResult.sealedCsek).shouldBe(Ok(csek))
    ssekDao.get(activationResult.sealedSsek).shouldBe(Ok(csek))

    // Store hardware serial to app.
    appInstallationDao
      .appInstallation.shouldNotBeNull()
      .hardwareSerialNumber.shouldBe(activationResult.serial)

    onSuccessCalls.shouldContainExactly(Unit)
  }
})
