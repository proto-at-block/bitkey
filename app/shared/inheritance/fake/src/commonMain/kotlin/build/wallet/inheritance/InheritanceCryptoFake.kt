package build.wallet.inheritance

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.inheritance.InheritanceKeyset
import build.wallet.bitkey.inheritance.InheritanceKeysetFake
import build.wallet.bitkey.inheritance.InheritanceMaterial
import build.wallet.bitkey.inheritance.InheritanceMaterialHashData
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.encrypt.XCiphertext
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class InheritanceCryptoFake(
  var inheritanceMaterial: Result<InheritanceMaterial, Error>,
  var inheritanceMaterialHash: Result<InheritanceMaterialHashData, Error> =
    Ok(
      InheritanceMaterialHashData(
        networkType = BitcoinNetworkType.BITCOIN,
        spendingKey = SpendingKeysetMock.appKey,
        contacts = emptyList()
      )
    ),
  var inheritanceKeysetResult: Result<InheritanceKeyset, Error> = Ok(InheritanceKeysetFake),
) : InheritanceCrypto {
  override suspend fun getInheritanceMaterialHashData(
    keybox: Keybox,
  ): Result<InheritanceMaterialHashData, Error> {
    return inheritanceMaterialHash
  }

  override suspend fun createInheritanceMaterial(
    keybox: Keybox,
  ): Result<InheritanceMaterial, Error> {
    return inheritanceMaterial
  }

  override suspend fun decryptInheritanceMaterial(
    delegatedDecryptionKey: AppKey<DelegatedDecryptionKey>,
    sealedDek: XCiphertext,
    sealedMobileKey: XCiphertext,
  ): Result<InheritanceKeyset, Error> {
    return inheritanceKeysetResult
  }
}
