package build.wallet.encrypt

import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import okio.ByteString.Companion.encodeUtf8

class SignatureVerifierImplTests : FunSpec({

  val signatureVerifier = SignatureVerifierImpl()
  val secp256k1KeyGenerator = Secp256k1KeyGeneratorImpl()
  val messageSigner = MessageSignerImpl()

  test("verifyEcdsa") {
    val keyPair = secp256k1KeyGenerator.generateKeypair()
    val message = "Hello world!".encodeUtf8()
    val signature = messageSigner.signResult(message, keyPair.privateKey).getOrThrow()
    signatureVerifier.verifyEcdsaResult(message, signature, keyPair.publicKey).getOrThrow() shouldBeEqual true
    val invalidMessage = "Helloworld!".encodeUtf8()
    signatureVerifier.verifyEcdsaResult(invalidMessage, signature, keyPair.publicKey).shouldBeErrOfType<SignatureVerifierError>()
    val invalidSignature = "malformed signature"
    signatureVerifier.verifyEcdsaResult(message, invalidSignature, keyPair.publicKey).shouldBeErrOfType<SignatureVerifierError>()
  }
})
