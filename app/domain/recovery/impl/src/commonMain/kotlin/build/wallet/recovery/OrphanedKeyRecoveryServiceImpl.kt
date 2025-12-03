package build.wallet.recovery

import bitkey.account.AccountConfigService
import bitkey.account.FullAccountConfig
import bitkey.account.HardwareType
import bitkey.auth.AuthTokenScope
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AccountAuthenticator.AuthData
import build.wallet.auth.AuthTokensService
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.f8e.recovery.ListKeysetsF8eClient
import build.wallet.f8e.recovery.ListKeysetsResponse
import build.wallet.f8e.recovery.toSpendingKeysets
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.wallet.KeysetWalletProvider
import build.wallet.logging.logWarn
import build.wallet.platform.random.UuidGenerator
import build.wallet.recovery.OrphanedKeyRecoveryService.RecoverableAccount
import build.wallet.recovery.OrphanedKeyRecoveryService.RecoveryError
import build.wallet.recovery.OrphanedKeyRecoveryService.RecoveryError.*
import build.wallet.recovery.OrphanedKeysState.NoOrphanedKeys
import build.wallet.recovery.OrphanedKeysState.OrphanedKeysFound
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class OrphanedKeyRecoveryServiceImpl(
  private val orphanedKeyDetectionService: OrphanedKeyDetectionService,
  private val keychainParser: KeychainParser,
  private val accountAuthenticator: AccountAuthenticator,
  private val accountConfigService: AccountConfigService,
  private val listKeysetsF8eClient: ListKeysetsF8eClient,
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val keyboxDao: KeyboxDao,
  private val uuidGenerator: UuidGenerator,
  private val keysetWalletProvider: KeysetWalletProvider,
  private val authTokensService: AuthTokensService,
) : OrphanedKeyRecoveryService {
  private companion object {
    const val LOG_TAG = "[OrphanedKeyRecovery]"
  }

  /**
   * Result of successful authentication including the token scope that worked.
   */
  private sealed class AuthenticationResult {
    abstract val authData: AuthData
    abstract val tokenScope: AuthTokenScope

    data class Global(
      override val authData: AuthData,
      val authKey: PublicKey<AppGlobalAuthKey>,
    ) : AuthenticationResult() {
      override val tokenScope = AuthTokenScope.Global
    }

    data class Recovery(
      override val authData: AuthData,
      val authKey: PublicKey<AppRecoveryAuthKey>,
    ) : AuthenticationResult() {
      override val tokenScope = AuthTokenScope.Recovery
    }
  }

  /**
   * Holds auth keys for an account as we discover them.
   */
  private data class AccountAuthKeys(
    val globalAuthKey: PublicKey<AppGlobalAuthKey>? = null,
    val recoveryAuthKey: PublicKey<AppRecoveryAuthKey>? = null,
    val authData: AuthData? = null,
  )

  override suspend fun canAttemptRecovery(
    orphanedKeys: List<KeychainScanner.KeychainEntry>,
  ): Boolean {
    val parsedKeys = keychainParser.parse(orphanedKeys)

    val hasAuthKeyPair = parsedKeys.authKeys.any { authKey ->
      parsedKeys.authKeyPrivates.containsKey(authKey.value)
    }

    val hasCompleteSpendingKey = parsedKeys.spendingKeys.any { it.hasXprv && it.hasMnemonic }

    return hasAuthKeyPair && hasCompleteSpendingKey
  }

  override suspend fun discoverRecoverableAccounts(): Result<List<RecoverableAccount>, RecoveryError> =
    coroutineBinding {
      val orphanedKeys = when (val state = orphanedKeyDetectionService.orphanedKeysState().value) {
        is OrphanedKeysFound -> state.entries
        NoOrphanedKeys -> {
          Err(KeyReconstructionFailed("No orphaned keys available for recovery"))
            .bind<List<KeychainScanner.KeychainEntry>>()
        }
      }

      val parsedKeys = keychainParser.parse(orphanedKeys)

      // Authenticate all discovered auth keys
      val accountAuthKeys = authenticateAllKeys(parsedKeys)

      // Build recoverable accounts from discovered auth keys
      val discoveredButNotRecoverable = mutableListOf<String>()
      val recoverableAccounts = accountAuthKeys.mapNotNull { (accountId, authKeys) ->
        buildRecoverableAccount(
          accountId = accountId,
          authKeys = authKeys,
          parsedKeys = parsedKeys,
          orphanedKeys = orphanedKeys,
          discoveredButNotRecoverable = discoveredButNotRecoverable
        )
      }

      if (recoverableAccounts.isEmpty()) {
        if (discoveredButNotRecoverable.isNotEmpty()) {
          logWarn {
            "$LOG_TAG No recoverable accounts found: ${discoveredButNotRecoverable.joinToString(", ")}"
          }
        }
        Err(KeyReconstructionFailed("No recoverable accounts found with valid auth and spending keys"))
          .bind<List<RecoverableAccount>>()
      }

      logWarn { "$LOG_TAG Found ${recoverableAccounts.size} recoverable account(s)" }

      recoverableAccounts
    }

  /**
   * Authenticates all discovered auth keys and groups them by account ID.
   */
  private suspend fun authenticateAllKeys(
    parsedKeys: KeychainParser.ParsedKeychain,
  ): Map<String, AccountAuthKeys> {
    logWarn { "$LOG_TAG Starting authentication of ${parsedKeys.authKeys.size} discovered auth keys" }
    val accountAuthKeys = mutableMapOf<String, AccountAuthKeys>()

    parsedKeys.authKeys.forEach { authKeyPublic ->
      val authResult = tryAuthenticateKey(authKeyPublic, parsedKeys)
      if (authResult != null) {
        val accountId = authResult.authData.accountId
        logWarn {
          "$LOG_TAG Successfully authenticated key for account: $accountId with scope: ${authResult.tokenScope}"
        }
        val existing = accountAuthKeys[accountId] ?: AccountAuthKeys()

        when (authResult) {
          is AuthenticationResult.Global -> {
            accountAuthKeys[accountId] = existing.copy(
              globalAuthKey = authResult.authKey,
              authData = authResult.authData
            )
          }
          is AuthenticationResult.Recovery -> {
            accountAuthKeys[accountId] = existing.copy(
              recoveryAuthKey = authResult.authKey,
              authData = authResult.authData
            )
          }
        }
      }
    }

    logWarn {
      "$LOG_TAG Authentication complete. Found ${accountAuthKeys.size} account(s) with auth keys"
    }
    return accountAuthKeys
  }

  /**
   * Builds a recoverable account if all required components are present.
   */
  private suspend fun buildRecoverableAccount(
    accountId: String,
    authKeys: AccountAuthKeys,
    parsedKeys: KeychainParser.ParsedKeychain,
    orphanedKeys: List<KeychainScanner.KeychainEntry>,
    discoveredButNotRecoverable: MutableList<String>,
  ): RecoverableAccount? {
    logWarn { "$LOG_TAG Starting to build recoverable account for: $accountId" }

    // Validate we have both auth keys
    if (!validateAuthKeys(authKeys, discoveredButNotRecoverable)) {
      return null
    }

    val hasGlobal = authKeys.globalAuthKey != null
    val hasRecovery = authKeys.recoveryAuthKey != null
    logWarn { "$LOG_TAG Found auth keys - Global: $hasGlobal, Recovery: $hasRecovery" }

    val keysetResponse = fetchKeysets(authKeys.authData!!)
    if (keysetResponse == null) {
      logWarn { "$LOG_TAG Failed to fetch keysets for account: $accountId" }
      discoveredButNotRecoverable.add("Account (keyset fetch failed)")
      return null
    }
    logWarn { "$LOG_TAG Successfully fetched ${keysetResponse.keysets.size} keysets" }

    val allKeysets = keysetResponse.keysets.toSpendingKeysets(uuidGenerator)

    // Find matching keysets with spending keys in keychain
    val matchingKeysets = allKeysets.filter { keyset ->
      parsedKeys.spendingKeys.any { spendingKey ->
        spendingKey.dpub == keyset.appKey.key.dpub &&
          spendingKey.hasXprv &&
          spendingKey.hasMnemonic
      }
    }

    if (matchingKeysets.isEmpty()) {
      discoveredButNotRecoverable.add("Account (no matching spending keys)")
      return null
    }

    val sortedKeysets = matchingKeysets.sortedByDescending { it.f8eSpendingKeyset.keysetId }
    // get the first (newest) keyset
    val selectedKeyset = sortedKeysets.first()

    // Try to fetch balance for the selected keyset
    val balance = tryFetchBalance(selectedKeyset)

    // Order matching keysets with selected one first
    val orderedMatchingKeysets = listOf(selectedKeyset) + matchingKeysets.filter {
      it != selectedKeyset
    }

    return RecoverableAccount(
      accountId = FullAccountId(accountId),
      globalAuthKey = authKeys.globalAuthKey!!,
      recoveryAuthKey = authKeys.recoveryAuthKey!!,
      matchingKeysets = orderedMatchingKeysets,
      allKeysets = allKeysets,
      balance = balance,
      f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment,
      sourceKeychainEntries = orphanedKeys
    )
  }

  /**
   * Validates that we have both required auth keys.
   */
  private fun validateAuthKeys(
    authKeys: AccountAuthKeys,
    discoveredButNotRecoverable: MutableList<String>,
  ): Boolean {
    if (authKeys.authData == null || authKeys.globalAuthKey == null || authKeys.recoveryAuthKey == null) {
      if (authKeys.authData != null) {
        // We authenticated but missing one of the auth keys
        val missingKey = when {
          authKeys.globalAuthKey == null && authKeys.recoveryAuthKey == null -> "both auth keys"
          authKeys.globalAuthKey == null -> "global auth key"
          else -> "recovery auth key"
        }
        discoveredButNotRecoverable.add("Account (missing $missingKey)")
      }
      return false
    }
    return true
  }

  private suspend fun tryAuthenticateKey(
    authKeyPublic: PublicKey<*>,
    parsedKeys: KeychainParser.ParsedKeychain,
  ): AuthenticationResult? {
    val privateKey = parsedKeys.authKeyPrivates[authKeyPublic.value] ?: return null
    return tryAuthenticationWithFallback(authKeyPublic, privateKey)
  }

  /**
   * Tries authentication with both Global and Recovery scopes to determine the auth key type.
   * Returns the auth result with the successful scope, or null if both fail.
   */
  private suspend fun tryAuthenticationWithFallback(
    authKeyPublic: PublicKey<*>,
    privateKey: PrivateKey<out AppAuthKey>,
  ): AuthenticationResult? {
    // First try as Global auth key
    val globalAuthKey = PublicKey<AppGlobalAuthKey>(authKeyPublic.value)
    val globalPrivateKey = PrivateKey<AppGlobalAuthKey>(privateKey.bytes)
    val globalAuthData = authenticateWithAuthKey(
      authKey = globalAuthKey,
      privateKey = globalPrivateKey,
      tokenScope = AuthTokenScope.Global
    )

    if (globalAuthData != null) {
      return AuthenticationResult.Global(
        authData = globalAuthData,
        authKey = globalAuthKey
      )
    }

    // If Global fails, try as Recovery auth key
    val recoveryAuthKey = PublicKey<AppRecoveryAuthKey>(authKeyPublic.value)
    val recoveryPrivateKey = PrivateKey<AppRecoveryAuthKey>(privateKey.bytes)
    val recoveryAuthData = authenticateWithAuthKey(
      authKey = recoveryAuthKey,
      privateKey = recoveryPrivateKey,
      tokenScope = AuthTokenScope.Recovery
    )

    if (recoveryAuthData != null) {
      return AuthenticationResult.Recovery(
        authData = recoveryAuthData,
        authKey = recoveryAuthKey
      )
    }

    return null
  }

  private suspend fun <T : AppAuthKey> authenticateWithAuthKey(
    authKey: PublicKey<T>,
    privateKey: PrivateKey<T>,
    tokenScope: AuthTokenScope,
  ): AuthData? {
    appPrivateKeyDao.storeAsymmetricPrivateKey(authKey, privateKey)
      .onFailure {
        logWarn { "$LOG_TAG Failed to store private key for auth key" }
        return null
      }

    val authData = accountAuthenticator.appAuth(
      appAuthPublicKey = authKey,
      authTokenScope = tokenScope
    ).onFailure { error ->
      logWarn { "$LOG_TAG Authentication failed for scope $tokenScope: ${error.message}" }
    }.get() ?: return null

    authTokensService.setTokens(
      accountId = FullAccountId(authData.accountId),
      tokens = authData.authTokens,
      scope = tokenScope
    ).onFailure { error ->
      logWarn { "$LOG_TAG Failed to store auth tokens for scope $tokenScope: ${error.message}" }
    }

    logWarn { "$LOG_TAG Successfully authenticated and stored tokens for scope $tokenScope" }

    return authData
  }

  private suspend fun fetchKeysets(authData: AuthData): ListKeysetsResponse? {
    val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment

    logWarn { "$LOG_TAG Fetching keysets from F8e" }

    return listKeysetsF8eClient.listKeysets(
      f8eEnvironment = f8eEnvironment,
      fullAccountId = FullAccountId(authData.accountId)
    ).onFailure { error ->
      logWarn { "$LOG_TAG Failed to fetch keysets: ${error.message}" }
    }.get()
  }

  @Suppress("TooGenericExceptionCaught")
  private suspend fun tryFetchBalance(keyset: SpendingKeyset): BitcoinBalance? =
    keysetWalletProvider.getWatchingWallet(keyset)
      .get()
      ?.let { wallet ->
        try {
          wallet.sync()
          wallet.balance().first()
        } catch (e: Exception) {
          // Balance fetching is best-effort for orphaned key recovery display
          logWarn {
            "$LOG_TAG Failed to fetch balance for keyset ${keyset.f8eSpendingKeyset.keysetId}: ${e::class.simpleName}"
          }
          null
        }
      }

  override suspend fun recoverFromRecoverableAccount(
    account: RecoverableAccount,
  ): Result<Keybox, RecoveryError> =
    coroutineBinding {
      val existingKeybox = keyboxDao.getActiveOrOnboardingKeybox()
        .mapError { KeyReconstructionFailed("Failed to check for existing keybox: ${it.message}") }
        .bind()
      if (existingKeybox != null) {
        Err(KeyboxAlreadyExists).bind<Unit>()
      }

      val parsedKeys = keychainParser.parse(account.sourceKeychainEntries)

      // Verify we have both auth keys and their private keys
      val hasGlobalPrivateKey = account.globalAuthKey?.let {
        parsedKeys.authKeyPrivates.containsKey(it.value)
      } ?: false

      val hasRecoveryPrivateKey = account.recoveryAuthKey?.let {
        parsedKeys.authKeyPrivates.containsKey(it.value)
      } ?: false

      if (!hasGlobalPrivateKey || !hasRecoveryPrivateKey) {
        val missingKey = when {
          !hasGlobalPrivateKey && !hasRecoveryPrivateKey -> "Both auth private keys"
          !hasGlobalPrivateKey -> "Global auth private key"
          else -> "Recovery auth private key"
        }
        Err(KeyReconstructionFailed("$missingKey not found in keychain"))
          .bind<Unit>()
      }

      val keyset = account.matchingKeysets.firstOrNull()
        ?: Err(KeyReconstructionFailed("No keysets available in recoverable account")).bind()

      val spendingKeyInfo =
        parsedKeys.spendingKeys.find { it.dpub == keyset.appKey.key.dpub }
          ?: Err(KeyReconstructionFailed("No spending key matches keyset")).bind()

      if (!spendingKeyInfo.hasXprv || !spendingKeyInfo.hasMnemonic) {
        Err(KeyReconstructionFailed("Incomplete spending key data - need both xprv and mnemonic"))
          .bind<Unit>()
      }

      // Both auth keys are required for recovery
      val globalAuthKey = account.globalAuthKey
        ?: Err(KeyReconstructionFailed("Missing global auth key for recovery")).bind()

      val recoveryAuthKey = account.recoveryAuthKey
        ?: Err(KeyReconstructionFailed("Missing recovery auth key for recovery")).bind()

      val appKeyBundle = AppKeyBundle(
        localId = uuidGenerator.random(),
        spendingKey = keyset.appKey,
        authKey = globalAuthKey,
        networkType = keyset.networkType,
        recoveryAuthKey = recoveryAuthKey
      )

      val hwKeyBundle =
        HwKeyBundle(
          localId = uuidGenerator.random(),
          spendingKey = keyset.hardwareKey,
          authKey = HwAuthPublicKey(Secp256k1PublicKey("")),
          networkType = keyset.networkType
        )

      val config =
        FullAccountConfig(
          bitcoinNetworkType = keyset.networkType,
          f8eEnvironment = account.f8eEnvironment,
          isHardwareFake = false,
          isTestAccount = false,
          isUsingSocRecFakes = false,
          hardwareType = HardwareType.W1
        )

      val keybox = Keybox(
        localId = uuidGenerator.random(),
        fullAccountId = account.accountId,
        config = config,
        activeSpendingKeyset = keyset,
        activeAppKeyBundle = appKeyBundle,
        activeHwKeyBundle = hwKeyBundle,
        appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
          AppGlobalAuthKeyHwSignature.ORPHANED_KEY_RECOVERY_SENTINEL
        ),
        keysets = account.allKeysets,
        canUseKeyboxKeysets = true
      )

      logWarn { "$LOG_TAG Orphaned key recovery successful" }

      keybox
    }
}
