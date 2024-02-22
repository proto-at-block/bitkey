package build.wallet.cloud.backup.csek

import build.wallet.encrypt.SymmetricKeyGenerator

class CsekGeneratorImpl(
  private val symmetricKeyGenerator: SymmetricKeyGenerator,
) : CsekGenerator {
  override suspend fun generate(): Csek {
    val symmetricKey = symmetricKeyGenerator.generate()
    return Csek(symmetricKey)
  }
}
