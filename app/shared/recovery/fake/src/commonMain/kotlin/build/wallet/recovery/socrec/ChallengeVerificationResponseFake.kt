package build.wallet.recovery.socrec

import build.wallet.bitkey.relationships.PakeCode
import build.wallet.f8e.socrec.models.ChallengeVerificationResponse
import build.wallet.relationships.RelationshipsCryptoFake
import com.github.michaelbull.result.getOrThrow
import io.ktor.utils.io.core.toByteArray
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

private val cryptoFake = RelationshipsCryptoFake()
private val pake = PakeCode("12345678901".toByteArray().toByteString())
private val delegatedDecryptionKey = cryptoFake.generateDelegatedDecryptionKey().getOrThrow()

// Protected Customer creates encrypted backup
private val privateKeyMaterial =
  "wsh(sortedmulti(2," +
    "[9d3902ae/84'/1'/0']" +
    "tpubDCU8xtEiG4DZ8J5qNsGrCNWzm4WzPBPM2nKTAiZqZfA6m2GMcva1n" +
    "GRLsiLKpLktmuJrdWg9XKxpcSd9uafyPSLfACwToyvk43XQVs8SH6P/*," +
    "[ff2d9449/84'/1'/0']" + "" +
    "tpubDGm8VUmd9iJKkfFhJCVTVfJx5ezF4iiwr5MrpjfaWNtot46fq2L5v" +
    "skeHLrccYKhBFfQ1BReoxwPRHaoUVAouFTTWyzqLVv3or8EBVHzFp5/*," +
    "[56a09b24/84'/1'/0']" +
    "tpubDDe5r54a9Ajy7dF8w16WCWegTJgGXceZNyBw2vkRczvwm1ZcRgiUE" +
    "8RUX7uHgExeNtbhrKVsQN4Eb24sWRrwoLDUmdxSeM4a3kgQrJr5m7P/*" +
    "))"
private val encryptedPrivateKeyMaterialOutput =
  cryptoFake.encryptPrivateKeyMaterial(privateKeyMaterial.encodeUtf8())
    .getOrThrow()
private val privateKeyEncryptionKey = encryptedPrivateKeyMaterialOutput.privateKeyEncryptionKey
private val sealedPrivateKeyEncryptionKey =
  cryptoFake.encryptPrivateKeyEncryptionKey(
    delegatedDecryptionKey.publicKey,
    privateKeyEncryptionKey
  ).getOrThrow()

val ChallengeVerificationResponseFake =
  ChallengeVerificationResponse(
    socialChallengeId = "social-challenge-id",
    protectedCustomerRecoveryPakePubkey = cryptoFake.generateProtectedCustomerRecoveryPakeKey(pake).getOrThrow().publicKey,
    sealedDek = sealedPrivateKeyEncryptionKey.value
  )
