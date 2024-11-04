package build.wallet.ui.app.transactions

import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.*
import build.wallet.compose.collections.immutableListOf
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data.TitleTextType
import build.wallet.statemachine.transactions.TransactionDetailModel
import build.wallet.statemachine.transactions.TxStatusModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.icon.*
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
              txStatusModel = TxStatusModel.Pending(
                recipientAddress = "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
                transactionType = Incoming,
                isLate = false
              ),
              isLoading = false,
              onViewTransaction = {},
              onClose = {},
              onSpeedUpTransaction = {},
              content =
                immutableListOf(
                  DataList(
                    items = immutableListOf(),
                    total =
                      Data(
                        title = "Amount receiving",
                        sideText = "~$5.08",
                        sideTextType = Data.SideTextType.BODY2BOLD,
                        secondarySideText = "12,759 sats"
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
              txStatusModel = TxStatusModel.Pending(
                recipientAddress = "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
                transactionType = Outgoing,
                isLate = true
              ),
              isLoading = false,
              onViewTransaction = {},
              onClose = {},
              onSpeedUpTransaction = {},
              content =
                immutableListOf(
                  DataList(
                    items =
                      immutableListOf(
                        Data(
                          title = "Should arrive by",
                          sideText = "Feb 1 at 5:25pm",
                          sideTextTreatment = Data.SideTextTreatment.STRIKETHROUGH,
                          sideTextType = Data.SideTextType.REGULAR,
                          secondarySideText = "7m late",
                          secondarySideTextType = Data.SideTextType.BOLD,
                          secondarySideTextTreatment = Data.SideTextTreatment.WARNING,
                          explainer =
                            Data.Explainer(
                              title = "Taking longer than usual",
                              subtitle = "You can either wait for this transaction to be confirmed or speed it up – you'll need to pay a higher network fee.",
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
                          title = "Recipient receives",
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
                        sideText = "~$5.08",
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
              txStatusModel = TxStatusModel.Confirmed(
                recipientAddress = "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
                transactionType = Outgoing
              ),
              isLoading = false,
              onViewTransaction = {},
              onClose = {},
              onSpeedUpTransaction = {},
              content =
                immutableListOf(
                  DataList(
                    items =
                      immutableListOf(
                        Data(
                          title = "Confirmed at",
                          sideText = "03-17-1963"
                        )
                      )
                  ),
                  DataList(
                    items =
                      immutableListOf(
                        Data(
                          title = "Recipient received",
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
                        secondaryTitle = "At time sent",
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
              txStatusModel = TxStatusModel.Confirmed(
                recipientAddress = "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
                transactionType = Incoming
              ),
              isLoading = false,
              onViewTransaction = {},
              onClose = {},
              onSpeedUpTransaction = {},
              content =
                immutableListOf(
                  DataList(
                    items =
                      immutableListOf(
                        Data(
                          title = "Confirmed at",
                          sideText = "03-17-1963"
                        )
                      )
                  ),
                  DataList(
                    items = immutableListOf(),
                    total =
                      Data(
                        title = "Amount received",
                        secondaryTitle = "At time confirmed",
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

    test("utxo consolidation transaction detail") {
      paparazzi.snapshot {
        FormScreen(
          model =
            TransactionDetailModel(
              feeBumpEnabled = false,
              txStatusModel = TxStatusModel.Confirmed(
                recipientAddress = "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
                transactionType = UtxoConsolidation
              ),
              isLoading = false,
              onViewTransaction = {},
              onClose = {},
              onSpeedUpTransaction = {},
              content =
                immutableListOf(
                  DataList(
                    items =
                      immutableListOf(
                        Data(
                          title = "Confirmed at",
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
                          titleTextType = TitleTextType.BOLD,
                          secondaryTitle = "At time confirmed",
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
  })
