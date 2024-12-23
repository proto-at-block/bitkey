package build.wallet.inheritance

import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.InheritanceClaim
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.store.KeyValueStoreFactory
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.SuspendSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@BitkeyInject(AppScope::class)
@OptIn(ExperimentalSettingsApi::class)
class InheritanceCardServiceImpl(
  coroutineScope: CoroutineScope,
  inheritanceService: InheritanceService,
  val keyValueStoreFactory: KeyValueStoreFactory,
) : InheritanceCardService {
  private val dismissedBeneficiaryPendingClaims = MutableStateFlow<Set<String>>(emptySet())

  override val cardsToDisplay: Flow<List<InheritanceClaim>> =
    combine(
      inheritanceService.claims,
      dismissedBeneficiaryPendingClaims
    ) { claims, dismissedPendingClaims ->
      // filter out dismissed pending claim cards
      claims.filter {
        if (it is BeneficiaryClaim.PendingClaim) {
          !dismissedPendingClaims.contains(it.claimId.value)
        } else {
          true
        }
      }
    }

  private suspend fun store(): SuspendSettings =
    keyValueStoreFactory.getOrCreate("PENDING_CLAIM_CARD_DISMISSED_STORE")

  init {
    coroutineScope.launch {
      dismissedBeneficiaryPendingClaims.value = store().keys()
    }
  }

  override suspend fun dismissPendingBeneficiaryClaimCard(claimId: String) {
    store().putBoolean(claimId, true)
    dismissedBeneficiaryPendingClaims.value = store().keys()
  }
}
