package build.wallet.integration.statemachine.recovery.socrec.challenge

import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_FAILED
import build.wallet.auth.AppAuthKeyMessageSignerImpl
import build.wallet.bitcoin.AppPrivateKeyDaoFake
import build.wallet.bitkey.socrec.PakeCode
import build.wallet.bitkey.socrec.SocialChallengeResponse
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState.VERIFIED
import build.wallet.bitkey.socrec.TrustedContactKeyCertificateFake
import build.wallet.cloud.backup.v2.FullAccountKeys
import build.wallet.cloud.backup.v2.FullAccountKeysMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.crypto.Spake2Impl
import build.wallet.encrypt.CryptoBoxImpl
import build.wallet.encrypt.MessageSignerImpl
import build.wallet.encrypt.SignatureVerifierImpl
import build.wallet.encrypt.SymmetricKeyGeneratorImpl
import build.wallet.encrypt.XChaCha20Poly1305Impl
import build.wallet.encrypt.XCiphertext
import build.wallet.encrypt.XNonceGeneratorImpl
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.recovery.socrec.SocRecCryptoImpl
import build.wallet.recovery.socrec.toActions
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.recovery.socrec.challenge.RecoveryChallengeUiProps
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.testing.launchNewApp
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
import io.ktor.http.HttpStatusCode
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

