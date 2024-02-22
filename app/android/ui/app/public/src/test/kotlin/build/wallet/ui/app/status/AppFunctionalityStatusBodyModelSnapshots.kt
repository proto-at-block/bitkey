package build.wallet.ui.app.status

import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.F8eUnreachable
import build.wallet.availability.InternetUnreachable
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.status.AppFunctionalityStatusBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Instant

class AppFunctionalityStatusBodyModelSnapshots : FunSpec({

  val paparazzi = paparazziExtension()

  test("app_status_f8eUnreachable") {
    paparazzi.snapshot {
      FormScreen(
        model =
          AppFunctionalityStatusBodyModel(
            status =
              AppFunctionalityStatus.LimitedFunctionality(
                cause = F8eUnreachable(Instant.DISTANT_PAST)
              ),
            cause = F8eUnreachable(Instant.DISTANT_PAST),
            dateFormatter = { "9:14pm" },
            onClose = {}
          )
      )
    }
  }

  test("app_status_internetUnreachable") {
    paparazzi.snapshot {
      FormScreen(
        model =
          AppFunctionalityStatusBodyModel(
            status =
              AppFunctionalityStatus.LimitedFunctionality(
                cause =
                  InternetUnreachable(
                    Instant.DISTANT_PAST,
                    Instant.DISTANT_PAST
                  )
              ),
            cause =
              InternetUnreachable(
                Instant.DISTANT_PAST,
                Instant.DISTANT_PAST
              ),
            dateFormatter = { "9:14pm" },
            onClose = {}
          )
      )
    }
  }
})
