package build.wallet.testing

import bitkey.account.HardwareType
import build.wallet.nfc.NfcSession
import build.wallet.nfc.NfcSession.RequirePairedHardware.NotRequired
import build.wallet.nfc.NfcSessionFake
import build.wallet.nfc.NfcTransactor
import build.wallet.nfc.TransactionFn

suspend fun <T> NfcTransactor.fakeTransact(
  hardwareType: HardwareType = HardwareType.W1,
  transaction: TransactionFn<T>,
) = transact(
  parameters = NfcSession.Parameters(
    isHardwareFake = true,
    hardwareType = hardwareType,
    needsAuthentication = NfcSessionFake.FakeParameters.needsAuthentication,
    shouldLock = NfcSessionFake.FakeParameters.shouldLock,
    skipFirmwareTelemetry = NfcSessionFake.FakeParameters.skipFirmwareTelemetry,
    nfcFlowName = NfcSessionFake.FakeParameters.nfcFlowName,
    requirePairedHardware = NotRequired,
    maxNfcRetryAttempts = NfcSessionFake.FakeParameters.maxNfcRetryAttempts,
    onTagConnected = {},
    onTagDisconnected = {},
    asyncNfcSigning = NfcSessionFake.FakeParameters.asyncNfcSigning
  ),
  transaction = transaction
)
