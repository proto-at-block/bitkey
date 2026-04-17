package build.wallet.ui.components.card

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import bitkey.account.HardwareType
import build.wallet.statemachine.core.form.FORM_DS_V2_WAITING_REVEAL_DURATION_MILLIS
import build.wallet.statemachine.core.form.FormDsV2WaitingRevealEasing
import build.wallet.statemachine.core.form.FormMainContentModel.DeviceStatusCard
import build.wallet.ui.components.video.VideoPlayer
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.WalletTheme
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectZero
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSString
import platform.Foundation.NSBundle
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.NSURL
import platform.QuartzCore.CADisplayLink
import platform.SceneKit.SCNCamera
import platform.SceneKit.SCNLight
import platform.SceneKit.SCNLightTypeAmbient
import platform.SceneKit.SCNLightTypeDirectional
import platform.SceneKit.SCNMaterial
import platform.SceneKit.SCNMatrix4
import platform.SceneKit.SCNMatrix4Translate
import platform.SceneKit.SCNNode
import platform.SceneKit.SCNPlane
import platform.SceneKit.SCNScene
import platform.SceneKit.SCNVector3Make
import platform.SceneKit.SCNView
import platform.SceneKit.SCNWrapModeRepeat
import platform.UIKit.UIColor
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateCancelled
import platform.UIKit.UIGestureRecognizerStateChanged
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIGestureRecognizerStateFailed
import platform.UIKit.UIPanGestureRecognizer
import platform.UIKit.UITraitCollection
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView
import platform.UIKit.drawInRect
import platform.UIKit.sizeWithAttributes
import platform.darwin.NSObject
import platform.darwin.sel_registerName
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

private const val BITKEY_DEVICE_MODEL_NAME_W3_ROTATE = "Bitkey_W3_bitkeylogo"
private const val BITKEY_DEVICE_MODEL_NAME_W3_WAITING = "Bitkey_W3_bitkeylogo"
private const val BITKEY_DEVICE_MODEL_TYPE = "usdz"
private const val BITKEY_DEVICE_HDRI_NAME = "W3_HDRI"
private const val BITKEY_DEVICE_HDRI_TYPE = "hdr"
private const val BITKEY_DEVICE_INLAY_NODE_NAME = "Inlay"
private const val BITKEY_DEVICE_INLAY_TEXTURE_NAME = "Bitkey_W3_bitkeylogo_inlay"
private const val BITKEY_DEVICE_INLAY_TEXTURE_TYPE = "png"
private const val BITKEY_DEVICE_SERIAL_TEXT_FONT_POSTSCRIPT_NAME = "CashSansMono-Regular"
private const val BITKEY_DEVICE_SCREEN_NODE_NAME = "Screen"
private const val BITKEY_DEVICE_SCREEN_VIDEO_NAME = "bitkey_taptophoneinsert"
private const val BITKEY_DEVICE_SCREEN_VIDEO_TYPE = "mp4"
private const val BITKEY_DEVICE_ROTATION_RADIANS_PER_POINT = 0.0077f
internal const val BITKEY_DEVICE_CAMERA_OFFSET_X = 0.0f
internal const val BITKEY_DEVICE_CAMERA_OFFSET_Y = 0.0f
internal const val BITKEY_DEVICE_CAMERA_DISTANCE = 0.17f
internal const val BITKEY_DEVICE_CAMERA_FIELD_OF_VIEW = 25.0f
private const val BITKEY_DEVICE_CAMERA_NEAR_Z = 0.001
private const val BITKEY_DEVICE_CAMERA_FAR_Z = 10.0
private const val BITKEY_DEVICE_WAITING_PREFERRED_FRAMES_PER_SECOND = 60L
private const val BITKEY_DEVICE_INTERACTION_PREFERRED_FRAMES_PER_SECOND = 120L
internal const val BITKEY_DEVICE_MODEL_SCALE = 1.0f
internal const val BITKEY_DEVICE_WAITING_MODEL_SCALE = 0.85f
private const val BITKEY_DEVICE_INERTIA_ENABLED = true
private const val BITKEY_DEVICE_INERTIA_VELOCITY_SCALE = 0.0072f
private const val BITKEY_DEVICE_INERTIA_DAMPING = 5.0f
private const val BITKEY_DEVICE_INERTIA_STOP_THRESHOLD = 0.02f
private const val BITKEY_DEVICE_ROTATION_SNAP_RATE = 9.0f
private const val BITKEY_DEVICE_ROTATION_SNAP_THRESHOLD = 0.002f
internal const val BITKEY_DEVICE_WAITING_REST_TRANSLATION_Y = -0.0065f
internal const val BITKEY_DEVICE_WAITING_INTRO_TARGET_TRANSLATION_Y = -0.002f
private const val BITKEY_DEVICE_WAITING_BOB_AMPLITUDE_Y = 0.0011f
private const val BITKEY_DEVICE_WAITING_BOB_PERIOD_SECONDS = 2.8f
internal const val BITKEY_DEVICE_W3_ROTATE_TRANSLATION_Y = 0.0f
internal const val BITKEY_DEVICE_HDRI_ROTATION_DEGREES = -94.0f
internal const val BITKEY_DEVICE_HDRI_ELEVATION_DEGREES = 23.0f
internal const val BITKEY_DEVICE_LIGHTING_INTENSITY = 0.25f
internal const val BITKEY_DEVICE_AMBIENT_LIGHT_INTENSITY = 1000.0f
internal const val BITKEY_DEVICE_FILL_LIGHT_INTENSITY = 0.0f
internal const val BITKEY_DEVICE_FILL_LIGHT_AZIMUTH_DEGREES = 0.0f
internal const val BITKEY_DEVICE_FILL_LIGHT_ELEVATION_DEGREES = 35.0f
internal const val BITKEY_DEVICE_USE_DEFAULT_LIGHTING = false
private const val BITKEY_DEVICE_INLAY_TEXTURE_BASE_HEIGHT = 407.0
private const val BITKEY_DEVICE_SERIAL_OVERLAY_RECT_LEFT = 264.0
private const val BITKEY_DEVICE_SERIAL_OVERLAY_RECT_TOP = 15.0
private const val BITKEY_DEVICE_SERIAL_OVERLAY_RECT_RIGHT = 1472.0
private const val BITKEY_DEVICE_SERIAL_OVERLAY_RECT_BOTTOM = 143.0
private const val BITKEY_DEVICE_SERIAL_TEXT_LEFT = 276.0
private const val BITKEY_DEVICE_SERIAL_TEXT_COLOR_WHITE = 104.0 / 255.0
private const val BITKEY_DEVICE_SERIAL_BACKGROUND_WHITE = 50.0 / 255.0
private const val BITKEY_DEVICE_SERIAL_TEXT_SIZE = 92.0
private const val BITKEY_DEVICE_SERIAL_TEXT_MIN_SIZE = 60.0
private const val BITKEY_DEVICE_SERIAL_TEXT_PADDING_RIGHT = 40.0
private const val BITKEY_DEVICE_SERIAL_TEXT_SCALE_REDUCTION = 0.96
private const val BITKEY_DEVICE_SCREEN_OVERLAY_TEXTURE_SIZE = 466.0
private const val BITKEY_DEVICE_SCREEN_OVERLAY_ICON_SIZE = 28.0
private const val BITKEY_DEVICE_SCREEN_OVERLAY_TEXT_SIZE = 27.0
private const val BITKEY_DEVICE_SCREEN_OVERLAY_BOTTOM_PADDING = 38.0
private const val BITKEY_DEVICE_SCREEN_OVERLAY_ICON_TEXT_SPACING = 8.0
private const val BITKEY_DEVICE_SCREEN_OVERLAY_Z_OFFSET = 0.0002
private const val BITKEY_DEVICE_SCREEN_OVERLAY_PLANE_WIDTH = 0.0364222414791584
private const val BITKEY_DEVICE_SCREEN_OVERLAY_PLANE_HEIGHT = 0.0364222414791584
private const val BITKEY_DEVICE_SCREEN_OVERLAY_TEXT_WHITE = 173.0 / 255.0
private const val BITKEY_DEVICE_SCREEN_OVERLAY_ICON_WHITE = 173.0 / 255.0
private const val BITKEY_DEVICE_SCREEN_OVERLAY_LOW_RED = 248.0 / 255.0
private const val BITKEY_DEVICE_SCREEN_OVERLAY_LOW_GREEN = 71.0 / 255.0
private const val BITKEY_DEVICE_SCREEN_OVERLAY_LOW_BLUE = 82.0 / 255.0

