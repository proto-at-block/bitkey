package bitkey.securitycenter

import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.recovery.socrec.SocRecService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

interface SocialRecoveryActionFactory {
  suspend fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class SocialRecoveryActionFactoryImpl(
  private val socRecService: SocRecService,
  private val appFunctionalityService: AppFunctionalityService,
) : SocialRecoveryActionFactory {
  override suspend fun create(): Flow<SecurityAction?> {
    return combine(
      socRecService.socRecRelationships,
      appFunctionalityService.status
    ) { socRecRelationships, status ->
      SocialRecoveryAction(socRecRelationships, status.featureStates.securityAndRecovery)
    }.onStart {
      emit(
        SocialRecoveryAction(
          relationships = null,
          featureState = FunctionalityFeatureStates.FeatureState.Unavailable
        )
      )
    }
  }
}
