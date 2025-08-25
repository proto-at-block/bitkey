package build.wallet.encrypt

import okio.ByteString.Companion.encodeUtf8

class XNonceGeneratorMock : XNonceGenerator {
  private val defaultXNonce = XNonce("default-nonce-24-bytes-long".encodeUtf8())

  var generateXNonceResult: XNonce = defaultXNonce
  val generateXNonceCalls = mutableListOf<Unit>()

  override fun generateXNonce(): XNonce {
    generateXNonceCalls.add(Unit)
    return generateXNonceResult
  }

  fun reset() {
    generateXNonceResult = defaultXNonce
    generateXNonceCalls.clear()
  }
}
