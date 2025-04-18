package bitkey.securitycenter

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.inheritance.InheritanceService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface InheritanceActionFactory {
  suspend fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class InheritanceActionFactoryImpl(
  private val inheritanceService: InheritanceService,
) : InheritanceActionFactory {
  override suspend fun create(): Flow<SecurityAction?> {
    return inheritanceService.inheritanceRelationships.map { relationships ->
      InheritanceAction(relationships)
    }
  }
}
