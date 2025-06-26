package build.wallet.cloud.backup.csek

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SymmetricKeyGenerator

@BitkeyInject(AppScope::class)
class SekGeneratorImpl(
  private val symmetricKeyGenerator: SymmetricKeyGenerator,
) : SekGenerator {
  override suspend fun generate(): Sek {
    val symmetricKey = symmetricKeyGenerator.generate()
    return Sek(symmetricKey)
  }
}
