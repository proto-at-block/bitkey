package build.wallet.ui.components.card

private const val BITKEY_DEVICE_SCREEN_DIFFUSE_BRIGHTNESS = 0.0f

internal data class BitkeyDeviceSceneDebugSettings(
  val useDefaultLighting: Boolean,
  val lightingIntensity: Float,
  val ambientLightIntensity: Float,
  val fillLightIntensity: Float,
  val fillLightAzimuthDegrees: Float,
  val fillLightElevationDegrees: Float,
  val hdriRotationDegrees: Float,
  val hdriElevationDegrees: Float,
  val cameraOffsetX: Float,
  val cameraOffsetY: Float,
  val cameraDistance: Float,
  val cameraFieldOfView: Float,
  val modelScale: Float,
  val waitingModelScale: Float,
  val rotateTranslationY: Float,
  val waitingRestTranslationY: Float,
  val waitingIntroTargetTranslationY: Float,
  val screenDiffuseBrightness: Float,
)

internal object BitkeyDeviceSceneDebugTuning {
  fun currentSettings() =
    BitkeyDeviceSceneDebugSettings(
      useDefaultLighting = BITKEY_DEVICE_USE_DEFAULT_LIGHTING,
      lightingIntensity = BITKEY_DEVICE_LIGHTING_INTENSITY,
      ambientLightIntensity = BITKEY_DEVICE_AMBIENT_LIGHT_INTENSITY,
      fillLightIntensity = BITKEY_DEVICE_FILL_LIGHT_INTENSITY,
      fillLightAzimuthDegrees = BITKEY_DEVICE_FILL_LIGHT_AZIMUTH_DEGREES,
      fillLightElevationDegrees = BITKEY_DEVICE_FILL_LIGHT_ELEVATION_DEGREES,
      hdriRotationDegrees = BITKEY_DEVICE_HDRI_ROTATION_DEGREES,
      hdriElevationDegrees = BITKEY_DEVICE_HDRI_ELEVATION_DEGREES,
      cameraOffsetX = BITKEY_DEVICE_CAMERA_OFFSET_X,
      cameraOffsetY = BITKEY_DEVICE_CAMERA_OFFSET_Y,
      cameraDistance = BITKEY_DEVICE_CAMERA_DISTANCE,
      cameraFieldOfView = BITKEY_DEVICE_CAMERA_FIELD_OF_VIEW,
      modelScale = BITKEY_DEVICE_MODEL_SCALE,
      waitingModelScale = BITKEY_DEVICE_WAITING_MODEL_SCALE,
      rotateTranslationY = BITKEY_DEVICE_W3_ROTATE_TRANSLATION_Y,
      waitingRestTranslationY = BITKEY_DEVICE_WAITING_REST_TRANSLATION_Y,
      waitingIntroTargetTranslationY = BITKEY_DEVICE_WAITING_INTRO_TARGET_TRANSLATION_Y,
      screenDiffuseBrightness = BITKEY_DEVICE_SCREEN_DIFFUSE_BRIGHTNESS
    )
}
