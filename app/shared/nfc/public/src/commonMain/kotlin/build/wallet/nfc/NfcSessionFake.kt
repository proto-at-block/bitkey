package build.wallet.nfc

import build.wallet.nfc.platform.NfcSessionProvider

class NfcSessionFake(
  override val parameters: NfcSession.Parameters,
) : NfcSession {
  init {
    parameters.onTagConnected()
  }

  override suspend fun transceive(buffer: List<UByte>) = emptyList<UByte>()

  override var message: String? = null

  override fun close() = Unit

  companion object : NfcSessionProvider {
    val FakeParameters =
      NfcSession.Parameters(
        isHardwareFake = true,
        needsAuthentication = true,
        shouldLock = true,
        skipFirmwareTelemetry = false,
        nfcFlowName = "fake-flow-name",
        onTagConnected = {},
        onTagDisconnected = {},
        asyncNfcSigning = false
      )

    override fun get(parameters: NfcSession.Parameters): NfcSession = NfcSessionFake(parameters)

    operator fun invoke() = this.get(FakeParameters)
  }
}
