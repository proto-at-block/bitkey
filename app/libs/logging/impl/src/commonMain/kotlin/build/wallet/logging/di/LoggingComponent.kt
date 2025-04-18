package build.wallet.logging.di

import build.wallet.di.AppScope
import build.wallet.logging.dev.LogStore
import build.wallet.logging.dev.LogStoreInMemory
import build.wallet.logging.prod.BoundedInMemoryLogStore
import build.wallet.platform.config.AppVariant
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface LoggingComponent {
  @Provides
  fun provideLogStore(
    appVariant: AppVariant,
    logStoreInMemory: LogStoreInMemory,
    boundedInMemoryLogStore: BoundedInMemoryLogStore,
  ): LogStore {
    return when (appVariant) {
      AppVariant.Development -> logStoreInMemory
      AppVariant.Alpha -> logStoreInMemory
      AppVariant.Team -> logStoreInMemory
      AppVariant.Customer -> boundedInMemoryLogStore
      AppVariant.Emergency -> boundedInMemoryLogStore
    }
  }
}
