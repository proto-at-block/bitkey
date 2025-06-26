package build.wallet.emergencyexitkit

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.emergencyaccesskit.v1.*
import build.wallet.emergencyaccesskit.v1.BitcoinNetworkType.*
import build.wallet.emergencyaccesskit.v1.Wildcard.*
import build.wallet.emergencyexitkit.EmergencyExitKitBackup.EmergencyExitKitBackupV1
import build.wallet.emergencyexitkit.EmergencyExitKitPayload.EmergencyExitKitPayloadV1
import build.wallet.emergencyexitkit.EmergencyExitKitPayloadDecoder.DecodeError
import build.wallet.emergencyexitkit.EmergencyExitKitPayloadDecoder.DecodeError.*
import build.wallet.encrypt.SealedData
import build.wallet.ensureNotNull
import build.wallet.logging.logFailure
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString

@BitkeyInject(AppScope::class)
class EmergencyExitKitPayloadDecoderImpl : EmergencyExitKitPayloadDecoder {
  override suspend fun encode(payload: EmergencyExitKitPayload): String {
    when (payload) {
      is EmergencyExitKitPayloadV1 ->
        return payload
          .toProto().encode()
          .toByteString().toByteArray()
          .encodeToBase58String()
    }
  }

  override suspend fun decode(
    encodedString: String,
  ): Result<EmergencyExitKitPayload, DecodeError> {
    return coroutineBinding {
      val data = catchingResult {
        // The EEK payload in the PDF has line breaks to allow it to wrap appropriately.
        // Filter for only valid base58 characters to ensure that it can be parsed.
        val filter = Regex("[^123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]")
        filter.replace(encodedString, "").decodeBase58()
      }
        .mapError { InvalidBase58Data(cause = it) }
        .bind()

      val payload = catchingResult { Payload.ADAPTER.decode(data) }
        .mapError { InvalidProtoData(cause = it) }
        .toErrorIfNull { InvalidProtoData() }
        .bind()

      payload.backup_v1
        .toResultOr { InvalidBackupVersion }
        .flatMap { it.toEmergencyExitKitPayload() }
        .bind()
    }.logFailure { "Emergency Exit Kit decrypted payload failed to decode" }
  }

  override suspend fun encodeBackup(backupV1: EmergencyExitKitBackup): ByteString {
    return withContext(Dispatchers.Default) {
      when (backupV1) {
        is EmergencyExitKitBackupV1 -> backupV1.toProto().encode().toByteString()
      }
    }
  }

  override suspend fun decodeDecryptedBackup(
    keysetData: ByteString,
  ): Result<EmergencyExitKitBackupV1, DecodeError> {
    return withContext(Dispatchers.Default) {
      coroutineBinding {
        val keyset = catchingResult { ActiveSpendingKeysetV1.ADAPTER.decode(keysetData) }
          .mapError { InvalidProtoData(it) }
          .bind()

        ensureNotNull(keyset) { InvalidProtoData() }

        keyset.toEmergencyExitKitBackupV1().bind()
      }
    }
  }
}

/** Marshal an [EmergencyExitKitPayload] to protobuf */
private fun EmergencyExitKitPayloadV1.toProto() =
  Payload(
    backup_v1 =
      BackupV1(
        hw_encryption_key_ciphertext = this.sealedHwEncryptionKey,
        sealed_active_spending_keyset = this.sealedActiveSpendingKeys.toProto()
      )
  )

private fun SealedData.toProto(): build.wallet.emergencyaccesskit.v1.SealedData =
  build.wallet.emergencyaccesskit.v1.SealedData(
    ciphertext = this.ciphertext,
    nonce = this.nonce,
    tag = this.tag
  )

private fun BitcoinNetworkType.toProto(): build.wallet.emergencyaccesskit.v1.BitcoinNetworkType {
  return when (this) {
    BitcoinNetworkType.BITCOIN -> BITCOIN_NETWORK_TYPE_BITCOIN
    BitcoinNetworkType.SIGNET -> BITCOIN_NETWORK_TYPE_SIGNET
    BitcoinNetworkType.TESTNET -> BITCOIN_NETWORK_TYPE_TESTNET
    BitcoinNetworkType.REGTEST -> BITCOIN_NETWORK_TYPE_REGTEST
  }
}

/**
 * Marshal a [BackupV1] protobuf to an [EmergencyExitKitPayload]
 * @return [DecodeError.InvalidProtoData] if any fields are missing
 */
fun BackupV1.toEmergencyExitKitPayload(): Result<EmergencyExitKitPayload, DecodeError> {
  val proto = this
  return binding {
    EmergencyExitKitPayloadV1(
      sealedHwEncryptionKey =
        proto.hw_encryption_key_ciphertext
          .toResultOr { InvalidProtoData() }
          .bind(),
      sealedActiveSpendingKeys =
        proto.sealed_active_spending_keyset
          .toResultOr { InvalidProtoData() }
          .flatMap { it.toSealedData() }
          .bind()
    )
  }
}

private fun build.wallet.emergencyaccesskit.v1.SealedData.toSealedData():
  Result<SealedData, DecodeError> {
  val proto = this
  return binding {
    SealedData(
      ciphertext = proto.ciphertext.toResultOr { InvalidProtoData() }.bind(),
      nonce = proto.nonce.toResultOr { InvalidProtoData() }.bind(),
      tag = proto.tag.toResultOr { InvalidProtoData() }.bind()
    )
  }
}