private data class BitkeyDeviceScreenBatteryDisplayStyle(
  val fillFraction: Double,
  val iconColor: UIColor,
  val textColor: UIColor,
)

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun BitkeyDeviceMedia(
  modifier: Modifier,
  content: DeviceStatusCard.VideoContent,
  serialNumber: String?,
  batteryPercentage: Int?,
  hardwareType: HardwareType,
  interactionState: BitkeyDeviceMediaInteractionState,
  shouldPlayWaitingIntro: Boolean,
) {
  when (content) {
    DeviceStatusCard.VideoContent.BITKEY_ROTATE,
    DeviceStatusCard.VideoContent.BITKEY_WAITING_3D,
    -> {
      val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
      val supports3DMedia = supportsBitkeyDevice3DMedia(hardwareType)
      val screenVideoResourcePath =
        if (content == DeviceStatusCard.VideoContent.BITKEY_WAITING_3D) {
          loadBitkeyDeviceScreenVideoPath()
        } else {
          null
        }
      val modelScene = remember(isDesignSystemV2Enabled, supports3DMedia, hardwareType, content) {
        if (!isDesignSystemV2Enabled || !supports3DMedia) {
          null
        } else {
          loadBitkeyDeviceScene(
            content = content
          )
        }
      }
      val lightingEnvironmentUrl = remember(isDesignSystemV2Enabled) {
        if (!isDesignSystemV2Enabled) {
          null
        } else {
          loadBitkeyDeviceLightingEnvironmentUrl()
        }
      }
      val sceneInteractionDelegate = remember(modelScene, content) {
        modelScene?.let {
          BitkeyDeviceSceneInteractionDelegate(
            content = content
          )
        }
      }
      val debugSettings = BitkeyDeviceSceneDebugTuning.currentSettings()
      val sceneBackgroundColor =
        when {
          content == DeviceStatusCard.VideoContent.BITKEY_WAITING_3D -> WalletTheme.colors.background
          isDesignSystemV2Enabled -> WalletTheme.colors.secondary
          else -> Color.Transparent
        }

      DisposableEffect(interactionState, sceneInteractionDelegate) {
        interactionState.setDelegate(sceneInteractionDelegate)
        onDispose {
          interactionState.setDelegate(null)
        }
      }

      if (modelScene == null) {
        if (content == DeviceStatusCard.VideoContent.BITKEY_WAITING_3D) {
          VideoPlayer(
            modifier = modifier,
            resourcePath = bitkeyDeviceVideoResource(content),
            isLooping = false,
            autoStart = true
          )
        } else {
          Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
          ) {
            VideoPlayer(
              modifier = Modifier.size(BitkeyDeviceFallbackMediaSize),
              resourcePath = bitkeyDeviceVideoResource(content),
              isLooping = true,
              autoStart = true
            )
          }
        }
      } else {
        UIKitView(
          factory = {
            val sceneView =
              createBitkeyDeviceSceneView(
                scene = modelScene,
                lightingEnvironmentUrl = lightingEnvironmentUrl,
                debugSettings = debugSettings,
                backgroundColor = sceneBackgroundColor
              )
            sceneInteractionDelegate?.updateScreenVideoResourcePath(screenVideoResourcePath)
            sceneInteractionDelegate?.updateSerialNumber(serialNumber)
            sceneInteractionDelegate?.updateBatteryPercentage(batteryPercentage)
            sceneInteractionDelegate?.sceneView = sceneView
            sceneInteractionDelegate?.updateDebugSettings(debugSettings)
            BitkeyDeviceSceneContainerView(sceneView).apply {
              applyBackgroundColor(
                color = sceneBackgroundColor.toUIColor(),
                useThemeBackgroundColor =
                  isDesignSystemV2Enabled &&
                    content == DeviceStatusCard.VideoContent.BITKEY_WAITING_3D,
                useSecondaryThemeColor =
                  isDesignSystemV2Enabled &&
                    content != DeviceStatusCard.VideoContent.BITKEY_WAITING_3D
              )
            }
          },
          update = { containerView ->
            containerView.applyBackgroundColor(
              color = sceneBackgroundColor.toUIColor(),
              useThemeBackgroundColor =
                isDesignSystemV2Enabled &&
                  content == DeviceStatusCard.VideoContent.BITKEY_WAITING_3D,
              useSecondaryThemeColor =
                isDesignSystemV2Enabled &&
                  content != DeviceStatusCard.VideoContent.BITKEY_WAITING_3D
            )
            sceneInteractionDelegate?.updateWaitingIntroTrigger(shouldPlayWaitingIntro)
            sceneInteractionDelegate?.updateSceneBackground(sceneBackgroundColor)
            sceneInteractionDelegate?.updateScreenVideoResourcePath(screenVideoResourcePath)
            sceneInteractionDelegate?.updateSerialNumber(serialNumber)
            sceneInteractionDelegate?.updateBatteryPercentage(batteryPercentage)
            sceneInteractionDelegate?.updateDebugSettings(debugSettings)
          },
          modifier = modifier,
          onRelease = {
            sceneInteractionDelegate?.stopMomentum()
            sceneInteractionDelegate?.sceneView = null
          },
          properties = UIKitInteropProperties(
            isInteractive = false,
            isNativeAccessibilityEnabled = false
          )
        )
      }
    }
  }
}

@OptIn(ExperimentalForeignApi::class)
private fun createBitkeyDeviceSceneView(
  scene: SCNScene,
  lightingEnvironmentUrl: NSURL?,
  debugSettings: BitkeyDeviceSceneDebugSettings,
  backgroundColor: Color,
): SCNView =
  SCNView(
    frame = cValue { CGRectZero },
    options = null
  ).apply {
    this.scene = scene
    opaque = false
    this.backgroundColor = backgroundColor.toUIColor()
    clipsToBounds = true
    if (lightingEnvironmentUrl == null) {
      autoenablesDefaultLighting = true
    } else {
      autoenablesDefaultLighting = BITKEY_DEVICE_USE_DEFAULT_LIGHTING
      scene.background.contents = backgroundColor.toUIColor()
      scene.lightingEnvironment.contents = lightingEnvironmentUrl
      scene.lightingEnvironment.intensity = debugSettings.lightingIntensity.toDouble()
      scene.lightingEnvironment.wrapS = SCNWrapModeRepeat
    }
    allowsCameraControl = false
    playing = false
    rendersContinuously = false
    preferredFramesPerSecond = BITKEY_DEVICE_WAITING_PREFERRED_FRAMES_PER_SECOND
  }

@OptIn(ExperimentalForeignApi::class)
private class BitkeyDeviceSceneContainerView(
  private val sceneView: SCNView,
) : UIView(cValue { CGRectZero }) {
  private var fallbackBackgroundColor = UIColor.clearColor
  private var usesThemeBackgroundColor = false
  private var usesSecondaryThemeColor = false

  init {
    opaque = false
    backgroundColor = UIColor.clearColor
    clipsToBounds = true
    addSubview(sceneView)
  }

  fun applyBackgroundColor(
    color: UIColor,
    useThemeBackgroundColor: Boolean,
    useSecondaryThemeColor: Boolean,
  ) {
    fallbackBackgroundColor = color
    usesThemeBackgroundColor = useThemeBackgroundColor
    usesSecondaryThemeColor = useSecondaryThemeColor
    applyResolvedBackgroundColor()
  }

  override fun layoutSubviews() {
    super.layoutSubviews()
    sceneView.setFrame(bounds)
  }

  override fun traitCollectionDidChange(previousTraitCollection: UITraitCollection?) {
    super.traitCollectionDidChange(previousTraitCollection)
    applyResolvedBackgroundColor()
  }

  private fun applyResolvedBackgroundColor() {
    val resolvedColor =
      if (usesThemeBackgroundColor) {
        fallbackBackgroundColor
      } else if (usesSecondaryThemeColor) {
        sceneSecondaryBackgroundColorForTraitCollection(traitCollection)
      } else {
        fallbackBackgroundColor
      }
    backgroundColor = resolvedColor
    sceneView.backgroundColor = resolvedColor
    sceneView.scene?.background?.contents = resolvedColor
  }
}

@OptIn(ExperimentalForeignApi::class)
private fun loadBitkeyDeviceScene(content: DeviceStatusCard.VideoContent): SCNScene? {
  val modelName =
    when (content) {
      DeviceStatusCard.VideoContent.BITKEY_ROTATE -> BITKEY_DEVICE_MODEL_NAME_W3_ROTATE
      DeviceStatusCard.VideoContent.BITKEY_WAITING_3D -> BITKEY_DEVICE_MODEL_NAME_W3_WAITING
    }
  val modelPath = NSBundle.mainBundle.pathForResource(
    name = modelName,
    ofType = BITKEY_DEVICE_MODEL_TYPE
  ) ?: return null

  val modelUrl = NSURL.fileURLWithPath(modelPath)
  return SCNScene.sceneWithURL(
    url = modelUrl,
    options = null,
    error = null
  )
}

