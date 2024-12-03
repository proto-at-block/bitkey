package build.wallet.ui.app.transactions

import build.wallet.compose.collections.immutableListOf
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.Bitcoin
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.LEADING
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.MONO
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.statemachine.transactions.TransactionDetailModel
import build.wallet.statemachine.transactions.completeTransactionStepper
import build.wallet.statemachine.transactions.processingTransactionStepper
import build.wallet.statemachine.transactions.submittedTransactionStepper
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.icon.*
import build.wallet.ui.model.icon.IconSize.Avatar
import io.kotest.core.spec.style.FunSpec

class TransactionDetailScreenSnapshots :
  FunSpec({
    val paparazzi = paparazziExtension()

    test("pending receive transaction detail") {
      paparazzi.snapshot {
        FormScreen(
          model =
            TransactionDetailModel(
              feeBumpEnabled = false,
              formHeaderModel = FormHeaderModel(
                iconModel = IconModel(
                  icon = Bitcoin,
                  iconSize = Avatar
                ),
                headline = "Transaction pending",
                sublineModel = StringModel("bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh"),
                sublineTreatment = MONO,
                alignment = LEADING
              ),
              isLoading = false,
              viewTransactionText = "View transaction",
              onViewTransaction = {},
              onClose = {},
              onSpeedUpTransaction = {},
              content =
                immutableListOf(
                  processingTransactionStepper,
                  FormMainContentModel.Divider,
                  DataList(
                    items = immutableListOf(
                      Data(
                        title = "Amount",
                        sideText = "$5.08",
                        sideTextType = Data.SideTextType.BODY2BOLD,
                        secondarySideText = "12,759 sats"
                      )
                    )
                  )
                )
            )
        )
      }
    }

    test("late send transaction detail") {
      paparazzi.snapshot {
        FormScreen(
          model =
            TransactionDetailModel(
              feeBumpEnabled = true,
              formHeaderModel = FormHeaderModel(
                iconModel = IconModel(
                  icon = Bitcoin,
                  iconSize = Avatar
                ),
                headline = "Transaction delayed",
                sublineModel = StringModel("bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh"),
                sublineTreatment = MONO,
                alignment = LEADING
              ),
              isLoading = false,
              viewTransactionText = "View transaction",
              onViewTransaction = {},
              onClose = {},
              onSpeedUpTransaction = {},
              content =
                immutableListOf(
                  processingTransactionStepper,
                  FormMainContentModel.Divider,
                  DataList(
                    items =
                      immutableListOf(
                        Data(
                          title = "Arrival time",
                          sideText = "Feb 1 at 5:25pm",
                          sideTextTreatment = Data.SideTextTreatment.STRIKETHROUGH,
                          sideTextType = Data.SideTextType.REGULAR,
                          secondarySideText = "7m late",
                          secondarySideTextType = Data.SideTextType.BOLD,
                          secondarySideTextTreatment = Data.SideTextTreatment.WARNING,
                          explainer =
                            Data.Explainer(
                              title = "Speed up transaction?",
                              subtitle = "You can speed up this transaction by increasing the network fee.",
                              iconButton = IconButtonModel(
                                iconModel = IconModel(
                                  icon = Icon.SmallIconInformationFilled,
                                  iconSize = IconSize.XSmall,
                                  iconBackgroundType = IconBackgroundType.Circle(
                                    circleSize = IconSize.XSmall
                                  ),
                                  iconTint = IconTint.Foreground,
                                  iconOpacity = 0.20f
                                ),
                                onClick = StandardClick { }
                              )
                            )
                        )
                      )
                  ),
                  DataList(
                    items =
                      immutableListOf(
                        Data(
                          title = "Amount",
                          sideText = "$30.82",
                          secondarySideText = "50,000 sats"
                        ),
                        Data(
                          title = "Network fees",
                          sideText = "$0.12",
                          secondarySideText = "189 sats"
                        )
                      ),
                    total =
                      Data(
                        title = "Total",
                        sideText = "$5.08",
                        sideTextType = Data.SideTextType.BODY2BOLD,
                        secondarySideText = "12,759 sats"
                      )
                  )
                )
            )
        )
      }
    }

    test("Sent transaction detail") {
      paparazzi.snapshot {
        FormScreen(
          model =
            TransactionDetailModel(
              feeBumpEnabled = false,
              formHeaderModel = FormHeaderModel(
                iconModel = IconModel(
                  icon = Bitcoin,
                  iconSize = Avatar
                ),
                headline = "Transaction sent",
                sublineModel = StringModel("bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh"),
                sublineTreatment = MONO,
                alignment = LEADING
              ),
              isLoading = false,
              viewTransactionText = "View transaction",
              onViewTransaction = {},
              onClose = {},
              onSpeedUpTransaction = {},
              content =
                immutableListOf(
                  completeTransactionStepper,
                  FormMainContentModel.Divider,
                  DataList(
                    items =
                      immutableListOf(
                        Data(
                          title = "Confirmed",
                          sideText = "03-17-1963"
                        )
                      )
                  ),
                  DataList(
                    items =
                      immutableListOf(
                        Data(
                          title = "Amount",
                          sideText = "$9.00",
                          secondarySideText = "35,584 sats"
                        ),
                        Data(
                          title = "Network fees",
                          sideText = "$1.00",
                          secondarySideText = "5,526 sats"
                        )
                      ),
                    total =
                      Data(
                        title = "Total",
                        sideText = "$10.00",
                        sideTextType = Data.SideTextType.BODY2BOLD,
                        secondarySideText = "41,110 sats"
                      )
                  )
                )
            )
        )
      }
    }

    test("Received transaction detail") {
      paparazzi.snapshot {
        FormScreen(
          model =
            TransactionDetailModel(
              feeBumpEnabled = false,
              formHeaderModel = FormHeaderModel(
                iconModel = IconModel(
                  icon = Bitcoin,
                  iconSize = Avatar
                ),
                headline = "Transaction received",
                sublineModel = StringModel("bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh"),
                sublineTreatment = MONO,
                alignment = LEADING
              ),
              isLoading = false,
              viewTransactionText = "View transaction",
              onViewTransaction = {},
              onClose = {},
              onSpeedUpTransaction = {},
              content =
                immutableListOf(
                  completeTransactionStepper,
                  FormMainContentModel.Divider,
                  DataList(
                    items =
                      immutableListOf(
                        Data(
                          title = "Confirmed",
                          sideText = "03-17-1963"
                        )
                      )
                  ),
                  DataList(
                    items = immutableListOf(
                      Data(
                        title = "Amount",
                        sideText = "$10.00",
                        sideTextType = Data.SideTextType.BODY2BOLD,
                        secondarySideText = "41,110 sats"
                      )
                    )
                  )
                )
            )
        )
      }
    }

    test("utxo consolidation transaction detail") {
      paparazzi.snapshot {
        FormScreen(
          model =
            TransactionDetailModel(
              feeBumpEnabled = false,
              formHeaderModel = FormHeaderModel(
                iconModel = IconModel(
                  icon = Bitcoin,
                  iconSize = Avatar
                ),
                headline = "UTXO Consolidation",
                sublineModel = StringModel("bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh"),
                sublineTreatment = MONO,
                alignment = LEADING
              ),
              isLoading = false,
              viewTransactionText = "View transaction",
              onViewTransaction = {},
              onClose = {},
              onSpeedUpTransaction = {},
              content =
                immutableListOf(
                  completeTransactionStepper,
                  FormMainContentModel.Divider,
                  DataList(
                    items =
                      immutableListOf(
                        Data(
                          title = "Confirmed",
                          sideText = "Sep 20 at 1:28 pm"
                        )
                      )
                  ),
                  DataList(
                    items =
                      immutableListOf(
                        Data(
                          title = "UTXOs consolidated",
                          sideText = "2 → 1"
                        ),
                        Data(
                          title = "Consolidation cost",
                          sideText = "$1.23",
                          secondarySideText = "2000 sats"
                        )
                      ),
                    total = null
                  )
                )
            )
        )
      }
    }

    test("pending partnership transaction") {
      paparazzi.snapshot {
        FormScreen(
          model =
            TransactionDetailModel(
              feeBumpEnabled = false,
              formHeaderModel = FormHeaderModel(
                iconModel = IconModel(
                  icon = Bitcoin, // In practice we dynamically download partner icons, this is just a placeholder for snapshots.
                  iconSize = Avatar
                ),
                headline = "Cash App transfer",
                sublineModel = StringModel("Arrival times and fees are estimates. Confirm details through Cash App."),
                alignment = LEADING
              ),
              isLoading = false,
              viewTransactionText = "View in Cash App",
              onViewTransaction = {},
              onClose = {},
              onSpeedUpTransaction = {},
              content =
                immutableListOf(
                  submittedTransactionStepper,
                  FormMainContentModel.Divider,
                  DataList(
                    items = immutableListOf(
                      Data(
                        title = "Amount",
                        sideText = "$5.08",
                        sideTextType = Data.SideTextType.BODY2BOLD,
                        secondarySideText = "12,759 sats"
                      )
                    )
                  )
                )
            )
        )
      }
    }

    test("confirmed partnership transaction") {
      paparazzi.snapshot {
        FormScreen(
          model =
            TransactionDetailModel(
              feeBumpEnabled = false,
              formHeaderModel = FormHeaderModel(
                iconModel = IconModel(
                  icon = Bitcoin,
                  iconSize = Avatar
                ),
                headline = "Cash App sale",
                sublineModel = StringModel("Arrival times and fees are estimates. Confirm details through Cash App."),
                alignment = LEADING
              ),
              isLoading = false,
              viewTransactionText = "View in Cash App",
              onViewTransaction = {},
              onClose = {},
              onSpeedUpTransaction = {},
              content =
                immutableListOf(
                  completeTransactionStepper,
                  FormMainContentModel.Divider,
                  DataList(
                    items =
                      immutableListOf(
                        Data(
                          title = "Confirmed",
                          sideText = "03-17-1963"
                        )
                      )
                  ),
                  DataList(
                    items =
                      immutableListOf(
                        Data(
                          title = "Amount",
                          sideText = "$9.00",
                          secondarySideText = "35,584 sats"
                        ),
                        Data(
                          title = "Network fees",
                          sideText = "$1.00",
                          secondarySideText = "5,526 sats"
                        )
                      ),
                    total =
                      Data(
                        title = "Total",
                        sideText = "$10.00",
                        sideTextType = Data.SideTextType.BODY2BOLD,
                        secondarySideText = "41,110 sats"
                      )
                  )
                )
            )
        )
      }
    }
  })
