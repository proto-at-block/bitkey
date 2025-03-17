package build.wallet.emergencyaccesskit

import build.wallet.crypto.SymmetricKey
import build.wallet.encrypt.SealedData
import build.wallet.encrypt.SymmetricKeyEncryptor
import okio.ByteString

internal class SymmetricKeyEncryptorFake : SymmetricKeyEncryptor {
  override fun seal(
    unsealedData: ByteString,
    key: SymmetricKey,
  ): SealedData {
    return SealedData(
      ciphertext = unsealedData,
      nonce = key.raw,
      tag = ByteString.EMPTY
    )
  }

  override fun unseal(
    sealedData: SealedData,
    key: SymmetricKey,
  ): ByteString {
    return sealedData.ciphertext
  }
}