@OptIn(ExperimentalForeignApi::class)
private fun loadBitkeyDeviceLightingEnvironmentUrl(): NSURL? {
  val environmentPath = NSBundle.mainBundle.pathForResource(
    name = BITKEY_DEVICE_HDRI_NAME,
    ofType = BITKEY_DEVICE_HDRI_TYPE
  ) ?: return null

  return NSURL.fileURLWithPath(environmentPath)
}

@OptIn(ExperimentalForeignApi::class)
private fun loadBitkeyDeviceScreenVideoPath(): String? =
  NSBundle.mainBundle.pathForResource(
    name = BITKEY_DEVICE_SCREEN_VIDEO_NAME,
    ofType = BITKEY_DEVICE_SCREEN_VIDEO_TYPE
  )

@OptIn(ExperimentalForeignApi::class)
private fun loadBitkeyDeviceInlayTexturePath(): String? =
  NSBundle.mainBundle.pathForResource(
    name = BITKEY_DEVICE_INLAY_TEXTURE_NAME,
    ofType = BITKEY_DEVICE_INLAY_TEXTURE_TYPE
  )

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun BitkeyDeviceMediaInteractionOverlay(
  modifier: Modifier,
  interactionState: BitkeyDeviceMediaInteractionState,
) {
  val gestureHandler = remember(interactionState) {
    BitkeyDeviceScenePanInteractionHandler(interactionState = interactionState)
  }
  val gestureRecognizerDelegate = remember {
    BitkeyDeviceScenePanGestureRecognizerDelegate()
  }

  UIKitView(
    factory = {
      BitkeyDeviceMediaInteractionOverlayView(
        gestureHandler = gestureHandler,
        gestureRecognizerDelegate = gestureRecognizerDelegate
      )
    },
    modifier = modifier,
    properties = UIKitInteropProperties(
      isInteractive = true,
      isNativeAccessibilityEnabled = false
    )
  )
}

internal actual fun supportsBitkeyDevice3DMedia(hardwareType: HardwareType): Boolean =
  hardwareType == HardwareType.W3

@Composable
internal actual fun supportsInteractiveBitkeyWaitingMedia(hardwareType: HardwareType): Boolean =
  supportsBitkeyDevice3DMedia(hardwareType)

@OptIn(ExperimentalForeignApi::class)
private class BitkeyDeviceMediaInteractionOverlayView(
  gestureHandler: BitkeyDeviceScenePanInteractionHandler,
  gestureRecognizerDelegate: BitkeyDeviceScenePanGestureRecognizerDelegate,
) : UIView(cValue { CGRectZero }) {
  private val panGestureRecognizer =
    UIPanGestureRecognizer(
      target = gestureHandler,
      action = sel_registerName("handlePan:")
    ).apply {
      minimumNumberOfTouches = 1u
      maximumNumberOfTouches = 1u
      cancelsTouchesInView = true
      delegate = gestureRecognizerDelegate
    }

  init {
    backgroundColor = UIColor.clearColor
    addGestureRecognizer(panGestureRecognizer)
  }
}

@OptIn(ExperimentalForeignApi::class)
private class BitkeyDeviceScenePanGestureRecognizerDelegate :
  NSObject(),
  UIGestureRecognizerDelegateProtocol {
  override fun gestureRecognizerShouldBegin(gestureRecognizer: UIGestureRecognizer): Boolean {
    val panGestureRecognizer = gestureRecognizer as? UIPanGestureRecognizer ?: return true
    val velocity = panGestureRecognizer.velocityInView(panGestureRecognizer.view)
    return velocity.useContents { abs(x) > abs(y) }
  }
}

@OptIn(ExperimentalForeignApi::class)
internal class BitkeyDeviceScenePanInteractionHandler(
  private val interactionState: BitkeyDeviceMediaInteractionState,
) : NSObject() {
  @ObjCAction
  fun handlePan(recognizer: UIPanGestureRecognizer) {
    when (recognizer.state) {
      UIGestureRecognizerStateBegan -> {
        interactionState.stopMomentum()
        interactionState.beginInteraction(
          locationX = 0.0f,
          locationY = 0.0f,
          viewportWidth = 0.0f,
          viewportHeight = 0.0f
        )
        recognizer.setTranslation(
          translation = CGPointMake(0.0, 0.0),
          inView = recognizer.view
        )
      }
      UIGestureRecognizerStateChanged -> {
        val translation = recognizer.translationInView(recognizer.view)
        interactionState.continueInteraction(
          locationX = translation.useContents { x.toFloat() },
          locationY = translation.useContents { y.toFloat() },
          viewportWidth = 0.0f,
          viewportHeight = 0.0f
        )
        recognizer.setTranslation(
          translation = CGPointMake(0.0, 0.0),
          inView = recognizer.view
        )
      }
      UIGestureRecognizerStateEnded,
      UIGestureRecognizerStateCancelled,
      UIGestureRecognizerStateFailed,
      -> {
        val velocity = recognizer.velocityInView(recognizer.view)
        interactionState.endInteraction(
          locationX = 0.0f,
          locationY = 0.0f,
          velocityX = velocity.useContents { x.toFloat() },
          velocityY = velocity.useContents { y.toFloat() },
          viewportWidth = 0.0f,
          viewportHeight = 0.0f
        )
        recognizer.setTranslation(
          translation = CGPointMake(0.0, 0.0),
          inView = recognizer.view
        )
      }
    }
  }
}

