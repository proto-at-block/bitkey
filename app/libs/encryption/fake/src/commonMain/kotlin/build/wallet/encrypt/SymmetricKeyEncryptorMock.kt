package build.wallet.encrypt

import build.wallet.crypto.SymmetricKey
import okio.ByteString

class SymmetricKeyEncryptorMock : SymmetricKeyEncryptor {
  lateinit var sealResult: SealedData
  lateinit var unsealResult: ByteString

  override fun seal(
    unsealedData: ByteString,
    key: SymmetricKey,
  ) = sealResult

  override fun unseal(
    sealedData: SealedData,
    key: SymmetricKey,
  ) = unsealResult
}