class RecoveryChallengeUiStateMachineFunctionalTests : FunSpec({
  val appTester = launchNewApp()
  val onExitCalls = turbines.create<Unit>("exit-recovery-flow")
  val onRecoveryCalls = turbines.create<FullAccountKeys>("recovery-key-recovered")
  val relationshipIdToPkekMap: MutableMap<String, XCiphertext> = mutableMapOf()
  val appPrivateKeyDao = AppPrivateKeyDaoFake()
  val messageSigner = MessageSignerImpl()
  val socRecCrypto =
    SocRecCryptoImpl(
      symmetricKeyGenerator = SymmetricKeyGeneratorImpl(),
      xChaCha20Poly1305 = XChaCha20Poly1305Impl(),
      xNonceGenerator = XNonceGeneratorImpl(),
      spake2 = Spake2Impl(),
      appAuthKeyMessageSigner = AppAuthKeyMessageSignerImpl(appPrivateKeyDao, messageSigner),
      signatureVerifier = SignatureVerifierImpl(),
      cryptoBox = CryptoBoxImpl()
    )
  val delegatedDecryptionKey = socRecCrypto.generateDelegatedDecryptionKey().getOrThrow()
  val trustedContact =
    TrustedContact(
      recoveryRelationshipId = "someRelationshipId",
      trustedContactAlias = TrustedContactAlias("someContact"),
      authenticationState = VERIFIED,
      keyCertificate = TrustedContactKeyCertificateFake.copy(delegatedDecryptionKey)
    )
  val pkMat = FullAccountKeysMock
  val (privateKeyEncryptionKey, sealedPrivateKeyMaterial) =
    socRecCrypto.encryptPrivateKeyMaterial(
      Json.encodeToString(pkMat).encodeUtf8()
    ).getOrThrow()

  beforeAny {
    appTester.app.socialRecoveryServiceFake.reset()
    appTester.app.socialRecoveryServiceFake.trustedContacts.add(trustedContact)
    appTester.app.socRecPendingChallengeDao.clear()
    relationshipIdToPkekMap[trustedContact.recoveryRelationshipId] =
      socRecCrypto.encryptPrivateKeyEncryptionKey(
        trustedContact.identityKey,
        privateKeyEncryptionKey
      ).getOrThrow()
  }

  test("Start Challenge") {
    val account = appTester.onboardFullAccountWithFakeHardware()
    appTester.app.recoveryChallengeUiStateMachine.test(
      props =
        RecoveryChallengeUiProps(
          accountId = account.accountId,
          f8eEnvironment = account.config.f8eEnvironment,
          actions =
            appTester.app.socRecChallengeRepository.toActions(
              accountId = account.accountId,
              f8eEnvironment = account.config.f8eEnvironment,
              isUsingSocRecFakes = true
            ),
          relationshipIdToSocRecPkekMap = relationshipIdToPkekMap,
          sealedPrivateKeyMaterial = sealedPrivateKeyMaterial,
          trustedContacts = appTester.app.socialRecoveryServiceFake.trustedContacts.toImmutableList(),
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
        primaryButton.shouldBeNull()
      }
    }
  }

  test("Start Challenge Failed") {
    val account = appTester.onboardFullAccountWithFakeHardware()
    appTester.app.socialRecoveryServiceFake.fakeNetworkingError =
      HttpError.ServerError(HttpResponseMock(HttpStatusCode.InternalServerError))
    appTester.app.recoveryChallengeUiStateMachine.test(
      props =
        RecoveryChallengeUiProps(
          accountId = account.accountId,
          f8eEnvironment = account.config.f8eEnvironment,
          actions =
            appTester.app.socRecChallengeRepository.toActions(
              accountId = account.accountId,
              f8eEnvironment = account.config.f8eEnvironment,
              isUsingSocRecFakes = true
            ),
          relationshipIdToSocRecPkekMap = relationshipIdToPkekMap,
          sealedPrivateKeyMaterial = sealedPrivateKeyMaterial,
          trustedContacts = appTester.app.socialRecoveryServiceFake.trustedContacts.toImmutableList(),
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
    val account = appTester.onboardFullAccountWithFakeHardware()
    appTester.app.recoveryChallengeUiStateMachine.test(
      props =
        RecoveryChallengeUiProps(
          accountId = account.accountId,
          f8eEnvironment = account.config.f8eEnvironment,
          actions =
            appTester.app.socRecChallengeRepository.toActions(
              accountId = account.accountId,
              f8eEnvironment = account.config.f8eEnvironment,
              isUsingSocRecFakes = true
            ),
          relationshipIdToSocRecPkekMap = relationshipIdToPkekMap,
          sealedPrivateKeyMaterial = sealedPrivateKeyMaterial,
          trustedContacts = appTester.app.socialRecoveryServiceFake.trustedContacts.toImmutableList(),
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
        val challenge =
          appTester.app.socialRecoveryServiceFake.challenges
            .shouldHaveSize(1)
            .first()
        val challengeAuth = appTester.app.socRecStartedChallengeAuthenticationDao.getAll()
          .getOrThrow()
          .single()
        val code = appTester.app.pakeCodeBuilder.buildRecoveryCode(
          challenge.response.counter,
          PakeCode(challengeAuth.pakeCode)
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
    val account = appTester.onboardFullAccountWithFakeHardware()
    var challengeId: String? = null
    appTester.app.recoveryChallengeUiStateMachine.test(
      props =
        RecoveryChallengeUiProps(
          accountId = account.accountId,
          f8eEnvironment = account.config.f8eEnvironment,
          actions =
            appTester.app.socRecChallengeRepository.toActions(
              accountId = account.accountId,
              f8eEnvironment = account.config.f8eEnvironment,
              isUsingSocRecFakes = true
            ),
          relationshipIdToSocRecPkekMap = relationshipIdToPkekMap,
          sealedPrivateKeyMaterial = sealedPrivateKeyMaterial,
          trustedContacts = appTester.app.socialRecoveryServiceFake.trustedContacts.toImmutableList(),
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
      challengeId =
        appTester.app.socialRecoveryServiceFake.challenges.single().response.challengeId
    }
    appTester.app.recoveryChallengeUiStateMachine.test(
      props =
        RecoveryChallengeUiProps(
          accountId = account.accountId,
          f8eEnvironment = account.config.f8eEnvironment,
          actions =
            appTester.app.socRecChallengeRepository.toActions(
              accountId = account.accountId,
              f8eEnvironment = account.config.f8eEnvironment,
              isUsingSocRecFakes = true
            ),
          relationshipIdToSocRecPkekMap = relationshipIdToPkekMap,
          sealedPrivateKeyMaterial = sealedPrivateKeyMaterial,
          trustedContacts = appTester.app.socialRecoveryServiceFake.trustedContacts.toImmutableList(),
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
      appTester.app.socialRecoveryServiceFake.challenges.single().response.challengeId.shouldBe(
        challengeId
      )
    }
  }

  test("Complete Challenge") {
    val account = appTester.onboardFullAccountWithFakeHardware()

    suspend fun simulateRespondToChallenge() {
      val recoveryAuth = appTester.app.socRecStartedChallengeAuthenticationDao.getByRelationshipId(
        recoveryRelationshipId = trustedContact.recoveryRelationshipId
      ).getOrThrow().shouldNotBeNull()
      val decryptOutput = socRecCrypto.decryptPrivateKeyEncryptionKey(
        password = PakeCode(recoveryAuth.pakeCode),
        protectedCustomerRecoveryPakeKey = recoveryAuth.protectedCustomerRecoveryPakeKey,
        delegatedDecryptionKey = trustedContact.identityKey,
        sealedPrivateKeyEncryptionKey = relationshipIdToPkekMap[trustedContact.recoveryRelationshipId].shouldNotBeNull()
      ).getOrThrow()
      appTester.app.socialRecoveryServiceFake.challengeResponses.add(
        SocialChallengeResponse(
          recoveryRelationshipId = trustedContact.recoveryRelationshipId,
          trustedContactRecoveryPakePubkey = decryptOutput.trustedContactRecoveryPakeKey.publicKey,
          recoveryPakeConfirmation = decryptOutput.keyConfirmation,
          resealedDek = decryptOutput.sealedPrivateKeyEncryptionKey
        )
      )
    }

    val props = RecoveryChallengeUiProps(
      accountId = account.accountId,
      f8eEnvironment = account.config.f8eEnvironment,
      actions =
        appTester.app.socRecChallengeRepository.toActions(
          accountId = account.accountId,
          f8eEnvironment = account.config.f8eEnvironment,
          isUsingSocRecFakes = true
        ),
      relationshipIdToSocRecPkekMap = relationshipIdToPkekMap,
      sealedPrivateKeyMaterial = sealedPrivateKeyMaterial,
      trustedContacts = appTester.app.socialRecoveryServiceFake.trustedContacts.toImmutableList(),
      onExit = { onExitCalls.add(Unit) },
      onKeyRecovered = { onRecoveryCalls.add(it) }
    )

    appTester.app.recoveryChallengeUiStateMachine.test(
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

    appTester.app.recoveryChallengeUiStateMachine.test(
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
