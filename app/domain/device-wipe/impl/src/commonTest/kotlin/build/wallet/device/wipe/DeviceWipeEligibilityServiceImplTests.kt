package build.wallet.device.wipe

import bitkey.account.HardwareType.W1
import bitkey.account.HardwareType.W3
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.BlockTime
import build.wallet.bdk.bindings.BdkError.Generic
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitcoin.blockchain.BitcoinBlockchainMock
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransactionFake
import build.wallet.bitcoin.transactions.BitcoinTransactionSend
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.transactions.TransactionsDataMock
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.FullAccountW3Mock
import build.wallet.bitkey.spending.PrivateSpendingKeysetMock
import build.wallet.compose.collections.immutableListOf
import build.wallet.db.DbError
import build.wallet.db.DbQueryError
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.WipeOldW1DeviceFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.firmware.FirmwareDeviceInfoDaoFake
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.money.BitcoinMoney
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSession
import build.wallet.nfc.NfcSessionFake
import build.wallet.recovery.sweep.Sweep
import build.wallet.recovery.sweep.SweepContext
import build.wallet.recovery.sweep.SweepPsbt
import build.wallet.recovery.sweep.SweepService.SweepError.SweepGenerationFailed
import build.wallet.recovery.sweep.SweepService.SweepError.NoFundsToSweep
import build.wallet.recovery.sweep.SweepServiceMock
import build.wallet.recovery.sweep.SweepSignaturePlan
import build.wallet.testing.shouldBeOk
import build.wallet.time.someInstant
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import app.cash.turbine.Turbine

