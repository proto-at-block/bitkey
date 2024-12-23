package build.wallet.integration.statemachine.recovery.socrec.challenge

import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_FAILED
import build.wallet.auth.AppAuthKeyMessageSignerImpl
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.VERIFIED
import build.wallet.bitkey.relationships.TrustedContactKeyCertificateFake
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.bitkey.socrec.SocialChallengeResponse
import build.wallet.cloud.backup.v2.FullAccountKeys
import build.wallet.cloud.backup.v2.FullAccountKeysMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.crypto.Spake2Impl
import build.wallet.encrypt.*
import build.wallet.f8e.relationships.RelationshipsF8eClientFake
import build.wallet.f8e.socrec.SocRecF8eClientFake
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.recovery.socrec.toActions
import build.wallet.relationships.RelationshipsCryptoImpl
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeUiProps
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.ui.model.list.ListItemAccessory
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.http.*
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

class RecoveryChallengeUiStateMachineFunctionalTests : FunSpec({
  lateinit var app: AppTester
  val onExitCalls = turbines.create<Unit>("exit-recovery-flow")
  val onRecoveryCalls = turbines.create<FullAccountKeys>("recovery-key-recovered")
  val relationshipIdToPkekMap: MutableMap<String, XCiphertext> = mutableMapOf()
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val messageSigner = MessageSignerImpl()
  val relationshipsCrypto = RelationshipsCryptoImpl(
    symmetricKeyGenerator = SymmetricKeyGeneratorImpl(),
    xChaCha20Poly1305 = XChaCha20Poly1305Impl(),
    xNonceGenerator = XNonceGeneratorImpl(),
    spake2 = Spake2Impl(),
    appAuthKeyMessageSigner = AppAuthKeyMessageSignerImpl(appPrivateKeyDao, messageSigner),
    signatureVerifier = SignatureVerifierImpl(),
    cryptoBox = CryptoBoxImpl()
  )
  val delegatedDecryptionKey = relationshipsCrypto.generateDelegatedDecryptionKey().getOrThrow()
  val endorsedTrustedContact =
    EndorsedTrustedContact(
      relationshipId = "someRelationshipId",
      trustedContactAlias = TrustedContactAlias("someContact"),
      authenticationState = VERIFIED,
      keyCertificate = TrustedContactKeyCertificateFake.copy(delegatedDecryptionKey.publicKey),
      roles = setOf(TrustedContactRole.SocialRecoveryContact)
    )
  val pkMat = FullAccountKeysMock
  val (privateKeyEncryptionKey, sealedPrivateKeyMaterial) =
    relationshipsCrypto.encryptPrivateKeyMaterial(
      Json.encodeToString(pkMat).encodeUtf8()
    ).getOrThrow()

  lateinit var relationshipsF8eClientFake: RelationshipsF8eClientFake
  lateinit var socRecF8eClientFake: SocRecF8eClientFake

  beforeAny {
    app = launchNewApp(isUsingSocRecFakes = true)
    relationshipsF8eClientFake =
      (app.relationshipsF8eClientProvider.get() as RelationshipsF8eClientFake)
        .apply {
          reset()
          endorsedTrustedContacts.add(endorsedTrustedContact)
        }
    socRecF8eClientFake = app.socRecF8eClientProvider.get() as SocRecF8eClientFake
    app.socRecStartedChallengeDao.clear()
    relationshipIdToPkekMap[endorsedTrustedContact.relationshipId] =
      relationshipsCrypto.encryptPrivateKeyEncryptionKey(
        endorsedTrustedContact.identityKey,
        privateKeyEncryptionKey
      ).getOrThrow()
  }

  test("Start Challenge") {
    val account = app.onboardFullAccountWithFakeHardware()
    app.recoveryChallengeUiStateMachine.test(
      props = RecoveryChallengeUiProps(
        accountId = account.accountId,
        f8eEnvironment = account.config.f8eEnvironment,
        actions = app.socRecChallengeRepository.toActions(
          accountId = account.accountId,
          f8eEnvironment = account.config.f8eEnvironment,
          isUsingSocRecFakes = true
        ),
        relationshipIdToSocRecPkekMap = relationshipIdToPkekMap,
        sealedPrivateKeyMaterial = sealedPrivateKeyMaterial,
        endorsedTrustedContacts = relationshipsF8eClientFake.endorsedTrustedContacts.toImmutableList(),
        onExit = { onExitCalls.add(Unit) },
        onKeyRecovered = { onRecoveryCalls.add(it) }
      ),
      useVirtualTime = false
    ) {
      awaitUntilScreenWithBody<LoadingSuccessBodyModel> {
        id.shouldBe(SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_STARTING)
        message.shouldNotBeNull().shouldBe("Starting Recovery...")
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        onBack.shouldNotBeNull().invoke()
        onExitCalls.awaitItem()
      }
      awaitUntilScreenWithBody<FormBodyModel>(
        NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS
      )
        .clickPrimaryButton()
      awaitUntilScreenWithBody<FormBodyModel> {
        id.shouldBe(
          SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_TRUSTED_CONTACTS_LIST
        )
        mainContentList.size.shouldBe(1)
        mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items
          .size.shouldBe(1)

        mainContentList
          .first()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items
          .first().should {
            it.sideText.shouldBeNull()
            it.trailingAccessory.shouldNotBeNull()
          }

        onBack.shouldNotBeNull().invoke()
        onExitCalls.awaitItem()
        primaryButton.shouldNotBeNull()
          .text.shouldBe("Waiting for your Trusted Contact to verify you…")
      }
    }
  }

  test("Start Challenge Failed") {
    val account = app.onboardFullAccountWithFakeHardware()
    socRecF8eClientFake.fakeNetworkingError =
      HttpError.ServerError(HttpResponseMock(HttpStatusCode.InternalServerError))
    app.recoveryChallengeUiStateMachine.test(
      props = RecoveryChallengeUiProps(
        accountId = account.accountId,
        f8eEnvironment = account.config.f8eEnvironment,
        actions = app.socRecChallengeRepository.toActions(
          accountId = account.accountId,
          f8eEnvironment = account.config.f8eEnvironment,
          isUsingSocRecFakes = true
        ),
        relationshipIdToSocRecPkekMap = relationshipIdToPkekMap,
        sealedPrivateKeyMaterial = sealedPrivateKeyMaterial,
        endorsedTrustedContacts = relationshipsF8eClientFake.endorsedTrustedContacts.toImmutableList(),
        onExit = { onExitCalls.add(Unit) },
        onKeyRecovered = { onRecoveryCalls.add(it) }
      ),
      useVirtualTime = false
    ) {
      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(
        SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_STARTING
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilScreenWithBody<FormBodyModel>(RECOVERY_CHALLENGE_FAILED).clickPrimaryButton()
      onExitCalls.awaitItem()
    }
  }

  test("TC Verification Code") {
    val account = app.onboardFullAccountWithFakeHardware()
    app.recoveryChallengeUiStateMachine.test(
      props = RecoveryChallengeUiProps(
        accountId = account.accountId,
        f8eEnvironment = account.config.f8eEnvironment,
        actions = app.socRecChallengeRepository.toActions(
          accountId = account.accountId,
          f8eEnvironment = account.config.f8eEnvironment,
          isUsingSocRecFakes = true
        ),
        relationshipIdToSocRecPkekMap = relationshipIdToPkekMap,
        sealedPrivateKeyMaterial = sealedPrivateKeyMaterial,
        endorsedTrustedContacts = relationshipsF8eClientFake.endorsedTrustedContacts.toImmutableList(),
        onExit = { onExitCalls.add(Unit) },
        onKeyRecovered = { onRecoveryCalls.add(it) }
      ),
      useVirtualTime = false
    ) {
      awaitUntilScreenWithBody<LoadingSuccessBodyModel> {
        id.shouldBe(SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_STARTING)
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilScreenWithBody<FormBodyModel>(
        NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS
      )
        .clickPrimaryButton()
      awaitUntilScreenWithBody<FormBodyModel> {
        id.shouldBe(
          SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_TRUSTED_CONTACTS_LIST
        )
        mainContentList.size.shouldBe(1)
        mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items
          .first()
          .trailingAccessory
          .shouldBeTypeOf<ListItemAccessory.ButtonAccessory>()
          .model
          .onClick()
      }
      awaitUntilScreenWithBody<FormBodyModel> {
        id.shouldBe(
          SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_TC_VERIFICATION_CODE
        )
        val challenge = socRecF8eClientFake.challenges
          .shouldHaveSize(1)
          .first()
        val challengeAuth =
          app.socRecStartedChallengeAuthenticationDao.getAll()
            .getOrThrow()
            .single()
        val code = app.relationshipsCodeBuilder.buildRecoveryCode(
          challenge.response.counter,
          challengeAuth.pakeCode
        ).getOrThrow()
        mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items
          .first()
          .title.replace("-", "").shouldBeEqual(code)
        onBack.shouldNotBeNull().invoke()
      }
      awaitUntilScreenWithBody<FormBodyModel> {
        id.shouldBe(
          SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_TRUSTED_CONTACTS_LIST
        )
      }
    }
  }

  test("Resume Challenge") {
    val account = app.onboardFullAccountWithFakeHardware()
    var challengeId: String? = null
    app.recoveryChallengeUiStateMachine.test(
      props = RecoveryChallengeUiProps(
        accountId = account.accountId,
        f8eEnvironment = account.config.f8eEnvironment,
        actions = app.socRecChallengeRepository.toActions(
          accountId = account.accountId,
          f8eEnvironment = account.config.f8eEnvironment,
          isUsingSocRecFakes = true
        ),
        relationshipIdToSocRecPkekMap = relationshipIdToPkekMap,
        sealedPrivateKeyMaterial = sealedPrivateKeyMaterial,
        endorsedTrustedContacts = relationshipsF8eClientFake.endorsedTrustedContacts.toImmutableList(),
        onExit = { onExitCalls.add(Unit) },
        onKeyRecovered = { onRecoveryCalls.add(it) }
      ),
      useVirtualTime = false
    ) {
      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(
        SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_STARTING
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilScreenWithBody<FormBodyModel>(
        NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS
      )
      challengeId = socRecF8eClientFake.challenges.single().response.challengeId
    }
    app.recoveryChallengeUiStateMachine.test(
      props = RecoveryChallengeUiProps(
        accountId = account.accountId,
        f8eEnvironment = account.config.f8eEnvironment,
        actions = app.socRecChallengeRepository.toActions(
          accountId = account.accountId,
          f8eEnvironment = account.config.f8eEnvironment,
          isUsingSocRecFakes = true
        ),
        relationshipIdToSocRecPkekMap = relationshipIdToPkekMap,
        sealedPrivateKeyMaterial = sealedPrivateKeyMaterial,
        endorsedTrustedContacts = relationshipsF8eClientFake.endorsedTrustedContacts.toImmutableList(),
        onExit = { onExitCalls.add(Unit) },
        onKeyRecovered = { onRecoveryCalls.add(it) }
      ),
      useVirtualTime = false
    ) {
      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(
        SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_STARTING
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilScreenWithBody<FormBodyModel>(
        NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS
      )
      socRecF8eClientFake.challenges.single().response.challengeId.shouldBe(challengeId)
    }
  }

  test("Complete Challenge") {
    val account = app.onboardFullAccountWithFakeHardware()

    suspend fun simulateRespondToChallenge() {
      val recoveryAuth =
        app.socRecStartedChallengeAuthenticationDao.getByRelationshipId(
          recoveryRelationshipId = endorsedTrustedContact.relationshipId
        ).getOrThrow().shouldNotBeNull()
      val decryptOutput = relationshipsCrypto.transferPrivateKeyEncryptionKeyEncryption(
        password = recoveryAuth.pakeCode,
        protectedCustomerRecoveryPakeKey = recoveryAuth.protectedCustomerRecoveryPakeKey.publicKey,
        delegatedDecryptionKey = delegatedDecryptionKey,
        sealedPrivateKeyEncryptionKey = relationshipIdToPkekMap[endorsedTrustedContact.relationshipId].shouldNotBeNull()
      ).getOrThrow()
      socRecF8eClientFake.challengeResponses.add(
        SocialChallengeResponse(
          recoveryRelationshipId = endorsedTrustedContact.relationshipId,
          trustedContactRecoveryPakePubkey = decryptOutput.trustedContactRecoveryPakeKey,
          recoveryPakeConfirmation = decryptOutput.keyConfirmation,
          resealedDek = decryptOutput.sealedPrivateKeyEncryptionKey
        )
      )
    }

    val props = RecoveryChallengeUiProps(
      accountId = account.accountId,
      f8eEnvironment = account.config.f8eEnvironment,
      actions = app.socRecChallengeRepository.toActions(
        accountId = account.accountId,
        f8eEnvironment = account.config.f8eEnvironment,
        isUsingSocRecFakes = true
      ),
      relationshipIdToSocRecPkekMap = relationshipIdToPkekMap,
      sealedPrivateKeyMaterial = sealedPrivateKeyMaterial,
      endorsedTrustedContacts = relationshipsF8eClientFake.endorsedTrustedContacts.toImmutableList(),
      onExit = { onExitCalls.add(Unit) },
      onKeyRecovered = { onRecoveryCalls.add(it) }
    )

    app.recoveryChallengeUiStateMachine.test(
      props = props,
      useVirtualTime = false
    ) {
      awaitUntilScreenWithBody<LoadingSuccessBodyModel> {
        id.shouldBe(SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_STARTING)
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilScreenWithBody<FormBodyModel>(
        NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS
      )
        .clickPrimaryButton()

      awaitUntilScreenWithBody<FormBodyModel>(
        SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_TRUSTED_CONTACTS_LIST
      )

      cancelAndIgnoreRemainingEvents()
    }

    simulateRespondToChallenge()

    app.recoveryChallengeUiStateMachine.test(
      props = props,
      useVirtualTime = false
    ) {
      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(
        SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_STARTING
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilScreenWithBody<FormBodyModel>(
        NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS
      )
        .clickPrimaryButton()

      awaitUntilScreenWithBody<FormBodyModel> {
        id.shouldBe(
          SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_TRUSTED_CONTACTS_LIST
        )
        mainContentList
          .first()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items
          .first().should {
            it.sideText.shouldBe("Verified")
            it.trailingAccessory.shouldBeNull()
          }

        primaryButton.shouldNotBeNull()
          .isEnabled
          .shouldBe(true)
        primaryButton?.onClick?.invoke()
      }
      awaitUntilScreenWithBody<LoadingSuccessBodyModel> {
        id.shouldBe(SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_RESTORE_APP_KEY)
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
        onRecoveryCalls.awaitItem()
      }
    }
  }
})
