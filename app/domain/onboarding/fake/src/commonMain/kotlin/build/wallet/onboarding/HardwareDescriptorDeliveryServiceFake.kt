package build.wallet.onboarding

import build.wallet.bitkey.account.FullAccount
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class HardwareDescriptorDeliveryServiceFake : HardwareDescriptorDeliveryService {
  var fetchSignatureAndPrepareNfcSessionResult: Result<suspend (NfcSession, NfcCommands) -> String, Error> =
    Ok { _, _ -> "fake-hw-signature" }

  var fetchSignatureAndPrepareNfcSessionCalled: Boolean = false

  override suspend fun fetchSignatureAndPrepareNfcSession(
    account: FullAccount,
  ): Result<suspend (NfcSession, NfcCommands) -> String, Error> {
    fetchSignatureAndPrepareNfcSessionCalled = true
    return fetchSignatureAndPrepareNfcSessionResult
  }

  fun reset() {
    fetchSignatureAndPrepareNfcSessionResult = Ok { _, _ -> "fake-hw-signature" }
    fetchSignatureAndPrepareNfcSessionCalled = false
  }
}
