package build.wallet.statemachine.dev

import build.wallet.debug.DebugDataDeletionReport
import build.wallet.debug.DebugDataDeletionService
import build.wallet.debug.DebugDataDeletionTarget
import build.wallet.statemachine.core.test
import build.wallet.statemachine.recovery.cloud.CloudSignInUiStateMachineMock
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.list.ListItemAccessory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.collections.immutable.persistentSetOf

class DebugDataManagementUiStateMachineImplTests : FunSpec({
  test("manual key deletion groups start collapsed and can be expanded") {
    createStateMachine().test(manualKeyDeletionProps) {
      awaitItem().shouldBeInstanceOf<DebugMenuBodyModel>().apply {
        groups.mapNotNull { it.header }.shouldBe(manualKeyDeletionHeaders)
        groups.first().items.first().title.shouldBe("All local app private keys")
        collapsedGroupHeaders.shouldBe(defaultCollapsedManualKeyDeletionHeaders)

        onToggleGroupCollapse("Cloud backup keys")
      }

      awaitItem().shouldBeInstanceOf<DebugMenuBodyModel>().apply {
        collapsedGroupHeaders.shouldBe(
          persistentSetOf(
            "Local app keys",
            "Onboarding keys",
            "Recovery support keys"
          )
        )

        onToggleGroupCollapse("Cloud backup keys")
      }

      awaitItem().shouldBeInstanceOf<DebugMenuBodyModel>().apply {
        collapsedGroupHeaders.shouldBe(defaultCollapsedManualKeyDeletionHeaders)
      }
    }
  }

  test("manual key deletion confirmation uses stable UI order") {
    createStateMachine().test(manualKeyDeletionProps) {
      awaitItem().shouldBeInstanceOf<DebugMenuBodyModel>().apply {
        groups[0].items[3].onClick.shouldNotBeNull().invoke()
      }

      awaitItem().shouldBeInstanceOf<DebugMenuBodyModel>().apply {
        groups[1].items[0].onClick.shouldNotBeNull().invoke()
      }

      awaitItem().shouldBeInstanceOf<DebugMenuBodyModel>().apply {
        groups[0].items[0].onClick.shouldNotBeNull().invoke()
      }

      awaitItem().shouldBeInstanceOf<DebugMenuBodyModel>().apply {
        groups.last().items.single()
          .trailingAccessory
          .shouldBeInstanceOf<ListItemAccessory.ButtonAccessory>()
          .model
          .onClick()
      }

      awaitItem().shouldBeInstanceOf<DebugMenuBodyModel>().apply {
        alertModel
          .shouldBeInstanceOf<ButtonAlertModel>()
          .subline
          .shouldBe(
            "- All local app private keys\n" +
              "- Active app recovery auth key\n" +
              "- All cloud backup stores"
          )
      }
    }
  }

  test("recovery scenario presets are shown without collapsible groups") {
    createStateMachine().test(recoveryScenarioPresetsProps) {
      awaitItem().shouldBeInstanceOf<DebugMenuBodyModel>().apply {
        groups.size.shouldBe(1)
        groups.single().header.shouldBe(null)
        collapsedGroupHeaders.shouldBe(persistentSetOf())
        groups.single().items.map { it.title }.shouldBe(
          listOf(
            "Lost app",
            "Lost app + cloud",
            "Lost cloud only",
            "Bad cloud backup",
            "Rollback cloud keyset"
          )
        )
      }
    }
  }
})

private val manualKeyDeletionHeaders = listOf(
  "Local app keys",
  "Cloud backup keys",
  "Onboarding keys",
  "Recovery support keys"
)

private val defaultCollapsedManualKeyDeletionHeaders = persistentSetOf(
  "Local app keys",
  "Cloud backup keys",
  "Onboarding keys",
  "Recovery support keys"
)

private val manualKeyDeletionProps = DebugDataManagementProps(
  screen = DebugDataManagementScreen.ManualKeyDeletion,
  onBack = {}
)

private val recoveryScenarioPresetsProps = DebugDataManagementProps(
  screen = DebugDataManagementScreen.RecoveryScenarioPresets,
  onBack = {}
)

private fun createStateMachine() =
  DebugDataManagementUiStateMachineImpl(
    cloudSignInUiStateMachine = CloudSignInUiStateMachineMock(),
    debugDataDeletionService = object : DebugDataDeletionService {
      override suspend fun delete(
        targets: List<DebugDataDeletionTarget>,
      ): DebugDataDeletionReport =
        DebugDataDeletionReport(
          deletedTargets = targets,
          failures = emptyList()
        )
    }
  )
