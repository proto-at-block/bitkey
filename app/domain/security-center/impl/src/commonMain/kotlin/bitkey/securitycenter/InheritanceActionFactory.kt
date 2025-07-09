package bitkey.securitycenter

import build.wallet.availability.AppFunctionalityService
import build.wallet.availability.FunctionalityFeatureStates
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.inheritance.InheritanceService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

interface InheritanceActionFactory {
  suspend fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class InheritanceActionFactoryImpl(
  private val inheritanceService: InheritanceService,
  private val appFunctionalityService: AppFunctionalityService,
) : InheritanceActionFactory {
  override suspend fun create(): Flow<SecurityAction?> {
    return combine(
      inheritanceService.inheritanceRelationships,
      appFunctionalityService.status
    ) { relationships, status ->
      InheritanceAction(relationships, status.featureStates.inheritance)
    }.onStart {
      emit(
        InheritanceAction(
          relationships = null,
          featureState = FunctionalityFeatureStates.FeatureState.Unavailable
        )
      )
    }
  }
}
