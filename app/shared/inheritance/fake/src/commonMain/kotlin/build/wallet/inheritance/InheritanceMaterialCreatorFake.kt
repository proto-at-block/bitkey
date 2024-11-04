package build.wallet.inheritance

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.inheritance.InheritanceMaterial
import build.wallet.bitkey.inheritance.InheritanceMaterialHashData
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeysetMock
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class InheritanceMaterialCreatorFake(
  var inheritanceMaterial: Result<InheritanceMaterial, Error>,
  var inheritanceMaterialHash: Result<InheritanceMaterialHashData, Error> =
    Ok(
      InheritanceMaterialHashData(
        networkType = BitcoinNetworkType.BITCOIN,
        spendingKey = SpendingKeysetMock.appKey,
        contacts = emptyList()
      )
    ),
) : InheritanceMaterialCreator {
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
}
