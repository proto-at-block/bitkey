package build.wallet.logging.di

import build.wallet.di.AppScope
import build.wallet.logging.LogWriterContextStore
import co.touchlab.kermit.LogWriter
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface IosLoggingComponent {
  @Provides
  fun provideAdditionalLogWriters(
    logWriterContextStore: LogWriterContextStore,
    additionalLogWritersProvider: (LogWriterContextStore) -> List<LogWriter>,
  ): List<LogWriter> = additionalLogWritersProvider(logWriterContextStore)
}