class DeviceWipeEligibilityServiceImplTests : FunSpec({
  val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoFake()
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val bitcoinBlockchain = BitcoinBlockchainMock(turbine = { name -> Turbine(name = name) })
  val sweepService = SweepServiceMock()
  val w3UpgradeDeviceHistoryRepository = W3UpgradeDeviceHistoryRepositoryFake()
  val wipeOldW1DeviceFeatureFlag = WipeOldW1DeviceFeatureFlag(FeatureFlagDaoFake())

  val service = DeviceWipeEligibilityServiceImpl(
    firmwareDeviceInfoDao = firmwareDeviceInfoDao,
    bitcoinWalletService = bitcoinWalletService,
    bitcoinBlockchain = bitcoinBlockchain,
    sweepService = sweepService,
    w3UpgradeDeviceHistoryRepository = w3UpgradeDeviceHistoryRepository,
    wipeOldW1DeviceFeatureFlag = wipeOldW1DeviceFeatureFlag
  )

  val spendingWallet = SpendingWalletMock(turbine = { name -> Turbine(name = name) })
  val zeroBalance = BitcoinBalanceFake(confirmed = BitcoinMoney.zero())
  val noPendingTransactionsData = TransactionsDataMock.copy(
    balance = zeroBalance,
    transactions = immutableListOf(BitcoinTransactionSend)
  )

  beforeTest {
    firmwareDeviceInfoDao.reset()
    bitcoinWalletService.reset()
    bitcoinBlockchain.reset()
    sweepService.reset()
    w3UpgradeDeviceHistoryRepository.reset()
    wipeOldW1DeviceFeatureFlag.setFlagValue(true)
    bitcoinWalletService.spendingWallet.value = spendingWallet
    bitcoinWalletService.transactionsData.value = noPendingTransactionsData
  }

  test("currently paired W1 auth-key match allows paired wipe when balance gates pass") {
    service.evaluateLoggedInDevice(
      account = FullAccountMock,
      tappedDevice = tappedW1Device(authKey = FullAccountMock.keybox.activeHwKeyBundle.authKey)
    ).shouldBeOk(DeviceWipeEligibility.ActiveReady)
  }

  test("currently paired W3 serial match allows paired wipe when balance gates pass") {
    firmwareDeviceInfoDao.setDeviceInfo(
      FirmwareDeviceInfoMock.copy(serial = "current-w3", hwRevision = "w3a-core-evt")
    )

    service.evaluateLoggedInDevice(
      account = FullAccountW3Mock,
      tappedDevice = tappedW3Device(serial = "current-w3")
    ).shouldBeOk(DeviceWipeEligibility.ActiveReady)
  }

  test("paired active pending or spendable funds returns paired device has funds") {
    val balance = BitcoinBalanceFake(
      untrustedPending = BitcoinMoney.sats(1_000),
      confirmed = BitcoinMoney.zero()
    )
    bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(balance = balance)

    service.evaluateLoggedInDevice(
      account = FullAccountMock,
      tappedDevice = tappedW1Device(authKey = FullAccountMock.keybox.activeHwKeyBundle.authKey)
    ).shouldBeOk(DeviceWipeEligibility.ActiveHasFunds(balance))
  }

  test("paired balance check failure returns paired device balance check failed") {
    bitcoinWalletService.transactionsData.value = null

    service.evaluateLoggedInDevice(
      account = FullAccountMock,
      tappedDevice = tappedW1Device(authKey = FullAccountMock.keybox.activeHwKeyBundle.authKey)
    ).shouldBe(Err(DeviceWipeEligibilityError.PairedDeviceBalanceCheckFailed))
  }

  test("paired spendability failure returns paired device balance check failed") {
    bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
      balance = BitcoinBalanceFake(confirmed = BitcoinMoney.sats(1_000)),
      transactions = immutableListOf(BitcoinTransactionSend)
    )
    bitcoinWalletService.spendingWallet.value = object : SpendingWallet by spendingWallet {
      override suspend fun isBalanceSpendable(): Result<Boolean, Error> = Err(Error("failed"))
    }

    service.evaluateLoggedInDevice(
      account = FullAccountMock,
      tappedDevice = tappedW1Device(authKey = FullAccountMock.keybox.activeHwKeyBundle.authKey)
    ).shouldBe(Err(DeviceWipeEligibilityError.PairedDeviceBalanceCheckFailed))
  }

  test("unknown unpaired device returns unknown device") {
    service.evaluateLoggedInDevice(
      account = FullAccountMock,
      tappedDevice = tappedW1Device(serial = "unknown-serial")
    ).shouldBe(Err(DeviceWipeEligibilityError.UnknownDevice))
  }

  test("old W1 feature flag disabled returns unknown device") {
    wipeOldW1DeviceFeatureFlag.setFlagValue(false)

    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint(),
      tappedDevice = tappedW1Device(serial = "old-serial")
    ).shouldBe(Err(DeviceWipeEligibilityError.UnknownDevice))
  }

  test("old W1 feature flag disabled blocks final validation") {
    wipeOldW1DeviceFeatureFlag.setFlagValue(false)

    service.validateInactiveDeviceForWipe(
      account = FullAccountW3Mock,
      session = NfcSessionFake(),
      commands = nfcCommands(),
      expectedDevice = oldW1Device(),
      bitcoinNetworkType = null
    ).shouldBe(Err(InactiveDeviceWipeValidationError.FeatureDisabled))
  }

  test("final inactive validation allows fingerprint match") {
    service.validateInactiveDeviceForWipe(
      account = w3AccountWithInactiveFingerprint("e5ff120e"),
      session = NfcSessionFake(),
      commands = nfcCommands(serial = "old-serial"),
      expectedDevice = oldW1Device(hardwareFingerprint = "e5ff120e"),
      bitcoinNetworkType = SIGNET
    ).shouldBeOk(Unit)
  }

  test("final inactive validation allows non-W3 inactive device") {
    service.validateInactiveDeviceForWipe(
      account = w1AccountWithInactiveFingerprint("e5ff120e"),
      session = NfcSessionFake(),
      commands = nfcCommands(serial = "old-serial"),
      expectedDevice = oldW1Device(hardwareFingerprint = "e5ff120e"),
      bitcoinNetworkType = SIGNET
    ).shouldBeOk(Unit)
  }

  test("final inactive validation rejects fingerprint mismatch") {
    service.validateInactiveDeviceForWipe(
      account = w3AccountWithInactiveFingerprint("e5ff120e"),
      session = NfcSessionFake(),
      commands = nfcCommands(serial = "other-serial"),
      expectedDevice = oldW1Device(hardwareFingerprint = "other-fingerprint"),
      bitcoinNetworkType = SIGNET
    ).shouldBe(Err(InactiveDeviceWipeValidationError.WrongDevice))
  }

  test("final old-W1 validation allows fingerprint match when serial is unavailable") {
    service.validateInactiveDeviceForWipe(
      account = w3AccountWithInactiveFingerprint("e5ff120e"),
      session = NfcSessionFake(),
      commands = nfcCommands(),
      expectedDevice = oldW1Device(hardwareFingerprint = "e5ff120e"),
      bitcoinNetworkType = SIGNET
    ).shouldBeOk(Unit)
  }

  test("final old-W1 validation rejects fingerprint mismatch when serial is unavailable") {
    service.validateInactiveDeviceForWipe(
      account = w3AccountWithInactiveFingerprint("other-fingerprint"),
      session = NfcSessionFake(),
      commands = nfcCommands(),
      expectedDevice = oldW1Device(hardwareFingerprint = "other-fingerprint"),
      bitcoinNetworkType = SIGNET
    ).shouldBe(Err(InactiveDeviceWipeValidationError.WrongDevice))
  }

  test("final old-W1 validation requires network when serial is unavailable") {
    service.validateInactiveDeviceForWipe(
      account = w3AccountWithInactiveFingerprint(),
      session = NfcSessionFake(),
      commands = nfcCommands(),
      expectedDevice = oldW1Device(),
      bitcoinNetworkType = null
    ).shouldBe(Err(InactiveDeviceWipeValidationError.MissingBitcoinNetworkType))
  }

  test("final inactive validation treats unauthenticated spending key as device locked") {
    service.validateInactiveDeviceForWipe(
      account = w3AccountWithInactiveFingerprint("e5ff120e"),
      session = NfcSessionFake(),
      commands = nfcCommandsWithInitialSpendingKeyError(
        initialSpendingKeyError = NfcException.CommandErrorUnauthenticated()
      ),
      expectedDevice = oldW1Device(hardwareFingerprint = "e5ff120e"),
      bitcoinNetworkType = SIGNET
    ).shouldBe(Err(InactiveDeviceWipeValidationError.DeviceLocked))
  }

  test("final inactive validation treats generic spending key NFC failure as device check failed") {
    service.validateInactiveDeviceForWipe(
      account = w3AccountWithInactiveFingerprint("e5ff120e"),
      session = NfcSessionFake(),
      commands = nfcCommandsWithInitialSpendingKeyError(
        initialSpendingKeyError = NfcException.UnknownError("failed")
      ),
      expectedDevice = oldW1Device(hardwareFingerprint = "e5ff120e"),
      bitcoinNetworkType = SIGNET
    ).shouldBe(Err(InactiveDeviceWipeValidationError.DeviceCheckFailed))
  }

  test("final old-W1 validation rejects wrong hardware type") {
    service.validateInactiveDeviceForWipe(
      account = w3AccountWithInactiveFingerprint(),
      session = NfcSessionFake(),
      commands = nfcCommands(hwRevision = "w3a-core-evt"),
      expectedDevice = oldW1Device(),
      bitcoinNetworkType = SIGNET
    ).shouldBe(Err(InactiveDeviceWipeValidationError.WrongDevice))
  }

  test("final old-W1 validation reports pending sweep confirmations") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory(
      hardwareFingerprint = "e5ff120e",
      oldW1SweepStatus = OldW1SweepStatus.CONFIRMATIONS_REQUIRED,
      sweepTxids = setOf("sweep-txid")
    )
    bitcoinBlockchain.latestBlockHeight = Ok(104L)
    bitcoinWalletService.transactionsData.value = noPendingTransactionsData.copy(
      transactions = immutableListOf(confirmedTransaction(id = "sweep-txid", blockHeight = 101))
    )

    service.validateInactiveDeviceForWipe(
      account = w3AccountWithInactiveFingerprint("e5ff120e"),
      session = NfcSessionFake(),
      commands = nfcCommands(serial = "old-serial"),
      expectedDevice = oldW1Device(hardwareFingerprint = "e5ff120e"),
      bitcoinNetworkType = SIGNET
    ).shouldBe(Err(InactiveDeviceWipeValidationError.OldDeviceSweepPendingConfirmation))
  }

  test("inactive W1 fingerprint match allows old-device path when safety gates pass") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory()

    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint(),
      tappedDevice = tappedW1Device(serial = "old-serial")
    ).shouldBeOk(DeviceWipeEligibility.InactiveReady(oldW1Device()))
  }

  test("inactive W3 fingerprint match allows inactive-device path when safety gates pass") {
    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint(),
      tappedDevice = tappedW3Device(
        serial = "old-w3-serial",
        initialSpendingKeyFingerprint = "old-fingerprint"
      )
    ).shouldBeOk(DeviceWipeEligibility.InactiveReady(inactiveW3Device()))
  }

  test("active keyset fingerprint does not count as inactive device") {
    service.evaluateLoggedInDevice(
      account = FullAccountW3Mock,
      tappedDevice = tappedW3Device(
        serial = "other-w3-serial",
        initialSpendingKeyFingerprint = "e5ff120e"
      )
    ).shouldBe(Err(DeviceWipeEligibilityError.UnknownDevice))
  }

  test("stale cached old-W1 serial does not bypass W3 old-device safety gates") {
    firmwareDeviceInfoDao.setDeviceInfo(
      FirmwareDeviceInfoMock.copy(serial = "old-serial", hwRevision = "w1a-dvt")
    )
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory()

    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint(),
      tappedDevice = tappedW1Device(serial = "old-serial")
    ).shouldBeOk(DeviceWipeEligibility.InactiveReady(oldW1Device()))
  }

  test("manual old-W1 evaluation allows matching inactive fingerprint among multiple candidates") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory(
      serial = null,
      hardwareFingerprint = "e5ff120e",
      oldW1SweepStatus = OldW1SweepStatus.UNKNOWN
    )
    val account = FullAccountW3Mock.copy(
      keybox = FullAccountW3Mock.keybox.copy(
        keysets = listOf(
          FullAccountW3Mock.keybox.activeSpendingKeyset,
          PrivateSpendingKeysetMock,
          inactiveKeysetWithFingerprint("abcd1234")
        )
      )
    )

    service.evaluateLoggedInDevice(
      account = account,
      tappedDevice = tappedW1Device(
        serial = "old-serial",
        initialSpendingKeyFingerprint = "e5ff120e"
      )
    ).shouldBeOk(
      DeviceWipeEligibility.InactiveReady(
        oldW1Device(hardwareFingerprint = "e5ff120e")
      )
    )
  }

  test("automatic old-W1 readiness blocks unknown sweep status") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory(
      serial = null,
      hardwareFingerprint = "e5ff120e",
      oldW1SweepStatus = OldW1SweepStatus.UNKNOWN
    )

    service.oldW1WipeReadiness(FullAccountW3Mock)
      .shouldBeOk(OldW1WipeReadiness.NotReady)
  }

  test("automatic old-W1 readiness fails closed when confirmations are required but no txids are tracked") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory(
      oldW1SweepStatus = OldW1SweepStatus.CONFIRMATIONS_REQUIRED,
      sweepTxids = emptySet()
    )

    service.oldW1WipeReadiness(FullAccountW3Mock)
      .shouldBe(Err(DeviceWipeEligibilityError.OldDeviceCheckFailed))
  }

  test("W3 upgrade sweep attempted is true when sweep txids are persisted") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory(
      oldW1SweepStatus = OldW1SweepStatus.CONFIRMATIONS_REQUIRED,
      sweepTxids = setOf("txid")
    )

    service.hasW3UpgradeSweepAttempted().shouldBeOk(true)
  }

  test("W3 upgrade sweep attempted is false for no-sweep-required status") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory(
      oldW1SweepStatus = OldW1SweepStatus.NOT_REQUIRED
    )

    service.hasW3UpgradeSweepAttempted().shouldBeOk(false)
  }

  test("record W3 old-W1 wipe marks reminder dismissed for matching old W1") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory(
      hardwareFingerprint = "e5ff120e"
    )

    service.recordW3UpgradeOldW1WipedIfApplicable(
      account = FullAccountW3Mock,
      device = oldW1Device(hardwareFingerprint = "e5ff120e")
    ).shouldBeOk(Unit)

    w3UpgradeDeviceHistoryRepository.oldW1WipeReminderDismissed.shouldBe(true)
  }

  test("record W3 old-W1 wipe ignores non-W3 account") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory()

    service.recordW3UpgradeOldW1WipedIfApplicable(
      account = FullAccountMock,
      device = oldW1Device()
    ).shouldBeOk(Unit)

    w3UpgradeDeviceHistoryRepository.oldW1WipeReminderDismissed.shouldBe(false)
  }

  test("record W3 old-W1 wipe ignores inactive W3 device") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory()

    service.recordW3UpgradeOldW1WipedIfApplicable(
      account = FullAccountW3Mock,
      device = inactiveW3Device()
    ).shouldBeOk(Unit)

    w3UpgradeDeviceHistoryRepository.oldW1WipeReminderDismissed.shouldBe(false)
  }

  test("record W3 old-W1 wipe ignores fingerprint mismatch") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory(
      hardwareFingerprint = "e5ff120e"
    )

    service.recordW3UpgradeOldW1WipedIfApplicable(
      account = FullAccountW3Mock,
      device = oldW1Device(hardwareFingerprint = "other-fingerprint")
    ).shouldBeOk(Unit)

    w3UpgradeDeviceHistoryRepository.oldW1WipeReminderDismissed.shouldBe(false)
  }

  test("W3 old-W1 serial mismatch still allows matching inactive fingerprint") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory(serial = "expected-serial")

    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint(),
      tappedDevice = tappedW1Device(serial = "other-serial")
    ).shouldBeOk(DeviceWipeEligibility.InactiveReady(oldW1Device()))
  }

  test("no serial plus inactive-keyset fingerprint match allows old-device path") {
    val account = FullAccountW3Mock.copy(
      keybox = FullAccountW3Mock.keybox.copy(
        keysets = listOf(
          FullAccountW3Mock.keybox.activeSpendingKeyset,
          PrivateSpendingKeysetMock
        )
      )
    )

    service.evaluateLoggedInDevice(
      account = account,
      tappedDevice = tappedW1Device(initialSpendingKeyFingerprint = "e5ff120e")
    ).shouldBeOk(
      DeviceWipeEligibility.InactiveReady(
        oldW1Device(hardwareFingerprint = "e5ff120e")
      )
    )
  }

  test("no serial plus missing fingerprint fails closed") {
    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint("e5ff120e"),
      tappedDevice = tappedW1Device(initialSpendingKeyFingerprint = null)
    ).shouldBe(Err(DeviceWipeEligibilityError.OldDeviceCheckFailed))
  }

  test("no serial plus mismatched fingerprint returns unknown device") {
    val inactiveKeysetWithDifferentFingerprint = PrivateSpendingKeysetMock.copy(
      hardwareKey = HwSpendingPublicKey(
        DescriptorPublicKeyMock(identifier = "hardware-dpub-3", fingerprint = "abcd1234")
      )
    )
    val account = FullAccountW3Mock.copy(
      keybox = FullAccountW3Mock.keybox.copy(
        keysets = listOf(
          FullAccountW3Mock.keybox.activeSpendingKeyset,
          inactiveKeysetWithDifferentFingerprint
        )
      )
    )

    service.evaluateLoggedInDevice(
      account = account,
      tappedDevice = tappedW1Device(initialSpendingKeyFingerprint = "e5ff120e")
    ).shouldBe(Err(DeviceWipeEligibilityError.UnknownDevice))
  }

  test("device history read failure returns old device check failed") {
    w3UpgradeDeviceHistoryRepository.result = Err(DbQueryError(Error("query failed")))

    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint("e5ff120e"),
      tappedDevice = tappedW1Device(initialSpendingKeyFingerprint = "e5ff120e")
    ).shouldBe(Err(DeviceWipeEligibilityError.OldDeviceCheckFailed))
  }

  test("sync failure returns old device check failed") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory()
    bitcoinWalletService.syncResult = Err(Error("sync failed"))

    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint(),
      tappedDevice = tappedW1Device(serial = "old-serial")
    ).shouldBe(Err(DeviceWipeEligibilityError.OldDeviceCheckFailed))
  }

  test("any active pending transaction returns old device pending active transaction") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory()
    bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
      transactions = immutableListOf(BitcoinTransactionFake)
    )

    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint(),
      tappedDevice = tappedW1Device(serial = "old-serial")
    ).shouldBe(Err(DeviceWipeEligibilityError.OldDevicePendingActiveTransaction))
  }

  test("sweep estimate success returns inactive device has funds") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory()
    sweepService.estimateSweepToActiveKeysetResult = Ok(sweep())

    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint(),
      tappedDevice = tappedW1Device(serial = "old-serial")
    ).shouldBeOk(DeviceWipeEligibility.InactiveHasFunds(oldW1Device()))
  }

  test("no funds to sweep allows old-W1 wipe") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory()
    sweepService.estimateSweepToActiveKeysetResult = Err(NoFundsToSweep)

    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint(),
      tappedDevice = tappedW1Device(serial = "old-serial")
    ).shouldBeOk(DeviceWipeEligibility.InactiveReady(oldW1Device()))
    sweepService.estimateSweepToActiveKeysetCalls.single()
      .shouldBe(SweepContext.InactiveHardware("old-fingerprint"))
  }

  test("unrelated inactive keyset funds do not block tapped inactive device") {
    val account = FullAccountW3Mock.copy(
      keybox = FullAccountW3Mock.keybox.copy(
        keysets = listOf(
          FullAccountW3Mock.keybox.activeSpendingKeyset,
          inactiveKeysetWithFingerprint("old-fingerprint"),
          inactiveKeysetWithFingerprint("unrelated-fingerprint")
        )
      )
    )
    sweepService.estimateSweepToActiveKeysetHandler = { context ->
      if (context == SweepContext.InactiveHardware("old-fingerprint")) {
        Err(NoFundsToSweep)
      } else {
        Ok(sweep())
      }
    }

    service.evaluateLoggedInDevice(
      account = account,
      tappedDevice = tappedW1Device(initialSpendingKeyFingerprint = "old-fingerprint")
    ).shouldBeOk(DeviceWipeEligibility.InactiveReady(oldW1Device()))
  }

  test("confirmed sweep funds in active W3 wallet do not block old-W1 wipe") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory()
    sweepService.estimateSweepWithMockDestinationResult = Ok(sweep())
    sweepService.estimateSweepToActiveKeysetResult = Err(NoFundsToSweep)

    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint(),
      tappedDevice = tappedW1Device(serial = "old-serial")
    ).shouldBeOk(DeviceWipeEligibility.InactiveReady(oldW1Device()))
  }

  test("sweep generation failure returns old device check failed") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory()
    sweepService.estimateSweepToActiveKeysetResult =
      Err(SweepGenerationFailed(Error("sweep failed")))

    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint(),
      tappedDevice = tappedW1Device(serial = "old-serial")
    ).shouldBe(Err(DeviceWipeEligibilityError.OldDeviceCheckFailed))
  }

  test("confirmation-required old W1 wipe blocks when tracked sweep txid is missing") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory(
      oldW1SweepStatus = OldW1SweepStatus.CONFIRMATIONS_REQUIRED,
      sweepTxids = setOf("sweep-txid")
    )

    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint(),
      tappedDevice = tappedW1Device(serial = "old-serial")
    ).shouldBe(Err(DeviceWipeEligibilityError.OldDeviceSweepPendingConfirmation))
  }

  test("confirmation-required old W1 wipe blocks before five confirmations") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory(
      oldW1SweepStatus = OldW1SweepStatus.CONFIRMATIONS_REQUIRED,
      sweepTxids = setOf("sweep-txid")
    )
    bitcoinBlockchain.latestBlockHeight = Ok(104L)
    bitcoinWalletService.transactionsData.value = noPendingTransactionsData.copy(
      transactions = immutableListOf(confirmedTransaction(id = "sweep-txid", blockHeight = 101))
    )

    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint(),
      tappedDevice = tappedW1Device(serial = "old-serial")
    ).shouldBe(Err(DeviceWipeEligibilityError.OldDeviceSweepPendingConfirmation))
  }

  test("confirmation-required old W1 wipe blocks when tracked sweep txid is pending") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory(
      oldW1SweepStatus = OldW1SweepStatus.CONFIRMATIONS_REQUIRED,
      sweepTxids = setOf("sweep-txid")
    )
    bitcoinWalletService.transactionsData.value = noPendingTransactionsData.copy(
      transactions = immutableListOf(BitcoinTransactionFake.copy(id = "sweep-txid"))
    )

    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint(),
      tappedDevice = tappedW1Device(serial = "old-serial")
    ).shouldBe(Err(DeviceWipeEligibilityError.OldDeviceSweepPendingConfirmation))
  }

  test("confirmation-required old W1 wipe blocks when latest block height check fails") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory(
      oldW1SweepStatus = OldW1SweepStatus.CONFIRMATIONS_REQUIRED,
      sweepTxids = setOf("sweep-txid")
    )
    bitcoinBlockchain.latestBlockHeight = Err(Generic(cause = Error("height failed"), message = null))
    bitcoinWalletService.transactionsData.value = noPendingTransactionsData.copy(
      transactions = immutableListOf(confirmedTransaction(id = "sweep-txid", blockHeight = 100))
    )

    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint(),
      tappedDevice = tappedW1Device(serial = "old-serial")
    ).shouldBe(Err(DeviceWipeEligibilityError.OldDeviceCheckFailed))
  }

  test("confirmation-required old W1 wipe allows at exactly five confirmations") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory(
      oldW1SweepStatus = OldW1SweepStatus.CONFIRMATIONS_REQUIRED,
      sweepTxids = setOf("sweep-txid")
    )
    bitcoinBlockchain.latestBlockHeight = Ok(104L)
    bitcoinWalletService.transactionsData.value = noPendingTransactionsData.copy(
      transactions = immutableListOf(confirmedTransaction(id = "sweep-txid", blockHeight = 100))
    )

    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint(),
      tappedDevice = tappedW1Device(serial = "old-serial")
    ).shouldBeOk(DeviceWipeEligibility.InactiveReady(oldW1Device()))
  }

  test("confirmation-required old W1 wipe requires every tracked txid to reach five confirmations") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory(
      oldW1SweepStatus = OldW1SweepStatus.CONFIRMATIONS_REQUIRED,
      sweepTxids = setOf("ready-txid", "not-ready-txid")
    )
    bitcoinBlockchain.latestBlockHeight = Ok(104L)
    bitcoinWalletService.transactionsData.value = noPendingTransactionsData.copy(
      transactions = immutableListOf(
        confirmedTransaction(id = "ready-txid", blockHeight = 100),
        confirmedTransaction(id = "not-ready-txid", blockHeight = 101)
      )
    )

    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint(),
      tappedDevice = tappedW1Device(serial = "old-serial")
    ).shouldBe(Err(DeviceWipeEligibilityError.OldDeviceSweepPendingConfirmation))
  }

  test("not-required old W1 wipe allows despite active pending transactions once old W1 funds are absent") {
    w3UpgradeDeviceHistoryRepository.setOldDeviceHistory(
      oldW1SweepStatus = OldW1SweepStatus.NOT_REQUIRED
    )
    bitcoinWalletService.transactionsData.value = TransactionsDataMock.copy(
      transactions = immutableListOf(BitcoinTransactionFake)
    )

    service.evaluateLoggedInDevice(
      account = w3AccountWithInactiveFingerprint(),
      tappedDevice = tappedW1Device(serial = "old-serial")
    ).shouldBeOk(DeviceWipeEligibility.InactiveReady(oldW1Device()))
  }
})

