package build.wallet.inheritance

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.BenefactorClaim
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.InheritanceClaim
import build.wallet.bitkey.inheritance.InheritanceClaims
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.inheritance.RetrieveInheritanceClaimsF8eClient
import build.wallet.logging.logFailure
import build.wallet.mapResult
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@BitkeyInject(AppScope::class)
class InheritanceClaimsRepositoryImpl(
  accountService: AccountService,
  private val inheritanceClaimsDao: InheritanceClaimsDao,
  private val retrieveInheritanceClaimsF8eClient: RetrieveInheritanceClaimsF8eClient,
  stateScope: CoroutineScope,
) : InheritanceClaimsRepository {
  /**
   * Current account status.
   */
  private val account = accountService.accountStatus()
    .mapResult { it as? AccountStatus.ActiveAccount }
    .mapNotNull { it.get()?.account as? FullAccount }
    .distinctUntilChanged()

  /**
   * Local database stored claims.
   *
   * This list is not complete, but has the benefit of being offline and is
   * therefore fetched eagerly to improve responsiveness.
   */
  private val databaseClaims = combine(
    inheritanceClaimsDao.pendingBeneficiaryClaims,
    inheritanceClaimsDao.pendingBenefactorClaims
  ) { beneficiaryClaims, benefactorClaims ->
    coroutineBinding {
      InheritanceClaims(
        benefactorClaims = benefactorClaims.bind(),
        beneficiaryClaims = beneficiaryClaims.bind()
      )
    }
  }

  /**
   * In-memory cache for claim data.
   *
   * This is the latest full set of claims from either the server or by
   * local modification.
   */
  private val claimsState = MutableStateFlow<Result<InheritanceClaims, Error>>(Ok(InheritanceClaims.EMPTY))

  /**
   * Flow of the latest available data about user claims.
   *
   * Data from this flow may be stale (only locally saved data) and will
   * continue to emit updates while observed.
   *
   * This flow will not emit until a value is available, either by local
   * or server updates, and does not emit a default empty value initially
   * before load.
   */
  private val claimsFlow = channelFlow {
    send(databaseClaims.first())

    coroutineScope {
      launch {
        claimsState
          .filterNotNull()
          .collectLatest { send(it) }
      }
    }
  }.shareIn(
    scope = stateScope,
    started = SharingStarted.WhileSubscribed(),
    replay = 1
  )

  override val claims: Flow<Result<InheritanceClaims, Error>> = claimsFlow.distinctUntilChanged()

  override suspend fun fetchClaims(): Result<InheritanceClaims, Error> {
    return claimsState.first()
      .onSuccess { newClaims ->
        claimsState.value = Ok(newClaims)
        inheritanceClaimsDao.setInheritanceClaims(newClaims)
      }
  }

  /**
   * Update the state of a single inheritance claim.
   */
  override suspend fun updateSingleClaim(claim: InheritanceClaim) {
    claimsState.value = when (claim) {
      is BeneficiaryClaim -> updateClaim(claim, { it.beneficiaryClaims }) { state, updatedClaims ->
        state.copy(beneficiaryClaims = updatedClaims)
      }
      is BenefactorClaim -> updateClaim(claim, { it.benefactorClaims }) { state, updatedClaims ->
        state.copy(benefactorClaims = updatedClaims)
      }
      else -> claimsState.value
    }
  }

  private inline fun <T : InheritanceClaim> updateClaim(
    claim: T,
    claimsSelector: (InheritanceClaims) -> List<T>,
    updateClaimsState: (InheritanceClaims, List<T>) -> InheritanceClaims,
  ): Result<InheritanceClaims, Error> {
    val currentClaim = claimsState.value
      .get()
      ?.let(claimsSelector)
      ?.find { it.claimId == claim.claimId }

    val updatedClaims = claimsSelector(
      claimsState.value.get() ?: InheritanceClaims.EMPTY
    ).let { claims ->
      if (currentClaim == null) {
        claims + claim
      } else {
        claims - currentClaim + claim
      }
    }

    return claimsState.value.map { updateClaimsState(it, updatedClaims) }
  }

  override suspend fun syncServerClaims() {
    val account = account.first()

    val newClaims = retrieveInheritanceClaimsF8eClient.retrieveInheritanceClaims(
      account.config.f8eEnvironment,
      account.accountId
    ).logFailure { "Failed to sync new claims" }

    if (newClaims.isOk || !claimsState.value.isOk) {
      claimsState.value = newClaims
    }

    newClaims.onSuccess {
      inheritanceClaimsDao.setInheritanceClaims(it)
        .logFailure { "Failed to save new claims to database" }
    }
  }
}
