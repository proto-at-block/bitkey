package build.wallet.inheritance

import build.wallet.bitkey.inheritance.InheritanceClaim
import build.wallet.bitkey.inheritance.InheritanceClaimId
import build.wallet.bitkey.inheritance.isActive
import build.wallet.bitkey.inheritance.isApproved
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.store.KeyValueStoreFactory
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.SuspendSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@BitkeyInject(AppScope::class)
@OptIn(ExperimentalSettingsApi::class)
class InheritanceCardServiceImpl(
  coroutineScope: CoroutineScope,
  inheritanceService: InheritanceService,
  val keyValueStoreFactory: KeyValueStoreFactory,
) : InheritanceCardService {
  private val dismissedBeneficiaryPendingClaimIds = MutableStateFlow<Set<InheritanceClaimId>>(emptySet())
  private val dismissedStoreMutex = Mutex()

  override val cardsToDisplay: Flow<List<InheritanceClaim>> =
    combine(
      inheritanceService.claimsSnapshot,
      dismissedBeneficiaryPendingClaimIds
    ) { claimsSnapshot, dismissedPendingClaims ->
      claimsSnapshot.claims.all.filter {
        when {
          it.isActive && !it.isApproved(claimsSnapshot.timestamp) -> it.claimId !in dismissedPendingClaims
          else -> true
        }
      }
    }

  private suspend fun store(): SuspendSettings =
    keyValueStoreFactory.getOrCreate("PENDING_CLAIM_CARD_DISMISSED_STORE")

  init {
    coroutineScope.launch {
      dismissedStoreMutex.withLock {
        dismissedBeneficiaryPendingClaimIds.value = store().keys()
          .map { InheritanceClaimId(it) }
          .toSet()
      }
    }
  }

  override suspend fun dismissPendingBeneficiaryClaimCard(claimId: InheritanceClaimId) {
    dismissedStoreMutex.withLock {
      store().putBoolean(claimId.value, true)
      dismissedBeneficiaryPendingClaimIds.update { it + claimId }
    }
  }
}