private fun tappedW1Device(
  serial: String = "w1-serial",
  authKey: build.wallet.bitkey.hardware.HwAuthPublicKey? = null,
  initialSpendingKeyFingerprint: String? = "old-fingerprint",
) = TappedDeviceIdentity(
  deviceInfo = FirmwareDeviceInfoMock.copy(
    serial = serial,
    hwRevision = "w1a-dvt"
  ),
  authKey = authKey,
  initialSpendingKeyFingerprint = initialSpendingKeyFingerprint
)

private fun tappedW3Device(
  serial: String = "w3-serial",
  initialSpendingKeyFingerprint: String? = null,
) = TappedDeviceIdentity(
  deviceInfo = FirmwareDeviceInfoMock.copy(
    serial = serial,
    hwRevision = "w3a-core-evt"
  ),
  authKey = null,
  initialSpendingKeyFingerprint = initialSpendingKeyFingerprint
)

private fun nfcCommands(
  serial: String = "old-serial",
  hwRevision: String = "w1a-dvt",
) = NfcCommandsMock(turbine = { name -> Turbine(name = name) }).apply {
  deviceInfoResult = FirmwareDeviceInfoMock.copy(
    serial = serial,
    hwRevision = hwRevision
  )
}

private fun nfcCommandsWithInitialSpendingKeyError(
  initialSpendingKeyError: NfcException,
) = object : NfcCommandsMock(turbine = { name -> Turbine(name = name) }) {
  override suspend fun getInitialSpendingKey(
    session: NfcSession,
    network: BitcoinNetworkType,
  ): HwSpendingPublicKey {
    throw initialSpendingKeyError
  }
}

