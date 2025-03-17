package build.wallet.integration.statemachine.create

import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.list.ListItemModel
import io.kotest.matchers.types.shouldBeTypeOf

val FormBodyModel.beTrustedContactButton: ListItemModel
  get() =
    mainContentList.first()
      .shouldBeTypeOf<FormMainContentModel.ListGroup>()
      .listGroupModel
      .items[0]

val FormBodyModel.restoreButton: ListItemModel
  get() =
    mainContentList.first()
      .shouldBeTypeOf<FormMainContentModel.ListGroup>()
      .listGroupModel
      .items[1]