private fun build.wallet.emergencyaccesskit.v1.BitcoinNetworkType.toBitcoinNetworkType():
  Result<BitcoinNetworkType, DecodeError> {
  return when (this) {
    BITCOIN_NETWORK_TYPE_UNSPECIFIED -> Err(InvalidProtoData())
    BITCOIN_NETWORK_TYPE_BITCOIN -> Ok(BitcoinNetworkType.BITCOIN)
    BITCOIN_NETWORK_TYPE_SIGNET -> Ok(BitcoinNetworkType.SIGNET)
    BITCOIN_NETWORK_TYPE_TESTNET -> Ok(BitcoinNetworkType.TESTNET)
    BITCOIN_NETWORK_TYPE_REGTEST -> Ok(BitcoinNetworkType.REGTEST)
  }
}

/** Marshal an [EmergencyExitKitBackupV1] to protobuf */
private fun EmergencyExitKitBackupV1.toProto() =
  ActiveSpendingKeysetV1(
    local_id = this.spendingKeyset.localId,
    bitcoin_network_type = this.spendingKeyset.networkType.toProto(),
    app_key =
      AppSpendingKey(
        key = this.spendingKeyset.appKey.toProto(),
        xprv = this.appSpendingKeyXprv.key.xprv
      ),
    hardware_key = this.spendingKeyset.hardwareKey.toProto(),
    f8e_key = this.spendingKeyset.f8eSpendingKeyset.spendingPublicKey.toProto()
  )

/** Marshal a [SpendingPublicKey] to protobuf */
private fun build.wallet.bitkey.spending.SpendingPublicKey.toProto() =
  SpendingPublicKey(
    origin =
      Origin(
        fingerprint = this.key.origin.fingerprint,
        derivation_path = this.key.origin.derivationPath
      ),
    xpub = this.key.xpub,
    derivation_path = this.key.derivationPath,
    wildcard =
      when (this.key.wildcard) {
        DescriptorPublicKey.Wildcard.None -> WILDCARD_NONE
        DescriptorPublicKey.Wildcard.Unhardened -> WILDCARD_UNHARDENED
        DescriptorPublicKey.Wildcard.Hardened -> WILDCARD_HARDENED
      }
  )

/**
 * Marshal an [ActiveSpendingKeysetV1] to an [EmergencyExitKitBackupV1]
 * @return a [DecodeError.InvalidProtoData] if any fields are missing
 */
fun ActiveSpendingKeysetV1.toEmergencyExitKitBackupV1():
  Result<EmergencyExitKitBackupV1, DecodeError> {
  val proto = this
  return binding {
    val appSpendingKey = proto.app_key.toResultOr { InvalidProtoData() }.bind()

    EmergencyExitKitBackupV1(
      spendingKeyset =
        SpendingKeyset(
          localId = proto.local_id.toResultOr { InvalidProtoData() }.bind(),
          networkType =
            proto.bitcoin_network_type
              .toResultOr { InvalidProtoData() }
              .flatMap { it.toBitcoinNetworkType() }
              .bind(),
          appKey =
            AppSpendingPublicKey(
              key =
                appSpendingKey.key
                  .toResultOr { InvalidProtoData() }
                  .flatMap { it.toDescriptorPublicKey() }
                  .bind()
            ),
          hardwareKey =
            HwSpendingPublicKey(
              key =
                proto.hardware_key
                  .toResultOr { InvalidProtoData() }
                  .flatMap { it.toDescriptorPublicKey() }
                  .bind()
            ),
          f8eSpendingKeyset =
            F8eSpendingKeyset(
              keysetId = "FAKE_KEYSET_ID",
              spendingPublicKey =
                F8eSpendingPublicKey(
                  key =
                    proto.f8e_key
                      .toResultOr { InvalidProtoData() }
                      .flatMap { it.toDescriptorPublicKey() }
                      .bind()
                )
            )
        ),
      appSpendingKeyXprv = AppSpendingPrivateKey(
        key = ExtendedPrivateKey(
          xprv = appSpendingKey.xprv.toResultOr { InvalidProtoData() }.bind(),
          mnemonic = "MNEMONIC REMOVED DURING EMERGENCY ACCESS"
        )
      )
    )
  }
}

private fun SpendingPublicKey.toDescriptorPublicKey(): Result<DescriptorPublicKey, DecodeError> {
  val proto = this
  return binding {
    val origin = proto.origin.toResultOr { InvalidProtoData() }.bind()
    DescriptorPublicKey(
      origin =
        DescriptorPublicKey.Origin(
          fingerprint = origin.fingerprint.toResultOr { InvalidProtoData() }.bind(),
          derivationPath = origin.derivation_path.toResultOr { InvalidProtoData() }.bind()
        ),
      xpub = proto.xpub.toResultOr { InvalidProtoData() }.bind(),
      derivationPath = proto.derivation_path.toResultOr { InvalidProtoData() }.bind(),
      wildcard =
        when (proto.wildcard.toResultOr { InvalidProtoData() }.bind()) {
          WILDCARD_UNSPECIFIED -> Err(InvalidProtoData())
          WILDCARD_NONE -> Ok(DescriptorPublicKey.Wildcard.None)
          WILDCARD_UNHARDENED -> Ok(DescriptorPublicKey.Wildcard.Unhardened)
          WILDCARD_HARDENED -> Ok(DescriptorPublicKey.Wildcard.Hardened)
        }.bind()
    )
  }
}
