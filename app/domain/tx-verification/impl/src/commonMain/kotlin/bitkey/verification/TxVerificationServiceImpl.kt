package bitkey.verification

import bitkey.f8e.verify.TxVerificationF8eClient
import bitkey.f8e.verify.TxVerifyPolicyF8eClient
import bitkey.privilegedactions.AuthorizationStrategy
import bitkey.privilegedactions.AuthorizationStrategyType
import bitkey.privilegedactions.PrivilegedActionInstance
import bitkey.privilegedactions.PrivilegedActionType
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.account.FullAccount
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.PendingPrivilegedActionsEntity
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import build.wallet.mapResult
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.display.BitcoinDisplayPreferenceRepository
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.exchange.ExchangeRate
import build.wallet.sqldelight.asFlowOfOneOrNull
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.recover
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

@BitkeyInject(AppScope::class)
class TxVerificationServiceImpl(
  private val txVerificationDao: TxVerificationDao,
  private val policyClient: TxVerifyPolicyF8eClient,
  private val verificationClient: TxVerificationF8eClient,
  private val accountService: AccountService,
  private val currencyConverter: CurrencyConverter,
  private val bitcoinDisplayPreferenceRepository: BitcoinDisplayPreferenceRepository,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val bitkeyDatabaseProvider: BitkeyDatabaseProvider,
) : TxVerificationService {
  override fun getCurrentThreshold(): Flow<Result<VerificationThreshold?, Error>> {
    return flow {
      txVerificationDao.getActivePolicy()
        .mapResult { it?.threshold }
        .collect(::emit)
    }
  }

  override fun getPendingPolicy(): Flow<Result<TxVerificationPolicy.Pending?, Error>> {
    return flow {
      bitkeyDatabaseProvider.database()
        .pendingPrivilegedActionsQueries
        .getPendingActionByType(PrivilegedActionType.LOOSEN_TRANSACTION_VERIFICATION_POLICY)
        .asFlowOfOneOrNull()
        .distinctUntilChanged()
        .mapResult { pendingAction ->
          pendingAction?.let { action ->
            TxVerificationPolicy.Pending(
              authorization = action.toPrivilegedActionInstance()
            )
          }
        }
        .collect(::emit)
    }
  }

  override suspend fun updateThreshold(
    txVerificationPolicy: TxVerificationPolicy.Active,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error> {
    return coroutineBinding {
      val account = accountService.getAccount<FullAccount>()
        .logFailure { "Update Threshold cannot be called without full account." }
        .bind()
      val apiResult = policyClient.setPolicy(
        fullAccountId = account.accountId,
        f8eEnvironment = account.config.f8eEnvironment,
        policy = txVerificationPolicy,
        hwFactorProofOfPossession = hwFactorProofOfPossession
      ).bind()

      when (apiResult) {
        is TxVerificationPolicy.Active -> {
          logInfo { "Verification policy updated to ${apiResult.threshold}" }
          txVerificationDao.setActivePolicy(apiResult).bind()
        }
        is TxVerificationPolicy.Pending -> {
          bitkeyDatabaseProvider.database()
            .pendingPrivilegedActionsQueries
            .insertPendingAction(
              id = apiResult.authorization.id,
              type = apiResult.authorization.privilegedActionType,
              strategy = AuthorizationStrategyType.OUT_OF_BAND
            )
        }
        TxVerificationPolicy.Disabled -> {
          txVerificationDao.deletePolicy().bind()
        }
      }
    }
  }

  override suspend fun isVerificationRequired(
    amount: BitcoinMoney,
    exchangeRates: List<ExchangeRate>?,
  ): Boolean {
    // If we fail to get a policy, we default to disabled. If this is incorrect, the
    // server will reject the request to sign a grant, and the user will be prompted to verify.
    val threshold = txVerificationDao.getActivePolicy().first()
      .logFailure { "Failed getting policy for verification check. Will default to disabled." }
      .get()
      ?.threshold

    return when (threshold) {
      VerificationThreshold.Always -> true
      else -> {
        if (threshold == null) {
          logInfo { "No active verification threshold set. Verification is not required." }
          return false
        }
        val btcThreshold: BitcoinMoney = when (val thresholdAmount = threshold.amount) {
          is BitcoinMoney -> thresholdAmount
          is FiatMoney -> currencyConverter.convert(
            fromAmount = thresholdAmount,
            toCurrency = BTC,
            rates = exchangeRates ?: return false // If no exchange rate is available, defer to server.
          ) as BitcoinMoney
        }

        btcThreshold <= amount
      }
    }
  }

  override suspend fun requestVerification(
    psbt: Psbt,
  ): Result<ConfirmationFlow<TxVerificationApproval>, Throwable> {
    return coroutineBinding {
      val account = accountService.getAccount<FullAccount>()
        .logFailure { "Request Verification cannot be called without full account." }
        .bind()
      val btcPreference = bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value
      val fiatPreference = fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value

      val createResponse = verificationClient.createVerificationRequest(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId,
        psbt = psbt,
        fiatCurrency = fiatPreference,
        bitcoinDisplayUnit = btcPreference,
        keysetId = account.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
      ).logFailure { "Failed to create verification request" }.bind()

      pollForConfirmation(
        onCancel = {
          // Attempt to cancel verification with server:
          coroutineBinding {
            verificationClient.cancelVerification(
              f8eEnvironment = account.config.f8eEnvironment,
              fullAccountId = account.accountId,
              verificationId = createResponse.id
            )
              .logFailure { "Failed to cancel verification request" }
              .recover { Unit } // Ignore errors, let verification expire.
              .bind()
          }
        },
        operation = {
          verificationClient.getVerificationStatus(
            f8eEnvironment = account.config.f8eEnvironment,
            fullAccountId = account.accountId,
            verificationId = createResponse.id
          ).map { status ->
            when (status) {
              is TxVerificationState.Success -> ConfirmationState.Confirmed(status.hardwareGrant)
              is TxVerificationState.Expired -> ConfirmationState.Expired
              is TxVerificationState.Failed -> ConfirmationState.Rejected
              is TxVerificationState.Pending -> ConfirmationState.Pending
            }
          }.logFailure { "Unexpected error polling verification status. Ignoring" }
            .getOrElse { ConfirmationState.Pending }
        }
      )
    }
  }

  override suspend fun requestGrant(psbt: Psbt): Result<TxVerificationApproval, Throwable> {
    return coroutineBinding {
      val account = accountService.getAccount<FullAccount>()
        .logFailure { "Update Threshold cannot be called without full account." }
        .bind()
      val btcPreference = bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value
      val fiatPreference = fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value

      verificationClient.requestGrant(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId,
        psbt = psbt,
        fiatCurrency = fiatPreference,
        bitcoinDisplayUnit = btcPreference,
        keysetId = account.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
      ).bind()
    }
  }
}

private fun PendingPrivilegedActionsEntity.toPrivilegedActionInstance(): PrivilegedActionInstance {
  return PrivilegedActionInstance(
    id = id,
    privilegedActionType = type,
    authorizationStrategy = AuthorizationStrategy.OutOfBand(
      authorizationStrategyType = strategy
    )
  )
}
