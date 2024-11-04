package build.wallet.inheritance

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.inheritance.InheritanceKeyset
import build.wallet.bitkey.inheritance.InheritanceMaterial
import build.wallet.bitkey.inheritance.InheritanceMaterialHashData
import build.wallet.bitkey.inheritance.InheritanceMaterialPackage
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.relationships.RelationshipsCrypto
import build.wallet.serialization.json.encodeToStringResult
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

class InheritanceMaterialCreatorImpl(
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val relationships: InheritanceRelationshipsProvider,
  private val crypto: RelationshipsCrypto,
) : InheritanceMaterialCreator {
  override suspend fun getInheritanceMaterialHashData(
    keybox: Keybox,
  ): Result<InheritanceMaterialHashData, Error> {
    val contacts = relationships.getEndorsedInheritanceContacts()
      ?: return Err(Error("Inheritance Contacts unavailable."))
    return coroutineBinding {
      InheritanceMaterialHashData(
        networkType = keybox.config.bitcoinNetworkType,
        spendingKey = keybox.activeAppKeyBundle.spendingKey,
        contacts = contacts
      )
    }
  }

  override suspend fun createInheritanceMaterial(
    keybox: Keybox,
  ): Result<InheritanceMaterial, Error> {
    val contacts = relationships.getEndorsedInheritanceContacts()
      ?: return Err(Error("Inheritance Contacts unavailable."))

    return coroutineBinding {
      val pKMatOutput = createInheritanceKeyset(keybox)
        .mapError { Error("Error creating inheritance keyset", it) }
        .flatMap { Json.encodeToStringResult(it) }
        .map { it.encodeUtf8() }
        .flatMap { crypto.encryptPrivateKeyMaterial(it) }
        .bind()
      val packages = contacts.map {
        InheritanceMaterialPackage(
          relationshipId = RelationshipId(it.relationshipId),
          sealedDek = crypto.encryptPrivateKeyEncryptionKey(
            it.identityKey,
            pKMatOutput.privateKeyEncryptionKey
          ).bind(),
          sealedMobileKey = pKMatOutput.sealedPrivateKeyMaterial
        )
      }

      InheritanceMaterial(packages)
    }
  }

  private suspend fun createInheritanceKeyset(keybox: Keybox): Result<InheritanceKeyset, Error> =
    coroutineBinding {
      val publicSpendingKey = keybox.activeAppKeyBundle.spendingKey
      val privateSpendingKey = appPrivateKeyDao.getAppSpendingPrivateKey(publicSpendingKey)
        .toErrorIfNull { IllegalStateException("Active app spending private key not found.") }
        .mapError { Error("Error getting spending private key", it) }
        .bind()
      val network = keybox.config.bitcoinNetworkType

      InheritanceKeyset(
        network = network,
        appSpendingPublicKey = publicSpendingKey,
        appSpendingPrivateKey = privateSpendingKey
      )
    }
}
