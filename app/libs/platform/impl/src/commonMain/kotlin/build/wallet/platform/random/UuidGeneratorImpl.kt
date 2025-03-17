package build.wallet.platform.random

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class UuidGeneratorImpl : UuidGenerator {
  override fun random(): String = uuid()
}