private fun oldW1Device(
  hardwareFingerprint: String = "old-fingerprint",
) = InactiveHardwareDevice(
  hardwareType = W1,
  hardwareFingerprint = hardwareFingerprint
)

private fun inactiveW3Device(
  hardwareFingerprint: String = "old-fingerprint",
) = InactiveHardwareDevice(
  hardwareType = W3,
  hardwareFingerprint = hardwareFingerprint
)

private fun w3AccountWithInactiveFingerprint(
  fingerprint: String = "old-fingerprint",
): FullAccount =
  FullAccountW3Mock.copy(
    keybox = FullAccountW3Mock.keybox.copy(
      keysets = listOf(
        FullAccountW3Mock.keybox.activeSpendingKeyset,
        inactiveKeysetWithFingerprint(fingerprint)
      )
    )
  )

private fun w1AccountWithInactiveFingerprint(
  fingerprint: String = "old-fingerprint",
): FullAccount =
  FullAccountMock.copy(
    keybox = FullAccountMock.keybox.copy(
      keysets = listOf(
        FullAccountMock.keybox.activeSpendingKeyset,
        inactiveKeysetWithFingerprint(fingerprint)
      )
    )
  )

private fun inactiveKeysetWithFingerprint(fingerprint: String) =
  PrivateSpendingKeysetMock.copy(
    hardwareKey = HwSpendingPublicKey(
      DescriptorPublicKeyMock(
        identifier = "hardware-dpub-$fingerprint",
        fingerprint = fingerprint
      )
    )
  )

