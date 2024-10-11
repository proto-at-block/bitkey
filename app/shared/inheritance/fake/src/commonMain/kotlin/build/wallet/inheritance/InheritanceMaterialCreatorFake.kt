package build.wallet.inheritance

import build.wallet.bitkey.inheritance.InheritanceMaterial
import build.wallet.bitkey.inheritance.InheritanceMaterialHash
import build.wallet.bitkey.keybox.Keybox
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class InheritanceMaterialCreatorFake(
  var inheritanceMaterial: Result<InheritanceMaterial, Error>,
  var inheritanceMaterialHash: Result<InheritanceMaterialHash, Error> =
    Ok(InheritanceMaterialHash(-1)),
) : InheritanceMaterialCreator {
  override suspend fun getInheritanceMaterialHash(
    keybox: Keybox,
  ): Result<InheritanceMaterialHash, Error> {
    return inheritanceMaterialHash
  }

  override suspend fun createInheritanceMaterial(
    keybox: Keybox,
  ): Result<InheritanceMaterial, Error> {
    return inheritanceMaterial
  }
}
