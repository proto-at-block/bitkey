package build.wallet.relationships.di

import build.wallet.di.AppScope
import build.wallet.f8e.relationships.EndorseTrustedContactsF8eClientProvider
import build.wallet.relationships.RelationshipsF8eClientProvider
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface RelationshipsComponent {
  @Provides
  fun provideEndorseTrustedContactsF8eClientProvider(
    relationshipsF8eClientProvider: RelationshipsF8eClientProvider,
  ): EndorseTrustedContactsF8eClientProvider {
    return EndorseTrustedContactsF8eClientProvider {
      relationshipsF8eClientProvider.get()
    }
  }
}