private fun sweep() = Sweep(
  unsignedPsbts = setOf(
    SweepPsbt(
      psbt = PsbtMock,
      signaturePlan = SweepSignaturePlan.AppAndHardware,
      sourceKeyset = FullAccountW3Mock.keybox.activeSpendingKeyset,
      destinationAddress = "bc1qtest"
    )
  )
)

private fun confirmedTransaction(
  id: String,
  blockHeight: Long,
): BitcoinTransaction =
  BitcoinTransactionSend.copy(
    id = id,
    confirmationStatus = Confirmed(BlockTime(blockHeight, someInstant))
  )

private class W3UpgradeDeviceHistoryRepositoryFake : W3UpgradeDeviceHistoryRepository {
  var result: Result<W3UpgradeDeviceHistory?, DbError> = Ok(null)
  val replacedSweepTxids = mutableListOf<Set<String>>()
  var oldW1WipeReminderDismissed = false

  override suspend fun getDeviceHistory(): Result<W3UpgradeDeviceHistory?, DbError> = result

  override suspend fun setOldW1SweepStatus(status: OldW1SweepStatus): Result<Unit, DbError> {
    result = Ok(currentOrDefault().copy(oldW1SweepStatus = status))
    return Ok(Unit)
  }

  override suspend fun replaceSweepTransactions(txids: Set<String>): Result<Unit, DbError> {
    replacedSweepTxids.add(txids)
    result = Ok(
      currentOrDefault().copy(
        oldW1SweepStatus = OldW1SweepStatus.CONFIRMATIONS_REQUIRED,
        sweepTxids = txids
      )
    )
    return Ok(Unit)
  }

