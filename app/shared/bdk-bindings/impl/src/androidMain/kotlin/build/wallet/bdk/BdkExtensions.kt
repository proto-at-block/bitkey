package build.wallet.bdk

import build.wallet.bdk.bindings.BdkAddressIndex
import build.wallet.bdk.bindings.BdkAddressInfo
import build.wallet.bdk.bindings.BdkBalance
import build.wallet.bdk.bindings.BdkBlockTime
import build.wallet.bdk.bindings.BdkBlockchainConfig
import build.wallet.bdk.bindings.BdkDatabaseConfig
import build.wallet.bdk.bindings.BdkElectrumConfig
import build.wallet.bdk.bindings.BdkKeychainKind
import build.wallet.bdk.bindings.BdkMnemonicWordCount
import build.wallet.bdk.bindings.BdkNetwork
import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bdk.bindings.BdkProgress
import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkTransactionDetails
import build.wallet.bdk.bindings.BdkTxIn
import build.wallet.bdk.bindings.BdkTxOut
import build.wallet.bdk.bindings.BdkUtxo
import kotlinx.datetime.Instant
import org.bitcoindevkit.AddressIndex.LastUnused
import org.bitcoindevkit.AddressIndex.New
import org.bitcoindevkit.LocalUtxo
import org.bitcoindevkit.OutPoint
import org.bitcoindevkit.TxIn
import org.bitcoindevkit.TxOut

/**
 * Convert BDK type to FFI type.
 */
internal val BdkAddressIndex.ffiAddressIndex: FfiAddressIndex
  get() =
    when (this) {
      BdkAddressIndex.NEW -> New
      BdkAddressIndex.LAST_UNUSED -> LastUnused
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
      scriptPubkey = BdkScriptImpl(scriptPubkey)
    )

/**
 * Convert KMP type to FFI type.
 */
internal val BdkScript.ffiScript: FfiScript
  get() = FfiScript(rawOutputScript = rawOutputScript())

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
