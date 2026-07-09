package build.wallet.wallet.migration

import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingKeyProof
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.cloud.backup.csek.Sek
import build.wallet.crypto.SealedData
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.recovery.SignedKeysetVerificationResponse

/**
 * Represents the current state of a wallet migration flow.
 *
 * Each state contains the data needed to perform that step and provides a [next] function
 * to transition to the following state. Credentials (proof of possession, sealed SSEK) are
 * passed through the flow via these transitions.
 *
 * Use [MigrationService.resume] to get the current state and [MigrationService.proceed]
 * to advance to the next state.
 */
sealed interface MigrationProgress {
  val type: MigrationType

  fun isInProgress(): Boolean {
    return when (this) {
      is Completed -> false
      is NotStarted -> resumedFromCloudBackup
      else -> true
    }
  }

  // Shared ordered pipeline. Migration types differ by which steps are no-ops
  // and which steps require additional authorization data.

  /**
   * Migration has not started yet.
   *
   * For W3 upgrades, [resumedFromCloudBackup] marks the placeholder state used after cloud
   * restore when the app must route back into the upgrade flow before any new keyset exists.
   */
  data class NotStarted(
    override val type: MigrationType,
    val resumedFromCloudBackup: Boolean = false,
  ) : MigrationProgress {
    /**
     * Convenience factory for starting a **private-wallet migration** with the required
     * credentials. Returns [CreateNewKeyset.PrivateWalletMigration] directly.
     *
     * W3 upgrades do not use this factory — they construct [CreateNewKeyset.W3Upgrade]
     * directly because they carry additional device-identity fields
     * ([CreateNewKeyset.W3Upgrade.oldDeviceSerial], etc.) that are not part of [NotStarted].
     *
     * @param currentKeybox The current keybox being migrated from
     * @param newHwSpendingKey The newly generated hardware spending key for the migration
     * @param hwProofOfPossession Proof of possession for the hardware key
     * @param ssek Unsealed storage encryption key for descriptor encryption
     * @param sealedSsek Hardware-sealed SSEK for descriptor encryption
     */
    fun next(
      currentKeybox: Keybox,
      newHwSpendingKey: HwSpendingPublicKey,
      hwProofOfPossession: HwFactorProofOfPossession,
      ssek: Sek,
      sealedSsek: SealedSsek,
      sealedCsek: SealedCsek? = null,
      newHwSpendingKeyProof: HwSpendingKeyProof? = null,
    ): CreateNewKeyset.PrivateWalletMigration {
      return CreateNewKeyset.PrivateWalletMigration(
        currentKeybox = currentKeybox,
        newHwSpendingKey = newHwSpendingKey,
        hwProofOfPossession = hwProofOfPossession,
        ssek = ssek,
        sealedSsek = sealedSsek,
        sealedCsek = sealedCsek,
        newHwSpendingKeyProof = newHwSpendingKeyProof
      )
    }
  }

  /** Creating a new keyset with app, hardware, and server keys. */
  sealed interface CreateNewKeyset : MigrationProgress {
    val currentKeybox: Keybox
    val newHwSpendingKey: HwSpendingPublicKey

    /**
     * Optional hardware-attested proof for [newHwSpendingKey]. Populated when the
     * NFC tap that derived the spending key also returned an attestation signature
     * + cert chain. Persisted incrementally via [PrivateWalletMigrationDao]/[W3UpgradeDao]
     * before [createKeyset]; on resume [MigrationServiceImpl] prefers the persisted
     * value over the in-memory one. Server tolerates `null` until the enrollment
     * gate flips on.
     */
    val newHwSpendingKeyProof: HwSpendingKeyProof?
      get() = null
    val hwProofOfPossession: HwFactorProofOfPossession
    val ssek: Sek
    val sealedSsek: SealedSsek
    val sealedCsek: SealedCsek?
    val resumedFromCloudBackup: Boolean
      get() = false

    fun next(
      newKeyset: SpendingKeyset,
      updatedKeybox: Keybox,
      sealedSsekForDecryption: SealedSsek? = null,
    ): AuthKeyRotation {
      return AuthKeyRotation(
        type = type,
        currentKeybox = updatedKeybox,
        newKeyset = newKeyset,
        hwProofOfPossession = hwProofOfPossession,
        ssek = ssek,
        sealedSsek = sealedSsek,
        sealedCsek = sealedCsek,
        resumedFromCloudBackup = resumedFromCloudBackup,
        sealedSsekForDecryption = sealedSsekForDecryption
      )
    }

    data class PrivateWalletMigration(
      override val currentKeybox: Keybox,
      override val newHwSpendingKey: HwSpendingPublicKey,
      override val hwProofOfPossession: HwFactorProofOfPossession,
      override val ssek: Sek,
      override val sealedSsek: SealedSsek,
      override val sealedCsek: SealedCsek? = null,
      override val newHwSpendingKeyProof: HwSpendingKeyProof? = null,
    ) : CreateNewKeyset {
      override val type: MigrationType = MigrationType.PrivateWalletMigration
    }