@OptIn(ExperimentalForeignApi::class)
@Suppress("LargeClass")
internal class BitkeyDeviceSceneInteractionDelegate(
  private val content: DeviceStatusCard.VideoContent,
  private val lightingEnvironmentUrl: NSURL? = loadBitkeyDeviceLightingEnvironmentUrl(),
) : BitkeyDeviceMediaInteractionDelegate {
  private enum class RotationMotionMode {
    INTRO,
    IDLE,
    MOMENTUM,
    SNAP,
  }

  var sceneView: SCNView? = null
    set(value) {
      if (field === value) {
        return
      }

      if (value == null) {
        releaseAppLifecycleObserver()
        removeScreenBatteryOverlayNode()
        restoreDefaultInlayMaterialContents()
        restoreDefaultScreenMaterialContents()
        releaseScreenVideoPlayer()
        inlayMaterial = null
        screenNode = null
        screenMaterial = null
        customInlayImage = null
        customInlaySerialNumber = null
        customScreenBatteryImage = null
        customScreenBatteryPercentage = null
        field = null
        stopAllAnimation()
        return
      }
      field = value
      ensureAppLifecycleObserver()
      configureCameraController()
      applySceneConfiguration()
      applyInlayMaterial()
      applyScreenVideoMaterial()
      applyScreenBatteryOverlay()
      if (shouldPlayWaitingIntro) {
        startWaitingIntroIfNeeded()
      } else {
        startWaitingIdleMotionIfNeeded()
      }
    }

  private var configuredScene: SCNScene? = null
  private val modelRootNode = SCNNode()
  private val modelNormalizationNode = SCNNode()
  private val orbitCameraNode =
    SCNNode().apply {
      camera =
        SCNCamera.camera().apply {
          fieldOfView = BITKEY_DEVICE_CAMERA_FIELD_OF_VIEW.toDouble()
          zNear = BITKEY_DEVICE_CAMERA_NEAR_Z
          zFar = BITKEY_DEVICE_CAMERA_FAR_Z
        }
      position = SCNVector3Make(
        x = 0.0f,
        y = 0.0f,
        z = BITKEY_DEVICE_CAMERA_DISTANCE
      )
    }
  private val ambientLightNode =
    SCNNode().apply {
      light =
        SCNLight.light().apply {
          type = SCNLightTypeAmbient
          intensity = BITKEY_DEVICE_AMBIENT_LIGHT_INTENSITY.toDouble()
          color = UIColor.whiteColor
        }
    }
  private val fillLightNode =
    SCNNode().apply {
      light =
        SCNLight.light().apply {
          type = SCNLightTypeDirectional
          intensity = BITKEY_DEVICE_FILL_LIGHT_INTENSITY.toDouble()
          color = UIColor.whiteColor
          castsShadow = false
        }
    }
  private var currentModelRotationY = 0.0f
  private var currentModelTranslationY = 0.0f
  private var sceneBackgroundColor = Color.Transparent
  private var angularVelocityY = 0.0f
  private var snapTargetRotationY = 0.0f
  private var serialNumber: String? = null
  private var batteryPercentage: Int? = null
  private var inlayMaterial: SCNMaterial? = null
  private var screenNode: SCNNode? = null
  private var screenVideoResourcePath: String? = null
  private var screenMaterial: SCNMaterial? = null
  private var screenBatteryOverlayNode: SCNNode? = null
  private var screenVideoPlayer: AVPlayer? = null
  private var screenVideoPlaybackObserver: BitkeyDeviceScreenVideoPlaybackObserver? = null
  private var screenVideoObservedItem: AVPlayerItem? = null
  private var defaultScreenDiffuseContents: Any? = null
  private var defaultScreenEmissionContents: Any? = null
  private var hasCapturedDefaultScreenMaterialContents = false
  private var defaultInlayDiffuseContents: Any? = null
  private var hasCapturedDefaultInlayMaterialContents = false
  private var customInlaySerialNumber: String? = null
  private var customInlayImage: platform.UIKit.UIImage? = null
  private var customScreenBatteryPercentage: Int? = null
  private var customScreenBatteryImage: platform.UIKit.UIImage? = null
  private var isApplicationActive = true
  private var isUserInteracting = false
  private var shouldResumeScreenVideoPlayback = false
  private var introElapsedSeconds = 0.0f
  private var introStartRotationY = 0.0f
  private var introStartTranslationY = 0.0f
  private var bobElapsedSeconds = 0.0f
  private var shouldPlayWaitingIntro = false
  private var hasPlayedWaitingIntro = false
  private var rotationMotionMode: RotationMotionMode? = null
  private var momentumDisplayLink: CADisplayLink? = null
  private var appLifecycleObserver: BitkeyDeviceAppLifecycleObserver? = null
  private var debugSettings = BitkeyDeviceSceneDebugTuning.currentSettings()
  private val momentumHandler =
    BitkeyDeviceSceneMomentumHandler { displayLink ->
      handleMomentumFrame(displayLink)
    }

  fun updateSceneBackground(sceneBackgroundColor: Color) {
    this.sceneBackgroundColor = sceneBackgroundColor
    applySceneConfiguration()
  }

  fun updateDebugSettings(settings: BitkeyDeviceSceneDebugSettings) {
    if (debugSettings == settings) return

    debugSettings = settings
    currentModelTranslationY = defaultModelTranslationY()
    applySceneConfiguration()
    applyScreenVideoMaterial()
    applyScreenBatteryOverlay()
  }

  fun updateScreenVideoResourcePath(resourcePath: String?) {
    if (screenVideoResourcePath != resourcePath) {
      restoreDefaultScreenMaterialContents()
      releaseScreenVideoPlayer()
      screenVideoResourcePath = resourcePath
    }
    applyScreenVideoMaterial()
  }

  fun updateSerialNumber(serialNumber: String?) {
    val normalizedSerialNumber = serialNumber?.trim()?.takeIf(String::isNotEmpty)
    if (this.serialNumber == normalizedSerialNumber) return

    this.serialNumber = normalizedSerialNumber
    customInlayImage = null
    customInlaySerialNumber = null
    applyInlayMaterial()
  }

  fun updateBatteryPercentage(batteryPercentage: Int?) {
    val normalizedBatteryPercentage = batteryPercentage?.coerceIn(0, 100)
    if (this.batteryPercentage == normalizedBatteryPercentage) return

    this.batteryPercentage = normalizedBatteryPercentage
    customScreenBatteryImage = null
    customScreenBatteryPercentage = null
    applyScreenBatteryOverlay()
  }

  fun updateWaitingIntroTrigger(shouldPlayWaitingIntro: Boolean) {
    if (content != DeviceStatusCard.VideoContent.BITKEY_WAITING_3D) return
    if (this.shouldPlayWaitingIntro == shouldPlayWaitingIntro) return

    this.shouldPlayWaitingIntro = shouldPlayWaitingIntro
    if (shouldPlayWaitingIntro) {
      startWaitingIntroIfNeeded()
    } else {
      startWaitingIdleMotionIfNeeded()
    }
  }

  override fun beginInteraction(
    locationX: Float,
    locationY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
  ) {
    isUserInteracting = true
    stopAllAnimation()
  }

  override fun continueInteraction(
    locationX: Float,
    locationY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
  ) {
    currentModelRotationY += locationX * BITKEY_DEVICE_ROTATION_RADIANS_PER_POINT
    applyModelTransform()
  }

  override fun endInteraction(
    locationX: Float,
    locationY: Float,
    velocityX: Float,
    velocityY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
  ) {
    isUserInteracting = false
    if (!BITKEY_DEVICE_INERTIA_ENABLED) {
      startSnapToNearestAnchor()
      return
    }

    angularVelocityY = velocityX * BITKEY_DEVICE_INERTIA_VELOCITY_SCALE
    if (abs(angularVelocityY) < BITKEY_DEVICE_INERTIA_STOP_THRESHOLD) {
      startSnapToNearestAnchor()
      return
    }

    startMomentumMotion()
  }

  override fun stopMomentum() {
    stopAllAnimation()
  }

  private fun stopAllAnimation() {
    angularVelocityY = 0.0f
    introElapsedSeconds = 0.0f
    bobElapsedSeconds = 0.0f
    rotationMotionMode = null
    momentumDisplayLink?.invalidate()
    momentumDisplayLink = null
    syncSceneViewRenderingState()
  }

  private fun handleMomentumFrame(displayLink: CADisplayLink) {
    val dt = displayLink.duration.toFloat().takeIf { it > 0.0f } ?: (1.0f / 60.0f)
    when (rotationMotionMode) {
      RotationMotionMode.INTRO -> {
        introElapsedSeconds += dt
        val introProgress =
          (
            introElapsedSeconds /
              (FORM_DS_V2_WAITING_REVEAL_DURATION_MILLIS.toFloat() / 1000.0f)
          ).coerceIn(0.0f, 1.0f)
        val easedProgress = FormDsV2WaitingRevealEasing.transform(introProgress)
        currentModelRotationY = lerp(introStartRotationY, PI.toFloat(), easedProgress)
        currentModelTranslationY = lerp(
          introStartTranslationY,
          debugSettings.waitingIntroTargetTranslationY,
          easedProgress
        )
        applyModelTransform()

        if (introProgress >= 1.0f) {
          currentModelRotationY = PI.toFloat()
          currentModelTranslationY = debugSettings.waitingIntroTargetTranslationY
          bobElapsedSeconds = 0.0f
          applyModelTransform()
          startIdleMotion()
        }
      }
      RotationMotionMode.IDLE -> {
        bobElapsedSeconds += dt
        applyModelTransform()
      }
      RotationMotionMode.MOMENTUM -> {
        currentModelRotationY += angularVelocityY * dt
        applyModelTransform()

        angularVelocityY *= exp(-BITKEY_DEVICE_INERTIA_DAMPING * dt)
        if (abs(angularVelocityY) < BITKEY_DEVICE_INERTIA_STOP_THRESHOLD) {
          startSnapToNearestAnchor()
        }
      }
      RotationMotionMode.SNAP -> {
        val deltaToTarget = snapTargetRotationY - currentModelRotationY
        currentModelRotationY += deltaToTarget * (1.0f - exp(-BITKEY_DEVICE_ROTATION_SNAP_RATE * dt))
        applyModelTransform()

        if (abs(deltaToTarget) < BITKEY_DEVICE_ROTATION_SNAP_THRESHOLD) {
          currentModelRotationY = snapTargetRotationY
          applyModelTransform()
          startIdleMotion()
        }
      }
      null -> stopAllAnimation()
    }
  }

  private fun configureCameraController() {
    val sceneView = sceneView ?: return
    val scene = sceneView.scene ?: return

    if (configuredScene !== scene) {
      val rootChildNodes = scene.rootNode.childNodes.mapNotNull { it as? SCNNode }
      modelRootNode.removeFromParentNode()
      modelNormalizationNode.removeFromParentNode()
      ambientLightNode.removeFromParentNode()
      fillLightNode.removeFromParentNode()
      modelNormalizationNode.childNodes.mapNotNull { it as? SCNNode }.forEach { childNode ->
        childNode.removeFromParentNode()
      }
      orbitCameraNode.removeFromParentNode()
      scene.rootNode.addChildNode(modelRootNode)
      modelRootNode.addChildNode(modelNormalizationNode)
      rootChildNodes
        .filter {
          it != orbitCameraNode &&
            it != modelRootNode &&
            it != ambientLightNode &&
            it != fillLightNode
        }
        .forEach { childNode ->
          modelNormalizationNode.addChildNode(childNode)
        }
      scene.rootNode.addChildNode(orbitCameraNode)
      scene.rootNode.addChildNode(ambientLightNode)
      scene.rootNode.addChildNode(fillLightNode)
      currentModelRotationY = defaultModelRotationY()
      currentModelTranslationY = defaultModelTranslationY()
      screenNode = null
      screenMaterial =
        modelRootNode
          .findDescendantNamed(BITKEY_DEVICE_SCREEN_NODE_NAME)
          ?.also { screenNode = it }
          ?.geometry
          ?.materials
          ?.firstOrNull() as? SCNMaterial
      inlayMaterial =
        modelRootNode
          .findDescendantNamed(BITKEY_DEVICE_INLAY_NODE_NAME)
          ?.geometry
          ?.materials
          ?.firstOrNull() as? SCNMaterial
      applyModelTransform()
      configuredScene = scene
    }

    sceneView.pointOfView = orbitCameraNode
    applySceneConfiguration()
    applyInlayMaterial()
    applyScreenBatteryOverlay()
    syncSceneViewRenderingState()
  }

  private fun applySceneConfiguration() {
    orbitCameraNode.camera?.fieldOfView = debugSettings.cameraFieldOfView.toDouble()
    orbitCameraNode.position = SCNVector3Make(
      x = debugSettings.cameraOffsetX,
      y = debugSettings.cameraOffsetY,
      z = debugSettings.cameraDistance
    )
    sceneView?.backgroundColor = sceneBackgroundColor.toUIColor()
    sceneView?.superview?.backgroundColor = sceneBackgroundColor.toUIColor()
    sceneView?.scene?.background?.contents = sceneBackgroundColor.toUIColor()
    ambientLightNode.light?.intensity = debugSettings.ambientLightIntensity.toDouble()
    fillLightNode.light?.intensity = debugSettings.fillLightIntensity.toDouble()
    fillLightNode.eulerAngles = SCNVector3Make(
      x = -debugSettings.fillLightElevationDegrees.toRadians(),
      y = debugSettings.fillLightAzimuthDegrees.toRadians(),
      z = 0.0f
    )
    if (lightingEnvironmentUrl == null) {
      sceneView?.autoenablesDefaultLighting = true
    } else {
      sceneView?.autoenablesDefaultLighting = debugSettings.useDefaultLighting
      sceneView?.scene?.lightingEnvironment?.contents = lightingEnvironmentUrl
      sceneView?.scene?.lightingEnvironment?.wrapS = SCNWrapModeRepeat
      sceneView?.scene?.lightingEnvironment?.intensity = debugSettings.lightingIntensity.toDouble()
    }
    applyModelNormalizationTransform()
    applyModelTransform()
    applyLightingEnvironmentTransform()
  }

  private fun applyModelNormalizationTransform() {
    modelNormalizationNode.eulerAngles = normalizedModelEulerAngles()
    val normalizationScale = normalizedModelScale()
    modelNormalizationNode.scale = SCNVector3Make(
      normalizationScale,
      normalizationScale,
      normalizationScale
    )
  }

  private fun applyScreenVideoMaterial() {
    val material = screenMaterial ?: return
    captureDefaultScreenMaterialContentsIfNeeded(material)

    val videoResourcePath = screenVideoResourcePath ?: run {
      restoreDefaultScreenMaterialContents()
      return
    }

    if (screenVideoPlayer == null) {
      val url = NSURL.fileURLWithPath(videoResourcePath)
      val item = AVPlayerItem.playerItemWithURL(url)
      val player =
        AVPlayer().apply {
          actionAtItemEnd = AVPlayerActionAtItemEndNone
          automaticallyWaitsToMinimizeStalling = false
          replaceCurrentItemWithPlayerItem(item)
        }
      val playbackObserver =
        BitkeyDeviceScreenVideoPlaybackObserver {
          restartScreenVideoPlayback()
        }
      NSNotificationCenter.defaultCenter.addObserver(
        observer = playbackObserver,
        selector = sel_registerName("handlePlaybackEnded:"),
        name = AVPlayerItemDidPlayToEndTimeNotification,
        `object` = item
      )
      if (isApplicationActive) {
        player.play()
      } else {
        shouldResumeScreenVideoPlayback = true
      }
      screenVideoObservedItem = item
      screenVideoPlaybackObserver = playbackObserver
      screenVideoPlayer = player
    }

    material.diffuse.contents = debugSettings.screenDiffuseBrightness.toGrayscaleUIColor()
    material.diffuse.contentsTransform = scnMatrix4IdentityValue()
    material.emission.contents = screenVideoPlayer
    material.emission.contentsTransform = scnMatrix4IdentityValue()
    syncSceneViewRenderingState()
  }

  private fun applyInlayMaterial() {
    val material = inlayMaterial ?: return
    captureDefaultInlayMaterialContentsIfNeeded(material)

    val activeSerialNumber = serialNumber ?: run {
      restoreDefaultInlayMaterialContents()
      return
    }

    val inlayImage =
      if (customInlayImage != null && customInlaySerialNumber == activeSerialNumber) {
        customInlayImage
      } else {
        createBitkeyDeviceSerialInlayImage(activeSerialNumber)?.also {
          customInlayImage = it
          customInlaySerialNumber = activeSerialNumber
        }
      } ?: run {
        restoreDefaultInlayMaterialContents()
        return
      }

    material.diffuse.contents = inlayImage
  }

  private fun applyScreenBatteryOverlay() {
    val activeScreenNode = screenNode ?: run {
      removeScreenBatteryOverlayNode()
      return
    }
    val activeBatteryPercentage =
      batteryPercentage?.takeIf { content == DeviceStatusCard.VideoContent.BITKEY_ROTATE } ?: run {
        removeScreenBatteryOverlayNode()
        return
      }
    val overlayImage =
      if (
        customScreenBatteryImage != null &&
        customScreenBatteryPercentage == activeBatteryPercentage
      ) {
        customScreenBatteryImage
      } else {
        createBitkeyDeviceScreenBatteryOverlayImage(activeBatteryPercentage)?.also {
          customScreenBatteryImage = it
          customScreenBatteryPercentage = activeBatteryPercentage
        }
      } ?: run {
        removeScreenBatteryOverlayNode()
        return
      }

    removeScreenBatteryOverlayNode()

    val overlayMaterial =
      SCNMaterial.material().apply {
        diffuse.contents = overlayImage
        emission.contents = overlayImage
        transparent.contents = overlayImage
        doubleSided = false
        readsFromDepthBuffer = true
        writesToDepthBuffer = true
      }
    val overlayPlane =
      SCNPlane.planeWithWidth(
        width = BITKEY_DEVICE_SCREEN_OVERLAY_PLANE_WIDTH,
        height = BITKEY_DEVICE_SCREEN_OVERLAY_PLANE_HEIGHT
      ).apply {
        materials = listOf(overlayMaterial)
      }

    screenBatteryOverlayNode =
      SCNNode().apply {
        geometry = overlayPlane
        position = SCNVector3Make(0.0f, 0.0f, BITKEY_DEVICE_SCREEN_OVERLAY_Z_OFFSET.toFloat())
      }.also { overlayNode ->
        activeScreenNode.addChildNode(overlayNode)
      }
  }

  private fun captureDefaultScreenMaterialContentsIfNeeded(material: SCNMaterial) {
    if (hasCapturedDefaultScreenMaterialContents) return

    defaultScreenDiffuseContents = material.diffuse.contents
    defaultScreenEmissionContents = material.emission.contents
    hasCapturedDefaultScreenMaterialContents = true
  }

  private fun restoreDefaultScreenMaterialContents() {
    val material = screenMaterial ?: return
    if (!hasCapturedDefaultScreenMaterialContents) return

    material.diffuse.contents = defaultScreenDiffuseContents
    material.emission.contents = defaultScreenEmissionContents
    material.diffuse.contentsTransform = scnMatrix4IdentityValue()
    material.emission.contentsTransform = scnMatrix4IdentityValue()
  }

  private fun captureDefaultInlayMaterialContentsIfNeeded(material: SCNMaterial) {
    if (hasCapturedDefaultInlayMaterialContents) return

    defaultInlayDiffuseContents = material.diffuse.contents
    hasCapturedDefaultInlayMaterialContents = true
  }

  private fun restoreDefaultInlayMaterialContents() {
    val material = inlayMaterial ?: return
    if (!hasCapturedDefaultInlayMaterialContents) return

    material.diffuse.contents = defaultInlayDiffuseContents
  }

  private fun removeScreenBatteryOverlayNode() {
    screenBatteryOverlayNode?.removeFromParentNode()
    screenBatteryOverlayNode = null
  }

  private fun releaseScreenVideoPlayer() {
    screenVideoPlaybackObserver?.let { observer ->
      NSNotificationCenter.defaultCenter.removeObserver(
        observer = observer,
        name = AVPlayerItemDidPlayToEndTimeNotification,
        `object` = screenVideoObservedItem
      )
    }
    screenVideoPlayer?.pause()
    screenVideoPlayer?.replaceCurrentItemWithPlayerItem(null)
    screenVideoObservedItem = null
    screenVideoPlaybackObserver = null
    screenVideoPlayer = null
    shouldResumeScreenVideoPlayback = false
    syncSceneViewRenderingState()
  }

  private fun restartScreenVideoPlayback() {
    val player = screenVideoPlayer ?: return

    player.seekToTime(CMTimeMakeWithSeconds(0.0, 1))
    if (isApplicationActive) {
      player.play()
    } else {
      shouldResumeScreenVideoPlayback = true
    }
  }

  private fun createBitkeyDeviceSerialInlayImage(
    serialNumber: String,
  ): platform.UIKit.UIImage? {
    val inlayTexturePath = loadBitkeyDeviceInlayTexturePath() ?: return null
    val baseImage = platform.UIKit.UIImage.imageWithContentsOfFile(inlayTexturePath) ?: return null
    val imageSize = baseImage.size
    val imageHeight = imageSize.useContents { this.height }
    val imageWidth = imageSize.useContents { this.width }
    val scale = imageHeight / BITKEY_DEVICE_INLAY_TEXTURE_BASE_HEIGHT
    val fullImageRect = CGRectMake(0.0, 0.0, imageWidth, imageHeight)
    val overlayRect =
      CGRectMake(
        BITKEY_DEVICE_SERIAL_OVERLAY_RECT_LEFT * scale,
        BITKEY_DEVICE_SERIAL_OVERLAY_RECT_TOP * scale,
        (BITKEY_DEVICE_SERIAL_OVERLAY_RECT_RIGHT - BITKEY_DEVICE_SERIAL_OVERLAY_RECT_LEFT) * scale,
        (BITKEY_DEVICE_SERIAL_OVERLAY_RECT_BOTTOM - BITKEY_DEVICE_SERIAL_OVERLAY_RECT_TOP) * scale
      )
    val displaySerial = "SN:${serialNumber.trim()}"
    val maxTextWidth = overlayRect.useContents { size.width } - (BITKEY_DEVICE_SERIAL_TEXT_PADDING_RIGHT * scale)
    val textX = BITKEY_DEVICE_SERIAL_TEXT_LEFT * scale

    platform.UIKit.UIGraphicsBeginImageContextWithOptions(
      size = imageSize,
      opaque = false,
      scale = baseImage.scale
    )

    try {
      baseImage.drawInRect(fullImageRect)

      bitkeyDeviceSerialBackgroundColor().setFill()
      platform.UIKit.UIRectFill(overlayRect)

      // Rotate the serial text 180 degrees so it faces the correct direction on the device.
      val context = platform.UIKit.UIGraphicsGetCurrentContext()
      val overlayCenterX = overlayRect.useContents { origin.x + size.width / 2.0 }
      val overlayCenterY = overlayRect.useContents { origin.y + size.height / 2.0 }
      context?.let { ctx ->
        platform.CoreGraphics.CGContextSaveGState(ctx)
        platform.CoreGraphics.CGContextTranslateCTM(ctx, overlayCenterX, overlayCenterY)
        platform.CoreGraphics.CGContextRotateCTM(ctx, PI)
        platform.CoreGraphics.CGContextTranslateCTM(ctx, -overlayCenterX, -overlayCenterY)
      }

      val displaySerialNSString: NSString = displaySerial as NSString
      var textFontSize = BITKEY_DEVICE_SERIAL_TEXT_SIZE * scale
      var textAttributes = bitkeyDeviceSerialTextAttributes(textFontSize)
      var textSize = displaySerialNSString.sizeWithAttributes(textAttributes)

      while (
        textSize.useContents { width } > maxTextWidth &&
        textFontSize > BITKEY_DEVICE_SERIAL_TEXT_MIN_SIZE * scale
      ) {
        textFontSize = textFontSize * BITKEY_DEVICE_SERIAL_TEXT_SCALE_REDUCTION
        textAttributes = bitkeyDeviceSerialTextAttributes(textFontSize)
        textSize = displaySerialNSString.sizeWithAttributes(textAttributes)
      }

      val textRect =
        CGRectMake(
          textX,
          overlayRect.useContents { origin.y } +
            ((overlayRect.useContents { size.height } - textSize.useContents { height }) / 2.0),
          maxTextWidth,
          overlayRect.useContents { size.height }
        )
      displaySerialNSString.drawInRect(rect = textRect, withAttributes = textAttributes)

      context?.let { ctx ->
        platform.CoreGraphics.CGContextRestoreGState(ctx)
      }

      return platform.UIKit.UIGraphicsGetImageFromCurrentImageContext()
    } finally {
      platform.UIKit.UIGraphicsEndImageContext()
    }
  }

  private fun createBitkeyDeviceScreenBatteryOverlayImage(
    batteryPercentage: Int,
  ): platform.UIKit.UIImage? {
    val batteryDisplayStyle = bitkeyDeviceScreenBatteryDisplayStyle(batteryPercentage)
    val imageSize =
      CGSizeMake(
        BITKEY_DEVICE_SCREEN_OVERLAY_TEXTURE_SIZE,
        BITKEY_DEVICE_SCREEN_OVERLAY_TEXTURE_SIZE
      )
    val displayPercentage = "${batteryPercentage.coerceIn(0, 100)}%"
    val displayPercentageNSString: NSString = displayPercentage as NSString
    val textAttributes =
      bitkeyDeviceScreenBatteryTextAttributes(
        textFontSize = BITKEY_DEVICE_SCREEN_OVERLAY_TEXT_SIZE,
        textColor = batteryDisplayStyle.textColor
      )
    val textSize = displayPercentageNSString.sizeWithAttributes(textAttributes)
    val rowWidth =
      BITKEY_DEVICE_SCREEN_OVERLAY_ICON_SIZE +
        BITKEY_DEVICE_SCREEN_OVERLAY_ICON_TEXT_SPACING +
        textSize.useContents { width }
    val rowLeft = (BITKEY_DEVICE_SCREEN_OVERLAY_TEXTURE_SIZE - rowWidth) / 2.0
    val iconRect =
      CGRectMake(
        rowLeft,
        BITKEY_DEVICE_SCREEN_OVERLAY_TEXTURE_SIZE -
          BITKEY_DEVICE_SCREEN_OVERLAY_BOTTOM_PADDING -
          BITKEY_DEVICE_SCREEN_OVERLAY_ICON_SIZE,
        BITKEY_DEVICE_SCREEN_OVERLAY_ICON_SIZE,
        BITKEY_DEVICE_SCREEN_OVERLAY_ICON_SIZE
      )

    platform.UIKit.UIGraphicsBeginImageContextWithOptions(
      size = imageSize,
      opaque = false,
      scale = 1.0
    )

    try {
      drawBitkeyDeviceScreenBatteryIcon(
        iconRect = iconRect,
        fillFraction = batteryDisplayStyle.fillFraction,
        tintColor = batteryDisplayStyle.iconColor
      )

      val textRect =
        CGRectMake(
          iconRect.useContents { origin.x + size.width + BITKEY_DEVICE_SCREEN_OVERLAY_ICON_TEXT_SPACING },
          iconRect.useContents { origin.y + ((size.height - textSize.useContents { height }) / 2.0) },
          textSize.useContents { width },
          textSize.useContents { height }
        )
      displayPercentageNSString.drawInRect(rect = textRect, withAttributes = textAttributes)

      return platform.UIKit.UIGraphicsGetImageFromCurrentImageContext()
    } finally {
      platform.UIKit.UIGraphicsEndImageContext()
    }
  }

  private fun bitkeyDeviceSerialTextAttributes(
    textFontSize: Double,
  ): Map<Any?, Any> {
    val font =
      platform.UIKit.UIFont.fontWithName(
        fontName = BITKEY_DEVICE_SERIAL_TEXT_FONT_POSTSCRIPT_NAME,
        size = textFontSize
      ) ?: platform.UIKit.UIFont.systemFontOfSize(textFontSize)

    return mapOf(
      platform.UIKit.NSFontAttributeName to font,
      platform.UIKit.NSForegroundColorAttributeName to bitkeyDeviceSerialTextColor()
    )
  }

  private fun bitkeyDeviceSerialTextColor(): UIColor =
    UIColor.colorWithWhite(
      white = BITKEY_DEVICE_SERIAL_TEXT_COLOR_WHITE,
      alpha = 1.0
    )

  private fun bitkeyDeviceSerialBackgroundColor(): UIColor =
    UIColor.colorWithWhite(
      white = BITKEY_DEVICE_SERIAL_BACKGROUND_WHITE,
      alpha = 1.0
    )

  private fun bitkeyDeviceScreenBatteryTextAttributes(
    textFontSize: Double,
    textColor: UIColor,
  ): Map<Any?, Any> {
    val font = platform.UIKit.UIFont.systemFontOfSize(textFontSize)

    return mapOf(
      platform.UIKit.NSFontAttributeName to font,
      platform.UIKit.NSForegroundColorAttributeName to textColor
    )
  }

  private fun bitkeyDeviceScreenBatteryTextColor(): UIColor =
    UIColor.colorWithWhite(
      white = BITKEY_DEVICE_SCREEN_OVERLAY_TEXT_WHITE,
      alpha = 1.0
    )

  private fun bitkeyDeviceScreenBatteryIconColor(): UIColor =
    UIColor.colorWithWhite(
      white = BITKEY_DEVICE_SCREEN_OVERLAY_ICON_WHITE,
      alpha = 1.0
    )

  private fun bitkeyDeviceScreenBatteryLowColor(): UIColor =
    UIColor.colorWithRed(
      red = BITKEY_DEVICE_SCREEN_OVERLAY_LOW_RED,
      green = BITKEY_DEVICE_SCREEN_OVERLAY_LOW_GREEN,
      blue = BITKEY_DEVICE_SCREEN_OVERLAY_LOW_BLUE,
      alpha = 1.0
    )

  private fun bitkeyDeviceScreenBatteryDisplayStyle(
    batteryPercentage: Int,
  ): BitkeyDeviceScreenBatteryDisplayStyle {
    val normalizedBatteryPercentage = batteryPercentage.coerceIn(0, 100)
    val fillFraction =
      when {
        normalizedBatteryPercentage <= 10 -> 2.0 / 12.0
        normalizedBatteryPercentage <= 25 -> 3.0 / 12.0
        normalizedBatteryPercentage <= 50 -> 6.5 / 12.0
        normalizedBatteryPercentage <= 75 -> 9.5 / 12.0
        else -> 1.0
      }
    val iconColor =
      when {
        normalizedBatteryPercentage <= 10 -> bitkeyDeviceScreenBatteryLowColor()
        else -> bitkeyDeviceScreenBatteryIconColor()
      }
    val textColor =
      if (normalizedBatteryPercentage <= 10) {
        bitkeyDeviceScreenBatteryLowColor()
      } else {
        bitkeyDeviceScreenBatteryTextColor()
      }

    return BitkeyDeviceScreenBatteryDisplayStyle(
      fillFraction = fillFraction,
      iconColor = iconColor,
      textColor = textColor
    )
  }

  private fun drawBitkeyDeviceScreenBatteryIcon(
    iconRect: kotlinx.cinterop.CValue<platform.CoreGraphics.CGRect>,
    fillFraction: Double,
    tintColor: UIColor,
  ) {
    val rect =
      iconRect.useContents {
        CGRectMake(origin.x, origin.y, size.width, size.height)
      }
    val width = rect.useContents { size.width }
    val height = rect.useContents { size.height }
    val strokeWidth = width * (2.0 / 24.0)
    val cornerRadius = width * (2.0 / 24.0)
    val bodyRect =
      CGRectMake(
        rect.useContents { origin.x + (1.0 / 24.0 * size.width) },
        rect.useContents { origin.y + (5.0 / 24.0 * size.height) },
        width * (18.0 / 24.0),
        height * (14.0 / 24.0)
      )
    val capRect =
      CGRectMake(
        rect.useContents { origin.x + (21.5 / 24.0 * size.width) },
        rect.useContents { origin.y + (9.5 / 24.0 * size.height) },
        width * (2.0 / 24.0),
        height * (5.0 / 24.0)
      )
    val fillLeft = rect.useContents { origin.x + (4.5 / 24.0 * size.width) }
    val fillWidth = width * (12.0 / 24.0) * fillFraction.coerceIn(0.0, 1.0)
    val fillRect =
      CGRectMake(
        fillLeft,
        rect.useContents { origin.y + (8.5 / 24.0 * size.height) },
        fillWidth,
        height * (7.0 / 24.0)
      )

    tintColor.setFill()
    tintColor.setStroke()
    platform.UIKit.UIBezierPath.bezierPathWithRoundedRect(
      rect = fillRect,
      cornerRadius = cornerRadius * 0.6
    ).fill()
    platform.UIKit.UIBezierPath.bezierPathWithRoundedRect(
      rect = bodyRect,
      cornerRadius = cornerRadius
    ).apply {
      lineWidth = strokeWidth
      stroke()
    }
    platform.UIKit.UIBezierPath.bezierPathWithRoundedRect(
      rect = capRect,
      cornerRadius = cornerRadius * 0.4
    ).fill()
  }

  private fun applyModelTransform() {
    val bobOffset =
      if (
        content == DeviceStatusCard.VideoContent.BITKEY_WAITING_3D &&
        rotationMotionMode == RotationMotionMode.IDLE
      ) {
        waitingBobTranslationOffsetY(bobElapsedSeconds)
      } else {
        0.0f
      }
    modelRootNode.eulerAngles = SCNVector3Make(0.0f, currentModelRotationY, 0.0f)
    modelRootNode.position = SCNVector3Make(
      0.0f,
      currentModelTranslationY + bobOffset,
      0.0f
    )
    val modelScale = if (content == DeviceStatusCard.VideoContent.BITKEY_WAITING_3D) {
      debugSettings.waitingModelScale
    } else {
      debugSettings.modelScale
    }
    modelRootNode.scale = SCNVector3Make(
      modelScale,
      modelScale,
      modelScale
    )
  }

  private fun applyLightingEnvironmentTransform() {
    val scene = sceneView?.scene ?: return
    val longitudeOffset = -(debugSettings.hdriRotationDegrees.normalizeDegrees() / 360.0f)
    val latitudeOffset = (-debugSettings.hdriElevationDegrees / 180.0f).coerceIn(-0.5f, 0.5f)
    scene.lightingEnvironment.contentsTransform =
      SCNMatrix4Translate(
        scnMatrix4IdentityValue(),
        longitudeOffset,
        latitudeOffset,
        0.0f
      )
  }

  private fun startMomentumMotion() {
    rotationMotionMode = RotationMotionMode.MOMENTUM
    ensureDisplayLink()
    syncSceneViewRenderingState()
  }

  private fun startIdleMotion() {
    if (content != DeviceStatusCard.VideoContent.BITKEY_WAITING_3D) {
      stopAllAnimation()
      return
    }
    angularVelocityY = 0.0f
    rotationMotionMode = RotationMotionMode.IDLE
    ensureDisplayLink()
    syncSceneViewRenderingState()
  }

  private fun startWaitingIdleMotionIfNeeded() {
    if (content != DeviceStatusCard.VideoContent.BITKEY_WAITING_3D || sceneView == null) return
    if (hasPlayedWaitingIntro || shouldPlayWaitingIntro) return

    angularVelocityY = 0.0f
    currentModelRotationY = 0.0f
    currentModelTranslationY = defaultModelTranslationY()
    rotationMotionMode = RotationMotionMode.IDLE
    applyModelTransform()
    ensureDisplayLink()
    syncSceneViewRenderingState()
  }

  private fun startWaitingIntroIfNeeded() {
    if (!canStartWaitingIntro()) {
      return
    }

    hasPlayedWaitingIntro = true
    shouldPlayWaitingIntro = true
    angularVelocityY = 0.0f
    introElapsedSeconds = 0.0f
    introStartRotationY = currentModelRotationY
    introStartTranslationY =
      currentModelTranslationY + waitingBobTranslationOffsetY(bobElapsedSeconds)
    currentModelTranslationY = introStartTranslationY
    rotationMotionMode = RotationMotionMode.INTRO
    applyModelTransform()
    ensureDisplayLink()
    syncSceneViewRenderingState()
  }

  private fun canStartWaitingIntro(): Boolean =
    content == DeviceStatusCard.VideoContent.BITKEY_WAITING_3D &&
      !hasPlayedWaitingIntro &&
      sceneView != null &&
      shouldPlayWaitingIntro

  private fun defaultModelTranslationY(): Float =
    when (content) {
      DeviceStatusCard.VideoContent.BITKEY_WAITING_3D -> debugSettings.waitingRestTranslationY
      DeviceStatusCard.VideoContent.BITKEY_ROTATE -> debugSettings.rotateTranslationY
    }

  private fun defaultModelRotationY(): Float =
    when (content) {
      DeviceStatusCard.VideoContent.BITKEY_WAITING_3D -> 0.0f
      DeviceStatusCard.VideoContent.BITKEY_ROTATE -> PI.toFloat()
    }

  private fun normalizedModelEulerAngles() = SCNVector3Make(0.0f, 0.0f, 0.0f)

  private fun normalizedModelScale(): Float = BITKEY_DEVICE_MODEL_SCALE

  private fun startSnapToNearestAnchor() {
    angularVelocityY = 0.0f
    snapTargetRotationY = nearestAnchorRotation(currentModelRotationY)
    rotationMotionMode = RotationMotionMode.SNAP

    if (abs(snapTargetRotationY - currentModelRotationY) < BITKEY_DEVICE_ROTATION_SNAP_THRESHOLD) {
      currentModelRotationY = snapTargetRotationY
      applyModelTransform()
      startIdleMotion()
      return
    }

    ensureDisplayLink()
  }

  private fun ensureDisplayLink() {
    if (momentumDisplayLink == null) {
      momentumDisplayLink =
        CADisplayLink.displayLinkWithTarget(
          target = momentumHandler,
          selector = sel_registerName("handleMomentumFrame:")
        ).apply {
          preferredFramesPerSecond =
            if (content == DeviceStatusCard.VideoContent.BITKEY_WAITING_3D) {
              BITKEY_DEVICE_WAITING_PREFERRED_FRAMES_PER_SECOND
            } else {
              BITKEY_DEVICE_INTERACTION_PREFERRED_FRAMES_PER_SECOND
            }
          addToRunLoop(
            runloop = NSRunLoop.mainRunLoop,
            forMode = NSRunLoopCommonModes
          )
        }
    }
    momentumDisplayLink?.paused = !isApplicationActive
  }

  private fun ensureAppLifecycleObserver() {
    if (appLifecycleObserver != null) return

    val observer =
      BitkeyDeviceAppLifecycleObserver(
        onWillResignActive = {
          isApplicationActive = false
          shouldResumeScreenVideoPlayback = screenVideoPlayer != null
          screenVideoPlayer?.pause()
          syncSceneViewRenderingState()
        },
        onDidBecomeActive = {
          isApplicationActive = true
          if (shouldResumeScreenVideoPlayback) {
            screenVideoPlayer?.play()
          }
          shouldResumeScreenVideoPlayback = false
          syncSceneViewRenderingState()
        }
      )
    NSNotificationCenter.defaultCenter.addObserver(
      observer = observer,
      selector = sel_registerName("handleWillResignActive:"),
      name = UIApplicationWillResignActiveNotification,
      `object` = null
    )
    NSNotificationCenter.defaultCenter.addObserver(
      observer = observer,
      selector = sel_registerName("handleDidBecomeActive:"),
      name = UIApplicationDidBecomeActiveNotification,
      `object` = null
    )
    appLifecycleObserver = observer
  }

  private fun releaseAppLifecycleObserver() {
    appLifecycleObserver?.let { observer ->
      NSNotificationCenter.defaultCenter.removeObserver(
        observer = observer,
        name = UIApplicationWillResignActiveNotification,
        `object` = null
      )
      NSNotificationCenter.defaultCenter.removeObserver(
        observer = observer,
        name = UIApplicationDidBecomeActiveNotification,
        `object` = null
      )
    }
    appLifecycleObserver = null
    isApplicationActive = true
    shouldResumeScreenVideoPlayback = false
  }

  private fun syncSceneViewRenderingState() {
    val sceneView = sceneView ?: return
    val shouldContinuouslyRender =
      isApplicationActive &&
        (
          isUserInteracting ||
            rotationMotionMode != null ||
            screenVideoPlayer != null
          )

    momentumDisplayLink?.paused = !isApplicationActive
    sceneView.playing = shouldContinuouslyRender
    sceneView.rendersContinuously = shouldContinuouslyRender
    sceneView.preferredFramesPerSecond =
      if (content == DeviceStatusCard.VideoContent.BITKEY_WAITING_3D) {
        BITKEY_DEVICE_WAITING_PREFERRED_FRAMES_PER_SECOND
      } else {
        BITKEY_DEVICE_INTERACTION_PREFERRED_FRAMES_PER_SECOND
      }
  }
}

