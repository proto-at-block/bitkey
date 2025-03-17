package build.wallet.cloud.store.di

import build.wallet.cloud.store.CloudStoreServiceProvider
import build.wallet.cloud.store.CloudStoreServiceProviderFake
import build.wallet.di.AppScope
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

@ContributesTo(AppScope::class)
interface JvmCloudStoreComponent {
  @Provides
  fun cloudStoreServiceProvider(): CloudStoreServiceProvider = CloudStoreServiceProviderFake
}