    data class W3Upgrade(
      val oldDeviceSerial: String,
      val oldHardwareFingerprint: String,
      val newDeviceSerial: String,
      override val currentKeybox: Keybox,
      override val newHwSpendingKey: HwSpendingPublicKey,
      override val hwProofOfPossession: HwFactorProofOfPossession,
      override val ssek: Sek,
      override val sealedSsek: SealedSsek,
      override val sealedCsek: SealedCsek? = null,
      override val resumedFromCloudBackup: Boolean = false,
      override val newHwSpendingKeyProof: HwSpendingKeyProof? = null,
    ) : CreateNewKeyset {
      override val type: MigrationType = MigrationType.W3Upgrade
    }
  }

  /**
   * Rotating authentication keys for the account.
   *
   * Some migration types treat this as a no-op. For W3 upgrades, the hardware device has been
   * replaced, so the recovery app auth key and hardware auth key must be rotated on the server
   * and locally. The global app auth key stays the same, but the new W3 must still sign it.
   * The caller (UI) must:
   * 1. Generate a new recovery app auth key while keeping the current global app auth key
   * 2. Tap old W1 hardware to get proof of possession (W1 PoP) to authorize auth rotation
   * 3. Tap new W3 hardware to sign account ID and the current app global auth key
   * 4. Call [withProof] and [withRotationData], then [MigrationService.proceed]
   *    to rotate auth keys from W1 to W3
   */
  data class AuthKeyRotation(
    override val type: MigrationType,
    val currentKeybox: Keybox,
    val newKeyset: SpendingKeyset,
    val hwProofOfPossession: HwFactorProofOfPossession? = null,
    val ssek: Sek? = null,
    val sealedSsek: SealedSsek? = null,
    val sealedCsek: SealedCsek? = null,
    val resumedFromCloudBackup: Boolean = false,
    val sealedSsekForDecryption: SealedSsek? = null,
    val newAppAuthKeys: AppAuthPublicKeys? = null,
    val hwAuthPublicKey: HwAuthPublicKey? = null,
    val hwSignedAccountId: String? = null,
    /** Proof authorizing auth key rotation. */
    val proof: PrivilegedActionProof? = null,
  ) : MigrationProgress {
    /**
     * Attach proof and public-key data needed to authorize auth rotation.
     */
    fun withProof(
      newAppAuthKeys: AppAuthPublicKeys,
      proof: PrivilegedActionProof,
    ): AuthKeyRotation =
      copy(
        newAppAuthKeys = newAppAuthKeys,
        proof = proof
      )

    /**
     * Attach app auth key material before the hardware proof has been collected.
     *
     * Used in resumed-from-cloud upgrade flows where auth keys are generated or recovered
     * before the hardware tap that produces the proof. The proof is attached later via
     * [withProof].
     */
    fun withAppAuthKeys(newAppAuthKeys: AppAuthPublicKeys): AuthKeyRotation =
      copy(newAppAuthKeys = newAppAuthKeys)

    /**
     * Attach signed data needed to finish auth rotation.
     */
    fun withRotationData(
      hwSignedAccountId: String,
      hwAuthPublicKey: HwAuthPublicKey,
      appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ): AuthKeyRotation =
      copy(
        hwAuthPublicKey = hwAuthPublicKey,
        hwSignedAccountId = hwSignedAccountId,
        newAppAuthKeys = newAppAuthKeys?.copy(
          appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature
        )
      )

    fun next(currentKeybox: Keybox): DescriptorBackup {
      return DescriptorBackup(
        type = type,
        currentKeybox = currentKeybox,
        newKeyset = newKeyset,
        hwProofOfPossession = hwProofOfPossession,
        ssek = ssek,
        sealedSsek = sealedSsek,
        sealedCsek = sealedCsek,
        resumedFromCloudBackup = resumedFromCloudBackup,
        sealedSsekForDecryption = sealedSsekForDecryption
      )
    }
  }

  /**
   * Backing up wallet descriptors to the server.
   *
   * Some migration types can use the proof-of-possession captured at the start of migration.
   * W3 upgrades collect a new action proof after auth rotation and attach it via [withProof].
   */
  data class DescriptorBackup(
    override val type: MigrationType,
    val currentKeybox: Keybox,
    val newKeyset: SpendingKeyset,
    val hwProofOfPossession: HwFactorProofOfPossession? = null,
    val ssek: Sek? = null,
    val sealedSsek: SealedSsek? = null,
    val sealedCsek: SealedCsek? = null,
    val resumedFromCloudBackup: Boolean = false,
    val sealedSsekForDecryption: SealedSsek? = null,
    val proof: PrivilegedActionProof? = null,
  ) : MigrationProgress {
    fun withProof(proof: PrivilegedActionProof): DescriptorBackup = copy(proof = proof)

    /**
     * Advance to the [ServerKeysetActivation] step.
     *
     * [currentKeybox] defaults to this state's keybox, but callers may pass an updated keybox
     * when a resumed-from-cloud W3 upgrade repairs the keybox with recovered historical keysets.
     */
    fun next(currentKeybox: Keybox = this.currentKeybox): ServerKeysetActivation {
      return ServerKeysetActivation(
        type,
        currentKeybox,
        newKeyset,
        hwProofOfPossession,
        sealedCsek = sealedCsek
      )
    }
  }

