package build.wallet.nfc.transaction

import bitkey.account.AccountConfigServiceFake
import bitkey.account.HardwareType
import build.wallet.account.analytics.AppInstallation
import build.wallet.account.analytics.AppInstallationDaoMock
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.SekGeneratorMock
import build.wallet.cloud.backup.csek.SsekDaoFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.firmware.EnrolledFingerprints.Companion.FIRST_FINGERPRINT_INDEX
import build.wallet.firmware.FingerprintEnrollmentStatus
import build.wallet.firmware.FingerprintHandle
import build.wallet.firmware.FirmwareDeviceInfoDaoFake
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.firmware.HardwareAttestationFake
import build.wallet.nfc.HardwareProvisionedAppKeyStatusDaoFake
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSessionFake
import build.wallet.platform.random.UuidGeneratorFake
import com.github.michaelbull.result.Ok
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
  val hardwareProvisionedAppKeyStatusDao = HardwareProvisionedAppKeyStatusDaoFake()
  val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoFake()

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
      accountConfigService = accountConfigService,
      fingerprintResetMinFirmwareVersionFeatureFlag = FingerprintResetMinFirmwareVersionFeatureFlag(
        FeatureFlagDaoFake()
      ),
      hardwareProvisionedAppKeyStatusDao = hardwareProvisionedAppKeyStatusDao,
      firmwareDeviceInfoDao = firmwareDeviceInfoDao
    )

  beforeTest {
    accountConfigService.reset()
    accountConfigService.setBitcoinNetworkType(BITCOIN)
    firmwareDeviceInfoDao.reset()
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
        appGlobalAuthPublicKey = PublicKey<AppGlobalAuthKey>("6170702D617574682D64707562"),
        onCancel = {},
        onSuccess = { onSuccessCalls.add(Unit) }
      )
    val activationResult =
      transaction
        .session(nfcSession, nfcCommands)
        .also { transaction.onSuccess(it) }
        .shouldBeTypeOf<PairingTransactionResponse.FingerprintEnrolled>()

    nfcCommands.getAuthenticationKeyCalls.awaitItem()
    nfcCommands.getDeviceInfoCalls.awaitItem().shouldBe(FirmwareDeviceInfoMock)

    nfcCommands.provisionAppAuthKeyCalls.awaitItem()
      .shouldBe(AppGlobalAuthPublicKeyMock.value.encodeUtf8())

    activationResult.keyBundle.shouldBe(
      HwKeyBundle(
        localId = "uuid-0",
        networkType = BITCOIN,
        spendingKey = HwSpendingPublicKey(DescriptorPublicKeyMock(identifier = "hardware-dpub-0")),
        authKey = HwAuthSecp256k1PublicKeyMock.copy(pubKey = Secp256k1PublicKey("hw-auth-dpub"))
      )
    )

    // W1: signature is obtained via signChallenge during pairing
    activationResult.appGlobalAuthKeyHwSignature.shouldNotBe(
      AppGlobalAuthKeyHwSignature(AppGlobalAuthKeyHwSignature.W3_ONBOARDING_PLACEHOLDER)
    )

    // Our mocks return a fixed value when wrapping key command is called.
    activationResult.sealedCsek.shouldBe("sealed-data".encodeUtf8())
    activationResult.sealedSsek.shouldBe("sealed-data".encodeUtf8())
    activationResult.serial.shouldBe("fakeS203serial")

    // Store hardware sealed CSEK and SSEK to app.
    csekDao.get(activationResult.sealedCsek).shouldBe(Ok(csek))
    ssekDao.get(activationResult.sealedSsek).shouldBe(Ok(csek))

    // Store hardware serial to app.
    appInstallationDao
      .appInstallation.shouldNotBeNull()
      .hardwareSerialNumber.shouldBe(activationResult.serial)

    firmwareDeviceInfoDao.storedDeviceInfo.shouldBe(FirmwareDeviceInfoMock)

    onSuccessCalls.shouldContainExactly(Unit)
  }

  test("W3 success uses placeholder signature") {
    val w3DeviceInfo = FirmwareDeviceInfoMock.copy(hwRevision = "w3a-dvt")
    nfcCommands.deviceInfoResult = w3DeviceInfo

    val onSuccessCalls = mutableListOf<Unit>()

    val transaction =
      provider(
        appGlobalAuthPublicKey = PublicKey<AppGlobalAuthKey>("6170702D617574682D64707562"),
        onCancel = {},
        onSuccess = { onSuccessCalls.add(Unit) }
      )
    val activationResult =
      transaction
        .session(nfcSession, nfcCommands)
        .also { transaction.onSuccess(it) }
        .shouldBeTypeOf<PairingTransactionResponse.FingerprintEnrolled>()

    nfcCommands.getAuthenticationKeyCalls.awaitItem()
    nfcCommands.getDeviceInfoCalls.awaitItem().shouldBe(w3DeviceInfo)

    // W3: placeholder signature, not obtained via signChallenge
    activationResult.appGlobalAuthKeyHwSignature.shouldBe(
      AppGlobalAuthKeyHwSignature(AppGlobalAuthKeyHwSignature.W3_ONBOARDING_PLACEHOLDER)
    )

    // W3: provisionAppAuthKey should NOT be called during pairing
    nfcCommands.provisionAppAuthKeyCalls.expectNoEvents()

    firmwareDeviceInfoDao.storedDeviceInfo.shouldBe(w3DeviceInfo)

    onSuccessCalls.shouldContainExactly(Unit)

    // Reset for other tests
    nfcCommands.deviceInfoResult = FirmwareDeviceInfoMock
  }

  test("NOT_IN_PROGRESS starts enrollment with default Fingerprint 1 label") {
    nfcCommands.setEnrollmentStatus(FingerprintEnrollmentStatus.NOT_IN_PROGRESS)

    val transaction = provider(
      appGlobalAuthPublicKey = AppGlobalAuthPublicKeyMock,
      onCancel = {},
      onSuccess = {}
    )

    transaction.session(nfcSession, nfcCommands)
      .shouldBeTypeOf<PairingTransactionResponse.FingerprintEnrollmentStarted>()

    nfcCommands.getDeviceInfoCalls.awaitItem().shouldBe(FirmwareDeviceInfoMock)

    nfcCommands.startFingerprintEnrollmentCalls.awaitItem().shouldBe(
      FingerprintHandle(
        index = FIRST_FINGERPRINT_INDEX,
        label = FingerprintHandle.defaultLabel(FIRST_FINGERPRINT_INDEX)
      )
    )
  }

  test("expectedHardwareType W3 fails fast when W1 device is tapped") {
    // W1 device info (default mock is W1)
    nfcCommands.deviceInfoResult = FirmwareDeviceInfoMock
    nfcCommands.setEnrollmentStatus(FingerprintEnrollmentStatus.COMPLETE)

    val transaction = provider(
      appGlobalAuthPublicKey = AppGlobalAuthPublicKeyMock,
      expectedHardwareType = HardwareType.W3,
      onCancel = {},
      onSuccess = {}
    )

    // Should throw WrongHardwareType immediately, before any other commands
    val exception = shouldThrow<NfcException.WrongHardwareType> {
      transaction.session(nfcSession, nfcCommands)
    }
    exception.expected.shouldBe(HardwareType.W3)
    exception.actual.shouldBe(HardwareType.W1)

    // getDeviceInfo is called for verification
    nfcCommands.getDeviceInfoCalls.awaitItem().shouldBe(FirmwareDeviceInfoMock)
  }

  test("expectedHardwareType W3 succeeds when W3 device is tapped") {
    val w3DeviceInfo = FirmwareDeviceInfoMock.copy(hwRevision = "w3a-core-evt")
    nfcCommands.deviceInfoResult = w3DeviceInfo
    nfcCommands.setEnrollmentStatus(FingerprintEnrollmentStatus.COMPLETE)

    val transaction = provider(
      appGlobalAuthPublicKey = PublicKey<AppGlobalAuthKey>("6170702D617574682D64707562"),
      expectedHardwareType = HardwareType.W3,
      onCancel = {},
      onSuccess = {}
    )

    val activationResult = transaction
      .session(nfcSession, nfcCommands)
      .shouldBeTypeOf<PairingTransactionResponse.FingerprintEnrolled>()

    // getDeviceInfo is called twice: once for verification, once for fingerprint status
    nfcCommands.getDeviceInfoCalls.awaitItem().shouldBe(w3DeviceInfo)
    nfcCommands.getAuthenticationKeyCalls.awaitItem()
    nfcCommands.getDeviceInfoCalls.awaitItem().shouldBe(w3DeviceInfo)

    activationResult.hardwareType.shouldBe(HardwareType.W3)

    // Reset for other tests
    nfcCommands.deviceInfoResult = FirmwareDeviceInfoMock
  }

  test("shouldLock is always false on the NfcTransaction") {
    // Locking is handled inside session() on FingerprintEnrolled, not via the interceptor.
    val transaction = provider(
      appGlobalAuthPublicKey = AppGlobalAuthPublicKeyMock,
      shouldLockHardware = true,
      onCancel = {},
      onSuccess = {}
    )
    transaction.shouldLock.shouldBe(false)
    transaction.showDeviceConfirmation.shouldBe(false)
  }

  test("shouldLockHardware locks W1 on successful enrollment") {
    nfcCommands.setEnrollmentStatus(FingerprintEnrollmentStatus.COMPLETE)

    val transaction = provider(
      appGlobalAuthPublicKey = PublicKey<AppGlobalAuthKey>("6170702D617574682D64707562"),
      shouldLockHardware = true,
      onCancel = {},
      onSuccess = {}
    )

    val result = transaction.session(nfcSession, nfcCommands)
      .shouldBeTypeOf<PairingTransactionResponse.FingerprintEnrolled>()

    nfcCommands.getAuthenticationKeyCalls.awaitItem()
    nfcCommands.getDeviceInfoCalls.awaitItem()
    nfcCommands.provisionAppAuthKeyCalls.awaitItem()
    // lockDevice is called inside session() for W1 — mock returns true
    result.hardwareType.shouldBe(HardwareType.W1)
  }

  test("W3 shows confirmation on successful enrollment") {
    val w3DeviceInfo = FirmwareDeviceInfoMock.copy(hwRevision = "w3a-dvt")
    nfcCommands.deviceInfoResult = w3DeviceInfo
    nfcCommands.setEnrollmentStatus(FingerprintEnrollmentStatus.COMPLETE)

    val transaction = provider(
      appGlobalAuthPublicKey = PublicKey<AppGlobalAuthKey>("6170702D617574682D64707562"),
      onCancel = {},
      onSuccess = {}
    )

    val result = transaction.session(nfcSession, nfcCommands)
      .shouldBeTypeOf<PairingTransactionResponse.FingerprintEnrolled>()

    nfcCommands.getAuthenticationKeyCalls.awaitItem()
    nfcCommands.getDeviceInfoCalls.awaitItem()
    // showConfirmationScreen is called inside session() for W3 — mock returns true
    result.hardwareType.shouldBe(HardwareType.W3)

    nfcCommands.deviceInfoResult = FirmwareDeviceInfoMock
  }

  test("incomplete enrollment does not lock or show confirmation") {
    nfcCommands.setEnrollmentStatus(FingerprintEnrollmentStatus.NOT_IN_PROGRESS)

    val transaction = provider(
      appGlobalAuthPublicKey = AppGlobalAuthPublicKeyMock,
      shouldLockHardware = true,
      onCancel = {},
      onSuccess = {}
    )

    transaction.session(nfcSession, nfcCommands)
      .shouldBeTypeOf<PairingTransactionResponse.FingerprintEnrollmentStarted>()

    nfcCommands.getDeviceInfoCalls.awaitItem()
    nfcCommands.startFingerprintEnrollmentCalls.awaitItem()
  }
})
