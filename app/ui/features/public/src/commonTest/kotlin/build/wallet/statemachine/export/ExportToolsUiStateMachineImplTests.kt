package build.wallet.statemachine.export

import app.cash.turbine.plusAssign
import build.wallet.bitcoin.export.ExportTransactionsServiceMock
import build.wallet.bitcoin.export.ExportWatchingDescriptorServiceMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.sharing.SharingManagerFake
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.Callout
import build.wallet.statemachine.core.test
import build.wallet.statemachine.export.view.ExportSheetBodyModel
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class ExportToolsUiStateMachineImplTests : FunSpec({
  val exportWatchingDescriptorService = ExportWatchingDescriptorServiceMock()
  val exportTransactionsService = ExportTransactionsServiceMock()
  val stateMachine = ExportToolsUiStateMachineImpl(
    sharingManager = SharingManagerFake(),
    exportWatchingDescriptorService = exportWatchingDescriptorService,
    exportTransactionsService = exportTransactionsService
  )
  val onBackCalls = turbines.create<Unit>("onBack calls")

  val props = ExportToolsUiProps(
    onBack = { onBackCalls += Unit }
  )

  beforeTest {
    exportWatchingDescriptorService.reset()
    exportTransactionsService.reset()
  }

  test("test resting state") {
    stateMachine.test(props) {
      awaitUntilScreenWithBody<ExportToolsSelectionModel> {
        bottomSheetModel.shouldBeNull()

        val formBody = body as ExportToolsSelectionModel
        formBody.run {
          toolbar.shouldNotBeNull().middleAccessory.shouldNotBeNull().title.shouldBe("Exports")
          toolbar.shouldNotBeNull().leadingAccessory.shouldBeTypeOf<IconAccessory>()

          mainContentList.count().shouldBe(1)
          mainContentList[0].shouldBeTypeOf<FormMainContentModel.ListGroup> {
            it.listGroupModel.items.map { it.title }
              .shouldBe(listOf("Transaction history", "Current wallet descriptor"))
            it.listGroupModel.items.map { it.secondaryText }
              .shouldBe(listOf("Export CSV", "Export XPUB bundle"))
          }

          primaryButton.shouldBeNull()
        }
      }
    }
  }

  test("onClose is called when close button is pressed.") {
    stateMachine.test(props) {
      awaitBody<ExportToolsSelectionModel> {
        onBack?.invoke()
      }

      onBackCalls.awaitItem()
    }
  }

  test("should show ExportTransactionHistorySheet when exporting transaction history") {
    stateMachine.test(props) {
      awaitBody<ExportToolsSelectionModel> {
        mainContentList[0].shouldBeTypeOf<FormMainContentModel.ListGroup> {
          it.listGroupModel.items[0].trailingAccessory.shouldBeTypeOf<ListItemAccessory.IconAccessory>().onClick.shouldNotBeNull()
          it.listGroupModel.items[0].onClick.shouldNotBeNull().invoke()
        }
      }

      awaitSheet<ExportSheetBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Export transaction history")
        header.shouldNotBeNull().sublineModel.shouldNotBeNull().string.shouldBe("Download your Bitkey transaction history.")
        mainContentList.shouldBeEmpty()
        primaryButton.shouldNotBeNull().text.shouldBe("Download .CSV")
        secondaryButton.shouldNotBeNull().text.shouldBe("Cancel")

        primaryButton!!.onClick.invoke()
      }

      awaitSheet<ExportSheetBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()
      }

      // Should dismiss bottom sheet when sharing manager is presented
      awaitItem().bottomSheetModel.shouldBeNull()
    }
  }

  test("should show error export sheet when exporting transaction history fails") {
    exportTransactionsService.result = Err(Error("oops"))
    stateMachine.test(props) {
      awaitBody<ExportToolsSelectionModel> {
        mainContentList[0].shouldBeTypeOf<FormMainContentModel.ListGroup> {
          it.listGroupModel.items[0].trailingAccessory.shouldBeTypeOf<ListItemAccessory.IconAccessory>().onClick.shouldNotBeNull()
          it.listGroupModel.items[0].onClick.shouldNotBeNull().invoke()
        }
      }

      awaitSheet<ExportSheetBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Export transaction history")
        header.shouldNotBeNull().sublineModel.shouldNotBeNull().string.shouldBe("Download your Bitkey transaction history.")
        mainContentList.shouldBeEmpty()
        primaryButton.shouldNotBeNull().text.shouldBe("Download .CSV")
        secondaryButton.shouldNotBeNull().text.shouldBe("Cancel")

        primaryButton!!.onClick.invoke()
      }

      awaitSheet<ExportSheetBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()
      }

      awaitSheet<ExportSheetBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("An error occurred.")
        header.shouldNotBeNull().sublineModel.shouldNotBeNull().string.shouldBe("We had trouble exporting your wallet information. Please try again later.")
        secondaryButton.shouldBeNull()

        primaryButton.shouldNotBeNull().onClick()
      }

      awaitItem().bottomSheetModel.shouldBeNull()
    }
  }

  test("should show ExportDescriptorSheet when exporting wallet descriptor") {
    stateMachine.test(props) {
      awaitBody<ExportToolsSelectionModel> {
        mainContentList[0].shouldBeTypeOf<FormMainContentModel.ListGroup> {
          it.listGroupModel.items[1].trailingAccessory.shouldBeTypeOf<ListItemAccessory.IconAccessory>()
            .shouldNotBeNull()
          it.listGroupModel.items[1].onClick.shouldNotBeNull().invoke()
        }
      }

      awaitSheet<ExportSheetBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Export wallet descriptor")
        header.shouldNotBeNull().sublineModel.shouldNotBeNull().string.shouldBe("Download your Bitkey wallet descriptor.")
        secondaryButton.shouldNotBeNull().text.shouldBe("Cancel")
        primaryButton.shouldNotBeNull().text.shouldBe("Download XPUB bundle")

        mainContentList.size.shouldBe(1)
        mainContentList.first().shouldNotBeNull().shouldBeTypeOf<Callout>().should {
          it.item.subtitle.shouldNotBeNull().string.shouldBe("XPUB bundles contain sensitive privacy data. For tax reporting, use your transaction history.")
        }

        primaryButton!!.onClick.invoke()
      }

      awaitSheet<ExportSheetBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()
      }

      // Should dismiss bottom sheet when sharing manager is presented
      awaitItem().bottomSheetModel.shouldBeNull()
    }
  }

  test("should show error export sheet when wallet descriptor export fails") {
    exportWatchingDescriptorService.result = Err(Error("oops"))
    stateMachine.test(props) {
      awaitBody<ExportToolsSelectionModel> {
        mainContentList[0].shouldBeTypeOf<FormMainContentModel.ListGroup> {
          it.listGroupModel.items[1].trailingAccessory.shouldBeTypeOf<ListItemAccessory.IconAccessory>().onClick?.shouldNotBeNull()
          it.listGroupModel.items[1].onClick.shouldNotBeNull().invoke()
        }
      }

      awaitSheet<ExportSheetBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Export wallet descriptor")
        header.shouldNotBeNull().sublineModel.shouldNotBeNull().string.shouldBe("Download your Bitkey wallet descriptor.")
        secondaryButton.shouldNotBeNull().text.shouldBe("Cancel")
        primaryButton.shouldNotBeNull().text.shouldBe("Download XPUB bundle")

        mainContentList.size.shouldBe(1)
        mainContentList[0].shouldNotBeNull().shouldBeTypeOf<Callout>().should {
          it.item.subtitle.shouldNotBeNull().string.shouldBe("XPUB bundles contain sensitive privacy data. For tax reporting, use your transaction history.")
        }

        primaryButton!!.onClick.invoke()
      }

      awaitSheet<ExportSheetBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()
      }

      awaitSheet<ExportSheetBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("An error occurred.")
        header.shouldNotBeNull().sublineModel.shouldNotBeNull().string.shouldBe("We had trouble exporting your wallet information. Please try again later.")
        secondaryButton.shouldBeNull()

        primaryButton.shouldNotBeNull().onClick()
      }

      awaitItem().bottomSheetModel.shouldBeNull()
    }
  }
})
