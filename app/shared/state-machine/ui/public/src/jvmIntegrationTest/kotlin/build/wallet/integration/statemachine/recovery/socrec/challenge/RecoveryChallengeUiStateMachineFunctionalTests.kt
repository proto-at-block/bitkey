package build.wallet.integration.statemachine.recovery.socrec.challenge

import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_FAILED
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.ProtectedCustomerEphemeralKey
import build.wallet.bitkey.socrec.ProtectedCustomerIdentityKey
import build.wallet.bitkey.socrec.SocialChallengeResponse
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.cloud.backup.v2.FullAccountKeys
import build.wallet.cloud.backup.v2.FullAccountKeysMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.XCiphertext
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.recovery.socrec.SocRecCryptoFake
import build.wallet.recovery.socrec.toActions
import build.wallet.statemachine.core.LoadingBodyModel
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
  // BKR-936 Revert to SocRecCryptoImpl
  // https://github.com/squareup/wallet/pull/13749
  val socRecCrypto = SocRecCryptoFake()
  val trustedContact =
    TrustedContact(
      recoveryRelationshipId = "someRelationshipId",
      trustedContactAlias = TrustedContactAlias("someContact"),
      identityKey = socRecCrypto.generateAsymmetricKey(::TrustedContactIdentityKey).getOrThrow()
    )
  val pkMat = FullAccountKeysMock
  val (privateKeyEncryptionKey, sealedPrivateKeyMaterial) =
    socRecCrypto.encryptPrivateKeyMaterial(
      Json.encodeToString(pkMat).encodeUtf8()
    ).getOrThrow()
  val protectedCustomerIdentityKey =
    socRecCrypto.generateAsymmetricKey(
      ::ProtectedCustomerIdentityKey
    ).getOrThrow()
  val protectedCustomerIdentityPublicKey =
    ProtectedCustomerIdentityKey(AppKey.fromPublicKey(protectedCustomerIdentityKey.publicKey.value))

  beforeAny {
    appTester.app.socialRecoveryServiceFake.reset()
    appTester.app.socialRecoveryServiceFake.trustedContacts.add(trustedContact)
    appTester.app.socRecPendingChallengeDao.clear()
    relationshipIdToPkekMap[trustedContact.recoveryRelationshipId] =
      socRecCrypto.encryptPrivateKeyEncryptionKey(
        trustedContact.identityKey,
        protectedCustomerIdentityKey,
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
          protectedCustomerIdentityKey = protectedCustomerIdentityPublicKey,
          onKeyRecovered = { onRecoveryCalls.add(it) }
        )
    ) {
      awaitUntilScreenWithBody<LoadingBodyModel> {
        id.shouldBe(SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_STARTING)
        message.shouldBe("Starting Recovery...")
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
    appTester.app.socialRecoveryServiceFake.fakeNetworkingError = HttpError.ServerError(HttpResponseMock(HttpStatusCode.InternalServerError))
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
          protectedCustomerIdentityKey = protectedCustomerIdentityPublicKey,
          onKeyRecovered = { onRecoveryCalls.add(it) }
        )
    ) {
      awaitUntilScreenWithBody<LoadingBodyModel>(
        SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_STARTING
      )
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
          protectedCustomerIdentityKey = protectedCustomerIdentityPublicKey,
          onKeyRecovered = { onRecoveryCalls.add(it) }
        )
    ) {
      awaitUntilScreenWithBody<LoadingBodyModel> {
        id.shouldBe(SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_STARTING)
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
            .protectedCustomerSideOfChallenge
        mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.ListGroup>()
          .listGroupModel
          .items
          .first()
          .title.shouldBeEqual(challenge.code)
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
          protectedCustomerIdentityKey = protectedCustomerIdentityPublicKey,
          onKeyRecovered = { onRecoveryCalls.add(it) }
        )
    ) {
      awaitUntilScreenWithBody<LoadingBodyModel>(
        SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_STARTING
      )
      awaitUntilScreenWithBody<FormBodyModel>(
        NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS
      )
      challengeId = appTester.app.socialRecoveryServiceFake.challenges.single().protectedCustomerSideOfChallenge.challengeId
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
          protectedCustomerIdentityKey = protectedCustomerIdentityPublicKey,
          onKeyRecovered = { onRecoveryCalls.add(it) }
        )
    ) {
      awaitUntilScreenWithBody<LoadingBodyModel>(
        SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_STARTING
      )
      awaitUntilScreenWithBody<FormBodyModel>(
        NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS
      )
      appTester.app.socialRecoveryServiceFake.challenges.single().protectedCustomerSideOfChallenge.challengeId.shouldBe(
        challengeId
      )
    }
  }

  test("Complete Challenge") {
    val account = appTester.onboardFullAccountWithFakeHardware()
    val ephemeralKey =
      appTester.app.socRecKeysRepository.getOrCreateKey(
        ::ProtectedCustomerEphemeralKey
      ).getOrThrow()
    appTester.app.socialRecoveryServiceFake.challengeResponses.add(
      SocialChallengeResponse(
        recoveryRelationshipId = trustedContact.recoveryRelationshipId,
        sharedSecretCiphertext =
          socRecCrypto.deriveAndEncryptSharedSecret(
            protectedCustomerIdentityKey,
            ephemeralKey,
            trustedContact.identityKey
          ).getOrThrow()
      )
    )

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
          protectedCustomerIdentityKey = protectedCustomerIdentityPublicKey,
          onKeyRecovered = { onRecoveryCalls.add(it) }
        )
    ) {
      awaitUntilScreenWithBody<LoadingBodyModel> {
        id.shouldBe(SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_STARTING)
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
      awaitUntilScreenWithBody<LoadingBodyModel> {
        id.shouldBe(SocialRecoveryEventTrackerScreenId.RECOVERY_CHALLENGE_RESTORE_APP_KEY)
        onRecoveryCalls.awaitItem()
      }
    }
  }
})
