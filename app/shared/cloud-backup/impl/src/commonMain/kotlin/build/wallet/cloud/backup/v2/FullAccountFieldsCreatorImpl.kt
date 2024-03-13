package build.wallet.cloud.backup.v2

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.cloud.backup.appGlobalAuthKeypair
import build.wallet.cloud.backup.appKeys
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.v2.FullAccountFieldsCreator.FullAccountFieldsCreationError
import build.wallet.cloud.backup.v2.FullAccountFieldsCreator.FullAccountFieldsCreationError.AppAuthPrivateKeyRetrievalError
import build.wallet.cloud.backup.v2.FullAccountFieldsCreator.FullAccountFieldsCreationError.AppSpendingPrivateKeyRetrievalError
import build.wallet.cloud.backup.v2.FullAccountFieldsCreator.FullAccountFieldsCreationError.KeysInfoEncodingError
import build.wallet.cloud.backup.v2.FullAccountFieldsCreator.FullAccountFieldsCreationError.PkekRetrievalError
import build.wallet.encrypt.SymmetricKeyEncryptor
import build.wallet.recovery.socrec.SocRecCrypto
import build.wallet.serialization.json.encodeToStringResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

class FullAccountFieldsCreatorImpl(
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val csekDao: CsekDao,
  private val symmetricKeyEncryptor: SymmetricKeyEncryptor,
  private val socRecCrypto: SocRecCrypto,
) : FullAccountFieldsCreator {
  override suspend fun create(
    keybox: Keybox,
    sealedCsek: SealedCsek,
    trustedContacts: List<TrustedContact>,
  ): Result<FullAccountFields, FullAccountFieldsCreationError> =
    binding {
      val appAuthKeypair =
        keybox.appGlobalAuthKeypair(appPrivateKeyDao)
          .mapError {
            AppAuthPrivateKeyRetrievalError(it)
          }
          .bind()

      val appPrivateKeysMap =
        keybox.appKeys(appPrivateKeyDao)
          .mapError {
            AppSpendingPrivateKeyRetrievalError(it)
          }.bind()

      val fullAccountKeys =
        FullAccountKeys(
          activeSpendingKeyset = keybox.activeSpendingKeyset,
          activeHwSpendingKey = keybox.activeHwKeyBundle.spendingKey,
          activeHwAuthKey = keybox.activeHwKeyBundle.authKey,
          inactiveSpendingKeysets = keybox.inactiveKeysets,
          appGlobalAuthKeypair = appAuthKeypair,
          appSpendingKeys = appPrivateKeysMap,
          rotationAppGlobalAuthKeypair = null
        )

      val fullCustomerKeysInfoEncoded =
        Json
          .encodeToStringResult(fullAccountKeys)
          .mapError { KeysInfoEncodingError(it) }
          .bind()

      val csek =
        csekDao
          .get(sealedCsek)
          .mapError { PkekRetrievalError(it) }
          .toErrorIfNull { PkekRetrievalError() }
          .bind()

      val fullCustomerKeysInfoEncodedHardwareEncrypted =
        symmetricKeyEncryptor
          .seal(fullCustomerKeysInfoEncoded.encodeUtf8(), csek.key)

      val socRecPKMatOutput =
        socRecCrypto.encryptPrivateKeyMaterial(fullCustomerKeysInfoEncoded.encodeUtf8())
          .mapError { FullAccountFieldsCreationError.SocRecEncryptionError(it) }
          .bind()

      val socRecRelationshipsMap =
        trustedContacts.associate {
          it.recoveryRelationshipId to
            socRecCrypto
              .encryptPrivateKeyEncryptionKey(
                it.identityKey,
                socRecPKMatOutput.privateKeyEncryptionKey
              )
              .mapError { err -> FullAccountFieldsCreationError.SocRecEncryptionError(err) }
              .bind()
        }

      FullAccountFields(
        sealedHwEncryptionKey = sealedCsek,
        socRecSealedDekMap = socRecRelationshipsMap,
        isFakeHardware = keybox.config.isHardwareFake,
        hwFullAccountKeysCiphertext = fullCustomerKeysInfoEncodedHardwareEncrypted,
        socRecSealedFullAccountKeys = socRecPKMatOutput.sealedPrivateKeyMaterial,
        rotationAppRecoveryAuthKeypair = null,
        appGlobalAuthKeyHwSignature = keybox.appGlobalAuthKeyHwSignature
      )
    }
}
