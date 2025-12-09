package build.wallet.bdk.legacy

import build.wallet.bdk.bindings.*
import kotlinx.datetime.Instant
import org.bitcoindevkit.*
import org.bitcoindevkit.AddressIndex.LastUnused as FfiLastUnused
import org.bitcoindevkit.AddressIndex.New as FfiNew
import org.bitcoindevkit.AddressIndex.Peek as FfiPeek

/**
 * Convert BDK type to FFI type.
 */
internal val BdkAddressIndex.ffiAddressIndex: FfiAddressIndex
  get() =
    when (this) {
      BdkAddressIndex.New -> FfiNew
      BdkAddressIndex.LastUnused -> FfiLastUnused
      is BdkAddressIndex.Peek -> FfiPeek(index)
    }

/**
 * Convert FFI type to BDK type.
 */
internal val FfiAddressInfo.bdkAddressInfo: BdkAddressInfo
  get() =
    BdkAddressInfo(
      index = index.toLong(),
      address = BdkAddressImpl(ffiAddress = address)
    )

/**
 * Convert FFI type to KMP type.
 */
internal val FfiBlockTime.bdkBlockTime: BdkBlockTime
  get() =
    BdkBlockTime(
      height = height.toLong(),
      timestamp = Instant.fromEpochSeconds(timestamp.toLong())
    )

/**
 * Convert FFI type to KMP type.
 */
internal val FfiTransactionDetails.bdkTransactionDetails: BdkTransactionDetails
  get() =
    BdkTransactionDetails(
      transaction = transaction?.let { BdkTransactionImpl(it) },
      fee = fee,
      received = received,
      sent = sent,
      txid = txid,
      confirmationTime = confirmationTime?.bdkBlockTime
    )

/**
 * Convert FFI Network to KMP type.
 */
internal val FfiNetwork.bdkNetwork: BdkNetwork
  get() =
    when (this) {
      FfiNetwork.BITCOIN -> BdkNetwork.BITCOIN
      FfiNetwork.TESTNET -> BdkNetwork.TESTNET
      FfiNetwork.SIGNET -> BdkNetwork.SIGNET
      FfiNetwork.REGTEST -> BdkNetwork.SIGNET
    }

/**
 * Convert KMP type to FFI type.
 */
internal val BdkNetwork.ffiNetwork: FfiNetwork
  get() =
    when (this) {
      BdkNetwork.BITCOIN -> FfiNetwork.BITCOIN
      BdkNetwork.TESTNET -> FfiNetwork.TESTNET
      BdkNetwork.SIGNET -> FfiNetwork.SIGNET
      BdkNetwork.REGTEST -> FfiNetwork.REGTEST
    }

internal val BdkKeychainKind.ffiKeychainKind: FfiKeychainKind
  get() =
    when (this) {
      BdkKeychainKind.EXTERNAL -> FfiKeychainKind.EXTERNAL
      BdkKeychainKind.INTERNAL -> FfiKeychainKind.INTERNAL
    }

/**
 * Convert KMP type to FFI type.
 */
internal val BdkElectrumConfig.ffiElectrumConfig: FfiElectrumConfig
  get() =
    FfiElectrumConfig(
      url = url,
      socks5 = socks5,
      retry = retry.toUByte(),
      timeout = timeout?.toUByte(),
      stopGap = stopGap.toULong(),
      validateDomain = validateDomain
    )

/**
 * Convert KMP type to FFI type.
 */
internal val BdkBlockchainConfig.ffiBlockchainConfig: FfiBlockchainConfig
  get() =
    when (this) {
      is BdkBlockchainConfig.Electrum -> {
        FfiBlockchainConfigElectrum(config = config.ffiElectrumConfig)
      }
    }

/**
 * Convert KMP type to FFI type.
 */
internal val BdkDatabaseConfig.ffiDatabaseConfig: FfiDatabaseConfig
  get() =
    when (this) {
      BdkDatabaseConfig.Memory -> FfiDatabaseConfigMemory
      is BdkDatabaseConfig.Sqlite ->
        FfiDatabaseConfigSqlite(
          config = FfiSqliteDbConfiguration(path = config.path)
        )
    }

/**
 * Convert KMP type to FFI type.
 */
internal val BdkProgress.ffiProgress: FfiProgress
  get() =
    object : FfiProgress {
      override fun update(
        progress: Float,
        message: String?,
      ) {
        this@ffiProgress.update(progress, message)
      }
    }

/**
 * Convert KMP type to FFI type.
 */
internal val BdkMnemonicWordCount.ffiWordCount: FfiWordCount
  get() =
    when (this) {
      BdkMnemonicWordCount.WORDS_24 -> FfiWordCount.WORDS24
    }

/**
 * Convert KMP type to FFI type.
 */
internal val FfiBalance.bdkBalance: BdkBalance
  get() =
    BdkBalance(
      immature = immature,
      trustedPending = trustedPending,
      untrustedPending = untrustedPending,
      confirmed = confirmed,
      spendable = spendable,
      total = total
    )

/**
 * Convert FFI [TxIn] to a KMP [BdkTxIn] type
 */
internal val TxIn.bdkTxIn: BdkTxIn
  get() =
    BdkTxIn(
      sequence = sequence,
      witness = witness,
      outpoint = previousOutput.bdkOutPoint
    )

/**
 * Convert FFI [TxOut] to a KMP [BdkTxOut] type
 */
internal val TxOut.bdkTxOut: BdkTxOut
  get() =
    BdkTxOut(
      value = value,
      scriptPubkey = BdkScriptImpl(rawOutputScript = scriptPubkey.toBytes())
    )

/**
 * Convert KMP type to FFI type.
 */
internal val BdkScript.ffiScript: FfiScript
  get() = FfiScript(rawOutputScript = rawOutputScript)

/**
 * Convert FFI [LocalUtxo] to KMP [BdkUtxo] type
 */
internal val LocalUtxo.bdkUtxo: BdkUtxo
  get() = BdkUtxo(
    outPoint = outpoint.bdkOutPoint,
    txOut = txout.bdkTxOut,
    isSpent = isSpent
  )

/**
 * Convert FFI [OutPoint] to KMP [BdkOutPoint] type
 */
internal val OutPoint.bdkOutPoint: BdkOutPoint
  get() = BdkOutPoint(
    txid = txid,
    vout = vout
  )

/**
 * Convert FFI Transaction to KMP type.
 */
internal val Transaction.bdkTransaction: BdkTransaction
  get() =
    BdkTransactionImpl(ffiTransaction = this)