@OptIn(ExperimentalForeignApi::class)
private class BitkeyDeviceSceneMomentumHandler(
  private val onFrame: (CADisplayLink) -> Unit,
) : NSObject() {
  @ObjCAction
  fun handleMomentumFrame(displayLink: CADisplayLink) {
    onFrame(displayLink)
  }
}

private fun Float.normalizeDegrees(): Float {
  var normalizedValue = this % 360.0f
  if (normalizedValue < 0.0f) {
    normalizedValue += 360.0f
  }
  return normalizedValue
}

private fun Float.toRadians(): Float = (this / 180.0f * PI).toFloat()

private fun waitingRestTranslationY(): Float = BITKEY_DEVICE_WAITING_REST_TRANSLATION_Y

private fun waitingBobTranslationOffsetY(bobElapsedSeconds: Float): Float {
  val phase = (bobElapsedSeconds / BITKEY_DEVICE_WAITING_BOB_PERIOD_SECONDS) * (2.0f * PI.toFloat())
  return sin(phase) * BITKEY_DEVICE_WAITING_BOB_AMPLITUDE_Y
}

private fun lerp(
  startValue: Float,
  endValue: Float,
  progress: Float,
): Float = startValue + (endValue - startValue) * progress

private fun sceneSecondaryBackgroundColorForTraitCollection(
  traitCollection: UITraitCollection?,
): UIColor =
  if (traitCollection?.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark) {
    UIColor(
      red = 0.14901960784313725,
      green = 0.14901960784313725,
      blue = 0.14901960784313725,
      alpha = 1.0
    )
  } else {
    UIColor.whiteColor
  }

