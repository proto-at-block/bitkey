package build.wallet.relationships

import build.wallet.di.AppScope
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Determines how frequently we should sync relationships.
 */
@JvmInline
value class RelationshipsSyncFrequency(val value: Duration = 5.seconds) {
  @ContributesTo(AppScope::class)
  interface Component {
    @Provides
    fun provideSocRecSyncFrequency() = RelationshipsSyncFrequency()
  }
}
