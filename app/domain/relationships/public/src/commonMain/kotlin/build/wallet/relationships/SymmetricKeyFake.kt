package build.wallet.relationships

import bitkey.data.PrivateData
import build.wallet.crypto.SymmetricKey
import okio.ByteString

@OptIn(PrivateData::class)
class SymmetricKeyFake(override val raw: ByteString) : SymmetricKey {
  override val length: Int
    get() = raw.size
}
