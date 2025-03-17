package build.wallet.recovery.socrec

import app.cash.turbine.test
import bitkey.relationships.Relationships
import build.wallet.bitkey.relationships.*
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.relationships.RelationshipsServiceMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope

class SocRecServiceImplTests : FunSpec({
  val postSocRecTaskRepository = PostSocRecTaskRepositoryMock()
  val relationshipsService = RelationshipsServiceMock(turbines::create)

  val appScope = TestScope()

  val service = SocRecServiceImpl(
    postSocRecTaskRepository = postSocRecTaskRepository,
    appCoroutineScope = appScope,
    relationshipsService = relationshipsService
  )

  afterTest {
    postSocRecTaskRepository.reset()
    relationshipsService.clear()
  }

  test("justCompletedRecovery emits false by default") {
    service.justCompletedRecovery().test {
      awaitItem().shouldBeFalse()
    }
  }

  test("justCompletedRecovery emits true when post soc rec task state is HardwareReplacementScreens") {
    service.justCompletedRecovery().test {
      awaitItem().shouldBeFalse()

      postSocRecTaskRepository.mutableState.value =
        PostSocialRecoveryTaskState.HardwareReplacementScreens

      awaitItem().shouldBeTrue()
    }
  }

  test("justCompletedRecovery emits false when post soc rec task state is not HardwareReplacementScreens") {
    postSocRecTaskRepository.mutableState.value =
      PostSocialRecoveryTaskState.HardwareReplacementNotification

    service.justCompletedRecovery().test {
      awaitItem().shouldBeFalse()
    }
  }

  test("socRecRelationships filters by SOCIAL_RECOVERY_CONTACT role") {
    relationshipsService.relationships.value = Relationships(
      invitations = listOf(InvitationFake, BeneficiaryInvitationFake),
      endorsedTrustedContacts = listOf(EndorsedTrustedContactFake1, EndorsedBeneficiaryFake, EndorsedTrustedContactFake2),
      unendorsedTrustedContacts = listOf(UnendorsedBeneficiaryFake, UnendorsedTrustedContactFake),
      protectedCustomers = immutableListOf(ProtectedCustomerFake, ProtectedBeneficiaryCustomerFake)
    )

    appScope.testScheduler.runCurrent()

    service.socRecRelationships.test {
      awaitItem().shouldBe(
        Relationships(
          invitations = listOf(InvitationFake),
          endorsedTrustedContacts = listOf(EndorsedTrustedContactFake1, EndorsedTrustedContactFake2),
          unendorsedTrustedContacts = listOf(UnendorsedTrustedContactFake),
          protectedCustomers = immutableListOf(ProtectedCustomerFake)
        )
      )
    }
  }
})
