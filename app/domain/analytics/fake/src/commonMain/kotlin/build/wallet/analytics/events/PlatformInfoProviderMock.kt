package build.wallet.analytics.events

import build.wallet.analytics.v1.Client.CLIENT_ANDROID_APP
import build.wallet.analytics.v1.DeviceLockState.DEVICE_LOCK_STATE_ENABLED
import build.wallet.analytics.v1.EmulatorState.EMULATOR_STATE_NOT_DETECTED
import build.wallet.analytics.v1.OSType.OS_TYPE_ANDROID
import build.wallet.analytics.v1.PlatformInfo
import build.wallet.analytics.v1.SecurityPatchAgeBucket.SECURITY_PATCH_AGE_BUCKET_0_TO_30_DAYS
import build.wallet.analytics.v1.StrongboxAvailability.STRONGBOX_AVAILABILITY_SUPPORTED

class PlatformInfoProviderMock : PlatformInfoProvider {
  override fun getPlatformInfo(): PlatformInfo {
    return PlatformInfo(
      device_id = "android_id_1",
      client_type = CLIENT_ANDROID_APP,
      application_version = "2023.1.3",
      os_type = OS_TYPE_ANDROID,
      os_version = "version_num_1",
      device_make = "make_1",
      device_model = "model_1",
      app_id = "app.id",
      os_security_patch = "security_patch_1",
      os_security_patch_age_bucket = SECURITY_PATCH_AGE_BUCKET_0_TO_30_DAYS,
      device_lock_state = DEVICE_LOCK_STATE_ENABLED,
      emulator_state = EMULATOR_STATE_NOT_DETECTED,
      android_strongbox_availability = STRONGBOX_AVAILABILITY_SUPPORTED
    )
  }
}