private fun Float.toGrayscaleUIColor(): UIColor {
  val clampedValue = coerceIn(0.0f, 1.0f).toDouble()
  return UIColor(
    red = clampedValue,
    green = clampedValue,
    blue = clampedValue,
    alpha = 1.0
  )
}

private fun Color.toUIColor(): UIColor =
  UIColor(
    red = red.toDouble(),
    green = green.toDouble(),
    blue = blue.toDouble(),
    alpha = alpha.toDouble()
  )

private fun nearestAnchorRotation(rotationRadians: Float): Float {
  val halfTurn = PI.toFloat()
  return (rotationRadians / halfTurn).roundToInt() * halfTurn
}

private fun scnMatrix4IdentityValue() =
  cValue<SCNMatrix4> {
    m11 = 1.0f
    m12 = 0.0f
    m13 = 0.0f
    m14 = 0.0f
    m21 = 0.0f
    m22 = 1.0f
    m23 = 0.0f
    m24 = 0.0f
    m31 = 0.0f
    m32 = 0.0f
    m33 = 1.0f
    m34 = 0.0f
    m41 = 0.0f
    m42 = 0.0f
    m43 = 0.0f
    m44 = 1.0f
  }

private fun SCNNode.findDescendantNamed(name: String): SCNNode? {
  if (this.name == name) {
    return this
  }

  childNodes
    .mapNotNull { it as? SCNNode }
    .forEach { childNode ->
      childNode.findDescendantNamed(name)?.let { return it }
    }

  return null
}

@OptIn(ExperimentalForeignApi::class)
private class BitkeyDeviceScreenVideoPlaybackObserver(
  private val onPlaybackEnded: () -> Unit,
) : NSObject() {
  @Suppress("UnusedParameter")
  @ObjCAction
  fun handlePlaybackEnded(notification: NSNotification) {
    onPlaybackEnded()
  }
}

@OptIn(ExperimentalForeignApi::class)
private class BitkeyDeviceAppLifecycleObserver(
  private val onWillResignActive: () -> Unit,
  private val onDidBecomeActive: () -> Unit,
) : NSObject() {
  @Suppress("UnusedParameter")
  @ObjCAction
  fun handleWillResignActive(notification: NSNotification) {
    onWillResignActive()
  }

  @Suppress("UnusedParameter")
  @ObjCAction
  fun handleDidBecomeActive(notification: NSNotification) {
    onDidBecomeActive()
  }
}
