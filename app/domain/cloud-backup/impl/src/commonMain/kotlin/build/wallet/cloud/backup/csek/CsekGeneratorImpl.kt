package build.wallet.cloud.backup.csek

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SymmetricKeyGenerator

@BitkeyInject(AppScope::class)
class CsekGeneratorImpl(
  private val symmetricKeyGenerator: SymmetricKeyGenerator,
) : CsekGenerator {
  override suspend fun generate(): Csek {
    val symmetricKey = symmetricKeyGenerator.generate()
    return Csek(symmetricKey)
  }
}
