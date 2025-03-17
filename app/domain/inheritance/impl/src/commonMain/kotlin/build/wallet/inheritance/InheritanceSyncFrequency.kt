package build.wallet.inheritance

import build.wallet.di.AppScope
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Defines how frequently to sync claims and inheritance material.
 */
@JvmInline
value class InheritanceSyncFrequency(val value: Duration = 1.minutes) {
  @ContributesTo(AppScope::class)
  interface Component {
    @Provides
    fun provideInheritanceSyncFrequency() = InheritanceSyncFrequency()
  }
}
