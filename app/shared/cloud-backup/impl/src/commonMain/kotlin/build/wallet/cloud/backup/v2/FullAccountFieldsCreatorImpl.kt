package build.wallet.cloud.backup.v2

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.cloud.backup.appGlobalAuthKeypair
import build.wallet.cloud.backup.appKeys
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.v2.FullAccountFieldsCreator.FullAccountFieldsCreationError
import build.wallet.cloud.backup.v2.FullAccountFieldsCreator.FullAccountFieldsCreationError.*
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SymmetricKeyEncryptor
import build.wallet.relationships.RelationshipsCrypto
import build.wallet.serialization.json.encodeToStringResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

@BitkeyInject(AppScope::class)
class FullAccountFieldsCreatorImpl(
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val csekDao: CsekDao,
  private val symmetricKeyEncryptor: SymmetricKeyEncryptor,
  private val relationshipsCrypto: RelationshipsCrypto,
) : FullAccountFieldsCreator {
  override suspend fun create(
    keybox: Keybox,
    sealedCsek: SealedCsek,
    endorsedTrustedContacts: List<EndorsedTrustedContact>,
  ): Result<FullAccountFields, FullAccountFieldsCreationError> =
    coroutineBinding {
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
        relationshipsCrypto.encryptPrivateKeyMaterial(fullCustomerKeysInfoEncoded.encodeUtf8())
          .mapError { FullAccountFieldsCreationError.SocRecEncryptionError(it) }
          .bind()

      val socRecRelationshipsMap =
        endorsedTrustedContacts.associate {
          it.relationshipId to
            relationshipsCrypto
              .encryptPrivateKeyEncryptionKey(
                it.identityKey,
                socRecPKMatOutput.privateKeyEncryptionKey
              )
              .mapError {
                  err ->
                FullAccountFieldsCreationError.SocRecEncryptionError(err)
              }
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