  override suspend fun markOldW1WipeReminderDismissed(): Result<Unit, DbError> {
    oldW1WipeReminderDismissed = true
    result.get()?.let { current ->
      result = Ok(current.copy(oldW1WipeReminderDismissed = true))
    }
    return Ok(Unit)
  }

  fun setOldDeviceHistory(
    serial: String? = "old-serial",
    hardwareFingerprint: String = "old-fingerprint",
    oldW1SweepStatus: OldW1SweepStatus = OldW1SweepStatus.UNKNOWN,
    oldW1WipeReminderDismissed: Boolean = false,
    sweepTxids: Set<String> = emptySet(),
  ) {
    result = Ok(
      W3UpgradeDeviceHistory(
        oldDeviceSerial = serial,
        oldHardwareFingerprint = hardwareFingerprint,
        oldW1SweepStatus = oldW1SweepStatus,
        oldW1WipeReminderDismissed = oldW1WipeReminderDismissed,
        sweepTxids = sweepTxids
      )
    )
  }

  fun reset() {
    result = Ok(null)
    replacedSweepTxids.clear()
    oldW1WipeReminderDismissed = false
  }

  private fun currentOrDefault(): W3UpgradeDeviceHistory =
    result.get() ?: W3UpgradeDeviceHistory(
      oldDeviceSerial = null,
      oldHardwareFingerprint = null,
      oldW1SweepStatus = OldW1SweepStatus.UNKNOWN,
      oldW1WipeReminderDismissed = false,
      sweepTxids = emptySet()
    )
}
