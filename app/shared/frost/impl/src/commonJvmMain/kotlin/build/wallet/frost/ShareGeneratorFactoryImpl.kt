package build.wallet.frost

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class ShareGeneratorFactoryImpl : ShareGeneratorFactory {
  override fun createShareGenerator(): ShareGenerator {
    return ShareGeneratorImpl()
  }
}
