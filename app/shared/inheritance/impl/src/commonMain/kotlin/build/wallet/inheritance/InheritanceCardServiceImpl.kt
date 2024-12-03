package build.wallet.inheritance

import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.store.KeyValueStoreFactory
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.SuspendSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@OptIn(ExperimentalSettingsApi::class)
class InheritanceCardServiceImpl(
  coroutineScope: CoroutineScope,
  inheritanceService: InheritanceService,
  val keyValueStoreFactory: KeyValueStoreFactory,
) : InheritanceCardService {
  private val dismissedClaims = MutableStateFlow<Set<String>>(emptySet())

  override val claimCardsToDisplay: Flow<List<BeneficiaryClaim>> =
    combine(
      inheritanceService.pendingBeneficiaryClaims,
      inheritanceService.lockedBeneficiaryClaims,
      dismissedClaims
    ) { pendingClaims, lockedClaims, dismissedClaims ->
      // filter out dismissed pending claim cards
      pendingClaims.filter { !dismissedClaims.contains(it.claimId.value) } + lockedClaims
    }

  private suspend fun store(): SuspendSettings =
    keyValueStoreFactory.getOrCreate("PENDING_CLAIM_CARD_DISMISSED_STORE")

  init {
    coroutineScope.launch {
      dismissedClaims.value = store().keys()
    }
  }

  override suspend fun dismissPendingClaimCard(claimId: String) {
    store().putBoolean(claimId, true)
    dismissedClaims.value = store().keys()
  }
}