  /**
   * Activating the new keyset on the server.
   *
   * Some migration types can use the proof-of-possession captured at the start of migration.
   * W3 upgrades collect a dedicated action proof and receive signed verification data that must
   * be provisioned to hardware before the migration can continue.
   */
  data class ServerKeysetActivation(
    override val type: MigrationType,
    val currentKeybox: Keybox,
    val newKeyset: SpendingKeyset,
    val hwProofOfPossession: HwFactorProofOfPossession? = null,
    val proof: PrivilegedActionProof? = null,
    val sealedCsek: SealedCsek? = null,
  ) : MigrationProgress {
    fun withProof(proof: PrivilegedActionProof): ServerKeysetActivation = copy(proof = proof)

    fun next(signedKeysResponse: SignedKeysetVerificationResponse? = null): MigrationProgress {
      return when {
        type == MigrationType.W3Upgrade ->
          HardwareDescriptorProvisioning(
            type = type,
            currentKeybox = currentKeybox,
            newKeyset = newKeyset,
            signedKeysResponse = signedKeysResponse
              ?: error("W3 upgrade requires signed keyset verification data."),
            sealedCsek = sealedCsek
          )
        type.requiresNewDdk -> DdkBackup(type, currentKeybox, newKeyset, sealedCsek = sealedCsek)
        else -> CloudBackup(type, currentKeybox, newKeyset, sealedCsek = sealedCsek)
      }
    }
  }

  /**
   * Provisioning the signed keyset verification data to W3 hardware after activation.
   */
  data class HardwareDescriptorProvisioning(
    override val type: MigrationType,
    val currentKeybox: Keybox,
    val newKeyset: SpendingKeyset,
    val signedKeysResponse: SignedKeysetVerificationResponse,
    val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature? = null,
    val sealedCsek: SealedCsek? = null,
  ) : MigrationProgress {
    fun withSignature(
      appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ): HardwareDescriptorProvisioning =
      copy(appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature)

    fun next(currentKeybox: Keybox): MigrationProgress {
      return when {
        type.requiresNewDdk -> DdkBackup(type, currentKeybox, newKeyset, sealedCsek = sealedCsek)
        else -> CloudBackup(type, currentKeybox, newKeyset, sealedCsek = sealedCsek)
      }
    }
  }

  /**
   * Re-sealing the DDK (Delegated Decryption Key) with the new hardware and uploading
   * it to the server. This is required when the hardware device changes (e.g., W3 upgrade)
   * because the DDK was previously sealed by the old device.
   *
   * The caller (UI) must perform the NFC seal via [SealDelegatedDecryptionKey] and then
   * pass the sealed data to [proceed] by constructing this state with [sealedDdkData].
   */
  data class DdkBackup(
    override val type: MigrationType,
    val currentKeybox: Keybox,
    val newKeyset: SpendingKeyset,
    val sealedDdkData: SealedData? = null,
    val sealedCsek: SealedCsek? = null,
  ) : MigrationProgress {
    fun withSealedData(sealedData: SealedData): DdkBackup {
      return copy(sealedDdkData = sealedData)
    }

    fun next(): CloudBackup {
      return CloudBackup(type, currentKeybox, newKeyset, sealedCsek = sealedCsek)
    }
  }

  /** Creating and uploading cloud backup. */
  data class CloudBackup(
    override val type: MigrationType,
    val currentKeybox: Keybox,
    val newKeyset: SpendingKeyset,
    val sealedCsek: SealedCsek? = null,
  ) : MigrationProgress {
    fun next(): MigrationProgress {
      return when {
        type.requiresServerSweep -> StartDelayNotify(type, currentKeybox, newKeyset)
        else -> LocalKeyboxActivation(type, currentKeybox, newKeyset)
      }
    }
  }

  /** Request a Delay/Notify for server sweeps */
  data class StartDelayNotify(
    override val type: MigrationType,
    val currentKeybox: Keybox,
    val newKeyset: SpendingKeyset,
  ) : MigrationProgress {
    fun next(): LocalKeyboxActivation {
      return LocalKeyboxActivation(type, currentKeybox, newKeyset)
    }
  }

  /** Sweeping funds from old wallet to new wallet. */
  data class Sweep(
    override val type: MigrationType,
    val currentKeybox: Keybox,
    val newKeyset: SpendingKeyset,
  ) : MigrationProgress {
    fun next(): LocalKeyboxActivation {
      return LocalKeyboxActivation(type, currentKeybox, newKeyset)
    }
  }

  /** Activating the new keybox locally as the active keybox. */
  data class LocalKeyboxActivation(
    override val type: MigrationType,
    val currentKeybox: Keybox,
    val newKeyset: SpendingKeyset,
  ) : MigrationProgress {
    fun next(): MigrationProgress = Completed(type)
  }

  /** Migration completed successfully. */
  data class Completed(
    override val type: MigrationType,
  ) : MigrationProgress
}
