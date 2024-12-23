package build.wallet.di

import build.wallet.coroutines.scopes.CoroutineScopes
import kotlinx.coroutines.CoroutineScope
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface CoroutinesComponent {
  @Provides
  fun appCoroutineScope(): CoroutineScope = CoroutineScopes.AppScope
}
