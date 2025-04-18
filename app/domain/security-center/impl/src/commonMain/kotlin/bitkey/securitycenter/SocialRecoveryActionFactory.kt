package bitkey.securitycenter

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.recovery.socrec.SocRecService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SocialRecoveryActionFactory {
  suspend fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class SocialRecoveryActionFactoryImpl(
  private val socRecService: SocRecService,
) : SocialRecoveryActionFactory {
  override suspend fun create(): Flow<SecurityAction?> {
    return socRecService.socRecRelationships.map { SocialRecoveryAction(it) }
  }
}
