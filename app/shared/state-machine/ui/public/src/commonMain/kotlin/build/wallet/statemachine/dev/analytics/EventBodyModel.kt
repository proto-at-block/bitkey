package build.wallet.statemachine.dev.analytics

import build.wallet.analytics.v1.Event
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Model for showing screen with singular event info.
 */
fun EventBodyModel(
  onBack: () -> Unit,
  event: Event,
) = FormBodyModel(
  onBack = onBack,
  toolbar =
    ToolbarModel(
      leadingAccessory = BackAccessory(onClick = onBack),
      middleAccessory = ToolbarMiddleAccessoryModel(title = "Event Detail")
    ),
  header = null,
  mainContentList =
    immutableListOfNotNull(
      FormMainContentModel.DataList(
        items =
          immutableListOfNotNull(
            FormMainContentModel.DataList.Data(
              title = "Event Time",
              sideText = event.event_time,
              showBottomDivider = true
            ),
            FormMainContentModel.DataList.Data(
              title = "Action",
              sideText = event.action.name,
              showBottomDivider = event.screen_id.isNotEmpty()
            ),
            if (event.screen_id.isNotEmpty()) {
              FormMainContentModel.DataList.Data(
                title = "Screen ID",
                sideText = event.screen_id
              )
            } else {
              null
            }
          )
      ),
      FormMainContentModel.DataList(
        items =
          immutableListOf(
            FormMainContentModel.DataList.Data(
              title = "Country",
              sideText = event.country,
              showBottomDivider = true
            ),
            FormMainContentModel.DataList.Data(
              title = "Locale Currency",
              sideText = event.locale_currency,
              showBottomDivider = true
            ),
            FormMainContentModel.DataList.Data(
              title = "Fiat Currency Preference",
              sideText = event.fiat_currency_preference,
              showBottomDivider = true
            ),
            FormMainContentModel.DataList.Data(
              title = "Bitcoin Display Preference",
              sideText = event.bitcoin_display_preference
            )
          )
      ),
      FormMainContentModel.DataList(
        items =
          immutableListOf(
            FormMainContentModel.DataList.Data(
              title = "Account ID",
              sideText = event.account_id,
              showBottomDivider = true
            ),
            FormMainContentModel.DataList.Data(
              title = "App Device ID",
              sideText = event.app_device_id,
              showBottomDivider = true
            ),
            FormMainContentModel.DataList.Data(
              title = "App Installation ID",
              sideText = event.app_installation_id,
              showBottomDivider = true
            ),
            FormMainContentModel.DataList.Data(
              title = "Keyset ID",
              sideText = event.keyset_id,
              showBottomDivider = true
            ),
            FormMainContentModel.DataList.Data(
              title = "Session ID",
              sideText = event.session_id
            )
          )
      ),
      event.hw_info?.let { hwInfo ->
        FormMainContentModel.DataList(
          items =
            immutableListOf(
              FormMainContentModel.DataList.Data(
                title = "HW Model",
                sideText = hwInfo.hw_model,
                showBottomDivider = true
              ),
              FormMainContentModel.DataList.Data(
                title = "HW Manufacture Info",
                sideText = hwInfo.hw_manufacture_info,
                showBottomDivider = true
              ),
              FormMainContentModel.DataList.Data(
                title = "Firmware Version",
                sideText = hwInfo.firmware_version,
                showBottomDivider = true
              ),
              FormMainContentModel.DataList.Data(
                title = "Serial Number",
                sideText = hwInfo.serial_number,
                showBottomDivider = true
              ),
              FormMainContentModel.DataList.Data(
                title = "HW Paired",
                sideText = hwInfo.hw_paired.toString()
              )
            )
        )
      },
      event.platform_info?.let { platformInfo ->
        FormMainContentModel.DataList(
          items =
            immutableListOf(
              FormMainContentModel.DataList.Data(
                title = "App ID",
                sideText = platformInfo.app_id,
                showBottomDivider = true
              ),
              FormMainContentModel.DataList.Data(
                title = "Application Version",
                sideText = platformInfo.application_version,
                showBottomDivider = true
              ),
              FormMainContentModel.DataList.Data(
                title = "Client Type",
                sideText = platformInfo.client_type.name,
                showBottomDivider = true
              ),
              FormMainContentModel.DataList.Data(
                title = "OS Type",
                sideText = platformInfo.os_type.name,
                showBottomDivider = true
              ),
              FormMainContentModel.DataList.Data(
                title = "OS Version",
                sideText = platformInfo.os_version,
                showBottomDivider = true
              ),
              FormMainContentModel.DataList.Data(
                title = "Device ID",
                sideText = platformInfo.device_id,
                showBottomDivider = true
              ),
              FormMainContentModel.DataList.Data(
                title = "Device Make",
                sideText = platformInfo.device_make,
                showBottomDivider = true
              ),
              FormMainContentModel.DataList.Data(
                title = "Device Model",
                sideText = platformInfo.device_model
              )
            )
        )
      }
    ),
  primaryButton = null,
  // This is only used by the debug menu, it doesn't need a screen ID
  id = null
)
