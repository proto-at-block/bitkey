package build.wallet.ui.components.card

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaPlayer
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.RequestDisallowInterceptTouchEvent
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import bitkey.account.HardwareType
import build.wallet.statemachine.core.form.FORM_DS_V2_WAITING_REVEAL_DELAY_MILLIS
import build.wallet.statemachine.core.form.FORM_DS_V2_WAITING_REVEAL_DURATION_MILLIS
import build.wallet.statemachine.core.form.FormDsV2WaitingRevealEasing
import build.wallet.statemachine.core.form.FormMainContentModel.DeviceStatusCard
import build.wallet.ui.components.video.VideoPlayer
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import io.github.sceneview.Scene
import io.github.sceneview.SceneView
import io.github.sceneview.environment.Environment
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.material.VideoMaterial
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.node.ImageNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.getOrNull
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberScene
import io.github.sceneview.rememberView
import io.github.sceneview.safeDestroyMaterialInstance
import io.github.sceneview.texture.ImageTexture
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

private const val BITKEY_DEVICE_MODEL_ASSET_LOCATION = "bitkey-device/Bitkey_W3_bitkeylogo.glb"
private const val BITKEY_DEVICE_HDRI_ASSET_LOCATION = "bitkey-device/W3_HDRI.hdr"
private const val BITKEY_DEVICE_SCREEN_NODE_NAME = "Screen"
private const val BITKEY_DEVICE_INLAY_NODE_NAME = "Inlay"
private const val BITKEY_DEVICE_INLAY_TEXTURE_ASSET_LOCATION = "bitkey-device/Bitkey_W3_bitkeylogo_inlay.png"
private const val BITKEY_DEVICE_SERIAL_TEXT_FONT_ASSET_LOCATION =
  "composeResources/bitkey.ui.framework_public.generated.resources/font/cash_sans_mono_regular.otf"
private const val BITKEY_DEVICE_SCREEN_VIDEO_RESOURCE_NAME = "bitkey_taptophoneinsert"
private const val BITKEY_DEVICE_ROTATION_DEGREES_PER_POINT = 0.441f
private const val BITKEY_DEVICE_INERTIA_VELOCITY_SCALE = 0.4125f
private const val BITKEY_DEVICE_INERTIA_DAMPING = 5.0f
private const val BITKEY_DEVICE_INERTIA_STOP_THRESHOLD = 1.15f
private const val BITKEY_DEVICE_ROTATION_SNAP_RATE = 9.0f
private const val BITKEY_DEVICE_ROTATION_SNAP_THRESHOLD = 0.12f
private const val BITKEY_DEVICE_DIRECTIONAL_SNAP_THRESHOLD_DEGREES = 24.0f
private const val BITKEY_DEVICE_DIRECTION_EPSILON = 0.001f
private const val BITKEY_DEVICE_MODEL_SCALE_TO_UNITS = 1.0f
private const val BITKEY_DEVICE_CAMERA_OFFSET_Y = 0.0f
private const val BITKEY_DEVICE_CAMERA_DISTANCE = 1.4f
private const val BITKEY_DEVICE_CAMERA_TARGET_OFFSET_Y = 0.0f
private const val BITKEY_DEVICE_MODEL_SCALE = 1.0f
private const val BITKEY_DEVICE_WAITING_MODEL_SCALE = 0.85f
private const val BITKEY_DEVICE_WAITING_REST_TRANSLATION_Y = -0.0535f
private const val BITKEY_DEVICE_WAITING_INTRO_TARGET_TRANSLATION_Y = -0.0165f
private const val BITKEY_DEVICE_WAITING_INTRO_DURATION_MILLIS = 800
private const val BITKEY_DEVICE_WAITING_BUTTON_REVEAL_GAP_MILLIS = 120
private const val BITKEY_DEVICE_WAITING_BOB_AMPLITUDE_Y = 0.0091f
private const val BITKEY_DEVICE_WAITING_BOB_PERIOD_SECONDS = 2.8f
private const val BITKEY_DEVICE_HDRI_INTENSITY_MULTIPLIER = 3.0f
private const val BITKEY_DEVICE_HDRI_ROTATION_DEGREES = -94.0f
private const val BITKEY_DEVICE_HDRI_ELEVATION_DEGREES = 23.0f
private const val BITKEY_DEVICE_FILL_LIGHT_INTENSITY = 65_000.0f
private const val BITKEY_DEVICE_FILL_LIGHT_AZIMUTH_DEGREES = 0.0f
private const val BITKEY_DEVICE_FILL_LIGHT_ELEVATION_DEGREES = 35.0f
private const val BITKEY_DEVICE_FILL_LIGHT_ENABLED = true
private const val BITKEY_DEVICE_INLAY_TEXTURE_BASE_WIDTH = 1500.0f
private const val BITKEY_DEVICE_INLAY_TEXTURE_BASE_HEIGHT = 407.0f
private const val BITKEY_DEVICE_SERIAL_OVERLAY_RECT_LEFT = 264.0f
private const val BITKEY_DEVICE_SERIAL_OVERLAY_RECT_TOP = 15.0f
private const val BITKEY_DEVICE_SERIAL_OVERLAY_RECT_RIGHT = 1472.0f
private const val BITKEY_DEVICE_SERIAL_OVERLAY_RECT_BOTTOM = 143.0f
private const val BITKEY_DEVICE_SERIAL_BACKGROUND_SAMPLE_LEFT = 1470.0f
private const val BITKEY_DEVICE_SERIAL_BACKGROUND_SAMPLE_TOP = 57.0f
private const val BITKEY_DEVICE_SERIAL_BACKGROUND_SAMPLE_RIGHT = 1494.0f
private const val BITKEY_DEVICE_SERIAL_BACKGROUND_SAMPLE_BOTTOM = 121.0f
private const val BITKEY_DEVICE_SERIAL_TEXT_LEFT = 276.0f
private const val BITKEY_DEVICE_SERIAL_TEXT_COLOR = 0xFF686868.toInt()
private const val BITKEY_DEVICE_SERIAL_TEXT_SIZE = 92.0f
private const val BITKEY_DEVICE_SERIAL_TEXT_MIN_SIZE = 60.0f
private const val BITKEY_DEVICE_SERIAL_TEXT_PADDING_RIGHT = 40.0f
private const val BITKEY_DEVICE_SERIAL_TEXT_CORNER_RADIUS = 10.0f
private const val BITKEY_DEVICE_SCREEN_OVERLAY_TEXTURE_SIZE = 466
private const val BITKEY_DEVICE_SCREEN_OVERLAY_ICON_SIZE = 28.0f
private const val BITKEY_DEVICE_SCREEN_OVERLAY_TEXT_SIZE = 27.0f
private const val BITKEY_DEVICE_SCREEN_OVERLAY_BOTTOM_PADDING = 38.0f
private const val BITKEY_DEVICE_SCREEN_OVERLAY_ICON_TEXT_SPACING = 8.0f
private const val BITKEY_DEVICE_SCREEN_OVERLAY_DEPTH_OFFSET = 0.0002f
private const val BITKEY_DEVICE_SCREEN_OVERLAY_SIZE_SCALE = 0.99f
private const val BITKEY_DEVICE_SCREEN_OVERLAY_COLOR = 0xFFADADAD.toInt()
private const val BITKEY_DEVICE_SCREEN_OVERLAY_LOW_COLOR = 0xFFF84752.toInt()
private const val DEFAULT_ROTATION_DEGREES = 0.0f

private data class BitkeyDeviceCustomInlayResources(
  val materialInstance: MaterialInstance,
  val texture: Texture,
)

private data class BitkeyDeviceEnvironmentResources(
  val environment: Environment,
  val baseHdriIntensity: Float,
)

private object BitkeyDeviceSharedFilamentResources {
  private val eglContext by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SceneView.createEglContext()
  }

  val engine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SceneView.createEngine(eglContext)
  }

  private var environmentResources: BitkeyDeviceEnvironmentResources? = null

  @Synchronized
  fun environment(context: Context): Environment {
    return environmentResources(context).environment
  }

  @Synchronized
  fun baseHdriIntensity(context: Context): Float {
    return environmentResources(context).baseHdriIntensity
  }

  @Synchronized
  private fun environmentResources(context: Context): BitkeyDeviceEnvironmentResources {
    return environmentResources ?: run {
      val loader = SceneView.createEnvironmentLoader(engine, context.applicationContext)
      val environment = requireNotNull(
        loader.createHDREnvironment(
          assetFileLocation = BITKEY_DEVICE_HDRI_ASSET_LOCATION,
          createSkybox = false
        )
      ) {
        "Failed to load Bitkey device HDR environment from assets."
      }
      val indirectLight = requireNotNull(environment.indirectLight) {
        "Bitkey device HDR environment did not produce indirect light."
      }
      BitkeyDeviceEnvironmentResources(
        environment = environment,
        baseHdriIntensity = indirectLight.intensity.coerceAtLeast(1.0f)
      ).also { environmentResources = it }
    }
  }
}

private enum class RotationMotionMode {
  INTRO,
  IDLE,
  MOMENTUM,
  SNAP,
}

private enum class RotationGestureIntent {
  HORIZONTAL,
  VERTICAL,
}

@Suppress("CyclomaticComplexMethod", "ModifierReused")
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
  val isWaitingContent = content == DeviceStatusCard.VideoContent.BITKEY_WAITING_3D
  val isInteractiveScene = content == DeviceStatusCard.VideoContent.BITKEY_ROTATE ||
    content == DeviceStatusCard.VideoContent.BITKEY_WAITING_3D
  val shouldRenderRealtimeScene = LocalDesignSystemUpdatesEnabled.current &&
    supportsBitkeyDevice3DMedia(hardwareType) &&
    !LocalInspectionMode.current

  if (!shouldRenderRealtimeScene) {
    when (content) {
      DeviceStatusCard.VideoContent.BITKEY_ROTATE -> {
        Box(
          modifier = modifier,
          contentAlignment = Alignment.Center
        ) {
          VideoPlayer(
            modifier = Modifier.size(BitkeyDeviceFallbackMediaSize),
            resourcePath = bitkeyDeviceVideoResource(content),
            backgroundColor = legacyBitkeyDeviceCardBackgroundColor(),
            isLooping = true,
            autoStart = true
          )
        }
      }
      DeviceStatusCard.VideoContent.BITKEY_WAITING_3D -> {
        VideoPlayer(
          modifier = modifier,
          resourcePath = bitkeyDeviceVideoResource(content),
          isLooping = false,
          autoStart = true
        )
      }
    }
    return
  }

  val androidContext = LocalContext.current
  val serialNumberTypeface = remember(androidContext) {
    loadBitkeyDeviceSerialTypeface(androidContext)
  }
  val composeView = LocalView.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val requestDisallowInterceptTouchEvent = remember { RequestDisallowInterceptTouchEvent() }
  val velocityTracker = remember { VelocityTracker.obtain() }
  val minimumFlingVelocity = remember(composeView) {
    ViewConfiguration.get(composeView.context).scaledMinimumFlingVelocity.toFloat()
  }
  val touchSlop = remember(composeView) {
    ViewConfiguration.get(composeView.context).scaledTouchSlop.toFloat()
  }
  val waitingEntranceInterpolator = remember {
    PathInterpolator(0.0f, 0.0f, 0.2f, 1.0f)
  }
  val engine = remember { BitkeyDeviceSharedFilamentResources.engine }
  val view = rememberView(engine)
  val renderer = rememberRenderer(engine)
  val scene = rememberScene(engine)
  val collisionSystem = rememberCollisionSystem(view)
  // Use per-composable loaders so all model/material resources are properly destroyed
  // when leaving the screen and freshly created on re-entry. This avoids stale texture
  // handles that cause "Invalid texture still bound to MaterialInstance" crashes.
  val modelLoader = rememberModelLoader(engine)
  val materialLoader = rememberMaterialLoader(engine)
  val environment = BitkeyDeviceSharedFilamentResources.environment(androidContext)
  val cameraNode = rememberCameraNode(engine) {
    position = Position(y = BITKEY_DEVICE_CAMERA_OFFSET_Y, z = BITKEY_DEVICE_CAMERA_DISTANCE)
  }
  val mainLightNode = rememberMainLightNode(engine) {
    lightDirection = fillLightDirection()
    intensity = 0.0f
    isShadowCaster = false
  }
  val modelNode = remember(modelLoader, content) {
    ModelNode(
      modelInstance = modelLoader.createModelInstance(
        assetFileLocation = BITKEY_DEVICE_MODEL_ASSET_LOCATION
      ),
      scaleToUnits = BITKEY_DEVICE_MODEL_SCALE_TO_UNITS
    )
  }
  val customInlayResources = remember(engine, androidContext, modelNode, serialNumber, serialNumberTypeface) {
    serialNumber
      ?.trim()
      ?.takeIf(String::isNotEmpty)
      ?.let {
        createCustomInlayResources(
          engine = engine,
          context = androidContext,
          modelNode = modelNode,
          serialNumber = it,
          serialNumberTypeface = serialNumberTypeface
        )
      }
  }
  val screenVideoResourceId = remember(androidContext, isWaitingContent) {
    if (isWaitingContent) {
      bitkeyDeviceScreenVideoResourceId(androidContext)
    } else {
      0
    }
  }
  val screenVideoMaterial = remember(engine, materialLoader, screenVideoResourceId) {
    screenVideoResourceId.takeIf { it != 0 }?.let {
      VideoMaterial(
        engine = engine,
        materialLoader = materialLoader
      )
    }
  }
  val screenBatteryOverlayNode = remember(materialLoader, androidContext, modelNode, content, batteryPercentage) {
    batteryPercentage
      ?.takeIf { content == DeviceStatusCard.VideoContent.BITKEY_ROTATE }
      ?.let { normalizedBatteryPercentage ->
        modelNode.renderableNodes.getOrNull(BITKEY_DEVICE_SCREEN_NODE_NAME)?.let {
            screenRenderableNode ->
          createScreenBatteryOverlayNode(
            materialLoader = materialLoader,
            screenRenderableNode = screenRenderableNode,
            batteryPercentage = normalizedBatteryPercentage
          )
        }
      }
  }
  val screenVideoPlayer = remember(androidContext, screenVideoResourceId, screenVideoMaterial) {
    if (screenVideoResourceId == 0 || screenVideoMaterial == null) {
      null
    } else {
      MediaPlayer.create(androidContext, screenVideoResourceId)?.apply {
        isLooping = true
        setVolume(0f, 0f)
        setSurface(screenVideoMaterial.surface)
      }
    }
  }
  val childNodes = listOf(modelNode)
  val indirectLight = requireNotNull(environment.indirectLight) {
    "Bitkey device HDR environment did not produce indirect light."
  }
  val baseHdriIntensity = BitkeyDeviceSharedFilamentResources.baseHdriIntensity(androidContext)
  var currentModelRotationY by remember(content) {
    mutableFloatStateOf(DEFAULT_ROTATION_DEGREES)
  }
  var currentModelTranslationY by remember(content) {
    mutableFloatStateOf(defaultTranslationY(content))
  }
  var angularVelocityY by remember { mutableFloatStateOf(0.0f) }
  var snapTargetRotationY by remember { mutableFloatStateOf(0.0f) }
  var rotationMotionMode by remember { mutableStateOf<RotationMotionMode?>(null) }
  var lastFrameNanos by remember { mutableLongStateOf(0L) }
  var initialTouchX by remember { mutableFloatStateOf(0.0f) }
  var initialTouchY by remember { mutableFloatStateOf(0.0f) }
  var lastTouchX by remember { mutableFloatStateOf(0.0f) }
  var totalDragDeltaX by remember { mutableFloatStateOf(0.0f) }
  var gestureIntent by remember { mutableStateOf<RotationGestureIntent?>(null) }
  var introElapsedSeconds by remember { mutableFloatStateOf(0.0f) }
  var introStartRotationY by remember { mutableFloatStateOf(0.0f) }
  var introStartTranslationY by remember { mutableFloatStateOf(0.0f) }
  var bobElapsedSeconds by remember { mutableFloatStateOf(0.0f) }
  var hasPlayedWaitingIntro by remember(content) { mutableStateOf(false) }
  var hasPlayedWaitingSceneEntrance by remember(content) { mutableStateOf(false) }
  var sceneView by remember { mutableStateOf<SceneView?>(null) }
  val latestSceneView by rememberUpdatedState(sceneView)
  val latestScreenVideoPlayer by rememberUpdatedState(screenVideoPlayer)

  fun applyModelTransform() {
    val bobOffset = if (isWaitingContent && rotationMotionMode == RotationMotionMode.IDLE) {
      waitingBobTranslationOffsetY(bobElapsedSeconds)
    } else {
      0.0f
    }

    modelNode.rotation = Rotation(y = currentModelRotationY.normalizeDegrees())
    modelNode.position = Position(y = currentModelTranslationY + bobOffset)
    when (content) {
      DeviceStatusCard.VideoContent.BITKEY_WAITING_3D -> modelNode.setScale(BITKEY_DEVICE_WAITING_MODEL_SCALE)
      DeviceStatusCard.VideoContent.BITKEY_ROTATE -> modelNode.setScale(BITKEY_DEVICE_MODEL_SCALE)
    }
  }

  fun startSnapToRotation(targetRotationY: Float) {
    angularVelocityY = 0.0f
    snapTargetRotationY = targetRotationY
    rotationMotionMode = RotationMotionMode.SNAP
  }

  fun stopAllAnimation() {
    angularVelocityY = 0.0f
    introElapsedSeconds = 0.0f
    bobElapsedSeconds = 0.0f
    rotationMotionMode = null
  }

  fun startSnapToNearestAnchor() {
    startSnapToRotation(nearestAnchorRotation(currentModelRotationY))
  }

  fun startDirectionalSnap(directionX: Float) {
    if (abs(directionX) <= BITKEY_DEVICE_DIRECTION_EPSILON) {
      startSnapToNearestAnchor()
      return
    }

    startSnapToRotation(
      anchorRotationInDirection(
        rotationDegrees = currentModelRotationY,
        directionX = directionX
      )
    )
  }

  fun updateGestureInterception(
    disallowIntercept: Boolean,
    deferViewParentUpdate: Boolean = false,
  ) {
    requestDisallowInterceptTouchEvent(disallowIntercept)
    if (deferViewParentUpdate) {
      composeView.post {
        composeView.parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
      }
    } else {
      composeView.parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
    }
  }

  fun updateSceneVisibility(sceneView: SceneView) {
    sceneView.visibility = if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
      VISIBLE
    } else {
      INVISIBLE
    }
  }

  fun prepareWaitingSceneEntrance(sceneView: SceneView) {
    if (!isWaitingContent) return

    sceneView.animate().cancel()
    if (hasPlayedWaitingSceneEntrance) {
      sceneView.alpha = 1.0f
      sceneView.translationY = 0.0f
    } else {
      sceneView.alpha = 0.0f
      sceneView.translationY = 0.0f
    }
  }

  fun startWaitingSceneEntranceIfNeeded() {
    if (!isWaitingContent) return
    val activeSceneView = sceneView ?: return

    activeSceneView.animate().cancel()
    if (hasPlayedWaitingSceneEntrance) {
      activeSceneView.alpha = 1.0f
      activeSceneView.translationY = 0.0f
      return
    }

    activeSceneView.alpha = 0.0f
    activeSceneView.translationY = 0.0f
    activeSceneView.animate()
      .alpha(1.0f)
      .setDuration(FORM_DS_V2_WAITING_REVEAL_DURATION_MILLIS.toLong())
      .setInterpolator(waitingEntranceInterpolator)
      .withEndAction {
        hasPlayedWaitingSceneEntrance = true
      }.start()
  }

  fun startIdleMotion() {
    if (!isWaitingContent) {
      stopAllAnimation()
      lastFrameNanos = 0L
      return
    }

    angularVelocityY = 0.0f
    rotationMotionMode = RotationMotionMode.IDLE
    lastFrameNanos = 0L
  }

  fun startWaitingIdleMotionIfNeeded() {
    if (!isWaitingContent || sceneView == null) return
    if (hasPlayedWaitingIntro || shouldPlayWaitingIntro) return

    angularVelocityY = 0.0f
    currentModelRotationY = 0.0f
    currentModelTranslationY = defaultTranslationY(content)
    bobElapsedSeconds = 0.0f
    rotationMotionMode = RotationMotionMode.IDLE
    lastFrameNanos = 0L
    applyModelTransform()
  }

  fun startWaitingIntro() {
    if (!isWaitingContent || sceneView == null) return
    if (hasPlayedWaitingIntro) return

    hasPlayedWaitingIntro = true
    angularVelocityY = 0.0f
    introElapsedSeconds = 0.0f
    introStartRotationY = currentModelRotationY
    introStartTranslationY = currentModelTranslationY + waitingBobTranslationOffsetY(bobElapsedSeconds)
    currentModelTranslationY = introStartTranslationY
    rotationMotionMode = RotationMotionMode.INTRO
    lastFrameNanos = 0L
    applyModelTransform()
  }

  val sceneModifier = modifier.then(
    if (isInteractiveScene) {
      Modifier
        .motionEventSpy { motionEvent ->
          when (motionEvent.actionMasked) {
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            ->
              updateGestureInterception(
                disallowIntercept = false,
                deferViewParentUpdate = true
              )
          }
        }.pointerInteropFilter(
          requestDisallowInterceptTouchEvent = requestDisallowInterceptTouchEvent,
          onTouchEvent = { false }
        )
    } else {
      Modifier
    }
  )

  LaunchedEffect(content, sceneView) {
    if (!isWaitingContent) return@LaunchedEffect

    startWaitingSceneEntranceIfNeeded()
  }

  LaunchedEffect(content, shouldPlayWaitingIntro, sceneView) {
    if (!isWaitingContent) return@LaunchedEffect

    if (shouldPlayWaitingIntro) {
      startWaitingIntro()
    } else {
      startWaitingIdleMotionIfNeeded()
      delay(waitingIntroStartDelayMillis())
      startWaitingIntro()
    }
  }

  DisposableEffect(velocityTracker) {
    onDispose {
      velocityTracker.recycle()
    }
  }

  DisposableEffect(modelNode, screenVideoMaterial, screenVideoPlayer, lifecycleOwner) {
    val screenRenderableNode = modelNode.renderableNodes.getOrNull(BITKEY_DEVICE_SCREEN_NODE_NAME)
    val originalScreenMaterialInstance = screenRenderableNode?.materialInstance

    if (screenRenderableNode != null && screenVideoMaterial != null) {
      screenRenderableNode.materialInstance = screenVideoMaterial.instance
    }

    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START,
        Lifecycle.Event.ON_RESUME,
        -> latestScreenVideoPlayer?.start()
        Lifecycle.Event.ON_PAUSE,
        Lifecycle.Event.ON_STOP,
        Lifecycle.Event.ON_DESTROY,
        -> latestScreenVideoPlayer?.pause()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)

    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
      screenVideoPlayer?.start()
    }

    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      if (screenRenderableNode != null && originalScreenMaterialInstance != null) {
        screenRenderableNode.materialInstance = originalScreenMaterialInstance
      }
      try {
        screenVideoPlayer?.pause()
      } catch (_: IllegalStateException) {
        // MediaPlayer can already be torn down when this scene disposes during navigation.
      }
      screenVideoPlayer?.release()
      screenVideoMaterial?.let { videoMaterial ->
        engine.safeDestroyMaterialInstance(videoMaterial.instance)
        videoMaterial.destroy()
      }
    }
  }

  DisposableEffect(modelNode, customInlayResources) {
    val inlayRenderableNode = modelNode.renderableNodes.getOrNull(BITKEY_DEVICE_INLAY_NODE_NAME)
    val originalInlayMaterialInstance = inlayRenderableNode?.materialInstance

    if (inlayRenderableNode != null && customInlayResources != null) {
      inlayRenderableNode.materialInstance = customInlayResources.materialInstance
    }

    onDispose {
      if (inlayRenderableNode != null && originalInlayMaterialInstance != null) {
        inlayRenderableNode.materialInstance = originalInlayMaterialInstance
      }
      customInlayResources?.let { resources ->
        engine.safeDestroyMaterialInstance(resources.materialInstance)
        engine.destroyTexture(resources.texture)
      }
    }
  }

  DisposableEffect(modelNode, screenBatteryOverlayNode) {
    val screenRenderableNode = modelNode.renderableNodes.getOrNull(BITKEY_DEVICE_SCREEN_NODE_NAME)

    if (screenRenderableNode != null && screenBatteryOverlayNode != null) {
      screenRenderableNode.addChildNode(screenBatteryOverlayNode)
    }

    onDispose {
      if (screenRenderableNode != null && screenBatteryOverlayNode != null) {
        screenRenderableNode.removeChildNode(screenBatteryOverlayNode)
        screenBatteryOverlayNode.destroy()
      }
    }
  }

  DisposableEffect(composeView, lifecycleOwner, requestDisallowInterceptTouchEvent) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START,
        Lifecycle.Event.ON_RESUME,
        -> latestSceneView?.let(::updateSceneVisibility)
        Lifecycle.Event.ON_PAUSE,
        Lifecycle.Event.ON_STOP,
        -> {
          latestSceneView?.visibility = INVISIBLE
          updateGestureInterception(disallowIntercept = false)
        }
        Lifecycle.Event.ON_DESTROY -> {
          latestSceneView?.visibility = INVISIBLE
          updateGestureInterception(disallowIntercept = false)
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)

    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      updateGestureInterception(disallowIntercept = false)
      latestSceneView?.visibility = INVISIBLE
    }
  }

  SideEffect {
    indirectLight.intensity = baseHdriIntensity * BITKEY_DEVICE_HDRI_INTENSITY_MULTIPLIER
    indirectLight.setRotation(
      hdriRotationMatrix(
        yawDegrees = BITKEY_DEVICE_HDRI_ROTATION_DEGREES,
        pitchDegrees = BITKEY_DEVICE_HDRI_ELEVATION_DEGREES
      )
    )
    mainLightNode.intensity = if (BITKEY_DEVICE_FILL_LIGHT_ENABLED) {
      BITKEY_DEVICE_FILL_LIGHT_INTENSITY
    } else {
      0.0f
    }
    mainLightNode.lightDirection = fillLightDirection(
      azimuthDegrees = BITKEY_DEVICE_FILL_LIGHT_AZIMUTH_DEGREES,
      elevationDegrees = BITKEY_DEVICE_FILL_LIGHT_ELEVATION_DEGREES
    )
    cameraNode.position = Position(y = BITKEY_DEVICE_CAMERA_OFFSET_Y, z = BITKEY_DEVICE_CAMERA_DISTANCE)
    cameraNode.lookAt(Position(y = BITKEY_DEVICE_CAMERA_TARGET_OFFSET_Y))
    applyModelTransform()
  }

  Scene(
    modifier = sceneModifier,
    childNodes = childNodes,
    engine = engine,
    view = view,
    renderer = renderer,
    scene = scene,
    collisionSystem = collisionSystem,
    cameraNode = cameraNode,
    environment = environment,
    mainLightNode = mainLightNode,
    isOpaque = false,
    cameraManipulator = null,
    onGestureListener = null,
    onTouchEvent = { motionEvent, _ ->
      if (!isInteractiveScene) {
        return@Scene false
      }

      when (motionEvent.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          updateGestureInterception(disallowIntercept = false)
          velocityTracker.clear()
          velocityTracker.addMovement(motionEvent)
          stopAllAnimation()
          lastFrameNanos = 0L
          initialTouchX = motionEvent.x
          initialTouchY = motionEvent.y
          lastTouchX = motionEvent.x
          totalDragDeltaX = 0.0f
          gestureIntent = null
        }
        MotionEvent.ACTION_MOVE -> {
          velocityTracker.addMovement(motionEvent)
          val totalDeltaX = motionEvent.x - initialTouchX
          val totalDeltaY = motionEvent.y - initialTouchY

          if (gestureIntent == null) {
            val absDeltaX = abs(totalDeltaX)
            val absDeltaY = abs(totalDeltaY)
            val exceededTouchSlop = absDeltaX > touchSlop || absDeltaY > touchSlop

            if (!exceededTouchSlop) {
              return@Scene true
            }

            gestureIntent = if (absDeltaX > absDeltaY) {
              RotationGestureIntent.HORIZONTAL
            } else {
              RotationGestureIntent.VERTICAL
            }

            if (gestureIntent == RotationGestureIntent.HORIZONTAL) {
              updateGestureInterception(disallowIntercept = true)
            } else {
              updateGestureInterception(disallowIntercept = false)
              velocityTracker.clear()
              return@Scene false
            }
          }

          if (gestureIntent == RotationGestureIntent.VERTICAL) {
            return@Scene false
          }

          val deltaX = motionEvent.x - lastTouchX
          lastTouchX = motionEvent.x
          totalDragDeltaX = totalDeltaX

          if (abs(deltaX) > 0.0f) {
            stopAllAnimation()
            currentModelRotationY += deltaX * BITKEY_DEVICE_ROTATION_DEGREES_PER_POINT
            applyModelTransform()
          }
        }
        MotionEvent.ACTION_UP -> {
          if (gestureIntent != RotationGestureIntent.HORIZONTAL) {
            updateGestureInterception(
              disallowIntercept = false,
              deferViewParentUpdate = true
            )
            velocityTracker.clear()
            lastFrameNanos = 0L
            totalDragDeltaX = 0.0f
            gestureIntent = null
            return@Scene false
          }

          velocityTracker.addMovement(motionEvent)
          velocityTracker.computeCurrentVelocity(1000)
          angularVelocityY = velocityTracker.xVelocity * BITKEY_DEVICE_INERTIA_VELOCITY_SCALE
          if (
            abs(velocityTracker.xVelocity) >= minimumFlingVelocity &&
            abs(angularVelocityY) >= BITKEY_DEVICE_INERTIA_STOP_THRESHOLD
          ) {
            rotationMotionMode = RotationMotionMode.MOMENTUM
          } else if (
            !isWaitingContent &&
            abs(totalDragDeltaX * BITKEY_DEVICE_ROTATION_DEGREES_PER_POINT) >=
            BITKEY_DEVICE_DIRECTIONAL_SNAP_THRESHOLD_DEGREES
          ) {
            startDirectionalSnap(totalDragDeltaX)
          } else {
            startSnapToNearestAnchor()
          }
          lastFrameNanos = 0L
          lastTouchX = motionEvent.x
          totalDragDeltaX = 0.0f
          gestureIntent = null
          velocityTracker.clear()
          updateGestureInterception(
            disallowIntercept = false,
            deferViewParentUpdate = true
          )
        }
        MotionEvent.ACTION_CANCEL -> {
          if (gestureIntent != RotationGestureIntent.HORIZONTAL) {
            updateGestureInterception(
              disallowIntercept = false,
              deferViewParentUpdate = true
            )
            velocityTracker.clear()
            totalDragDeltaX = 0.0f
            gestureIntent = null
            return@Scene false
          }

          velocityTracker.clear()
          if (
            !isWaitingContent &&
            abs(totalDragDeltaX * BITKEY_DEVICE_ROTATION_DEGREES_PER_POINT) >=
            BITKEY_DEVICE_DIRECTIONAL_SNAP_THRESHOLD_DEGREES
          ) {
            startDirectionalSnap(totalDragDeltaX)
          } else {
            startSnapToNearestAnchor()
          }
          totalDragDeltaX = 0.0f
          gestureIntent = null
          updateGestureInterception(
            disallowIntercept = false,
            deferViewParentUpdate = true
          )
        }
      }
      true
    },
    onViewCreated = {
      sceneView = this
      updateSceneVisibility(this)
      if (isWaitingContent) {
        prepareWaitingSceneEntrance(this)
        startWaitingIdleMotionIfNeeded()
        if (shouldPlayWaitingIntro) startWaitingIntro()
      }
    },
    onViewUpdated = {
      sceneView = this
      updateSceneVisibility(this)
    },
    onFrame = { frameTimeNanos ->
      when (rotationMotionMode) {
        RotationMotionMode.INTRO -> {
          val dt = frameDeltaSeconds(frameTimeNanos, lastFrameNanos)
          lastFrameNanos = frameTimeNanos
          introElapsedSeconds += dt
          val introProgress = (
            introElapsedSeconds /
              (BITKEY_DEVICE_WAITING_INTRO_DURATION_MILLIS.toFloat() / 1000.0f)
          ).coerceIn(0.0f, 1.0f)
          val easedProgress = FormDsV2WaitingRevealEasing.transform(introProgress)
          currentModelRotationY = lerp(introStartRotationY, 180.0f, easedProgress)
          currentModelTranslationY = lerp(
            introStartTranslationY,
            BITKEY_DEVICE_WAITING_INTRO_TARGET_TRANSLATION_Y,
            easedProgress
          )
          applyModelTransform()

          if (introProgress >= 1.0f) {
            currentModelRotationY = 180.0f
            currentModelTranslationY = BITKEY_DEVICE_WAITING_INTRO_TARGET_TRANSLATION_Y
            bobElapsedSeconds = 0.0f
            applyModelTransform()
            startIdleMotion()
          }
        }
        RotationMotionMode.IDLE -> {
          val dt = frameDeltaSeconds(frameTimeNanos, lastFrameNanos)
          lastFrameNanos = frameTimeNanos
          bobElapsedSeconds += dt
          applyModelTransform()
        }
        RotationMotionMode.MOMENTUM -> {
          val dt = frameDeltaSeconds(frameTimeNanos, lastFrameNanos)
          lastFrameNanos = frameTimeNanos
          currentModelRotationY += angularVelocityY * dt
          applyModelTransform()

          angularVelocityY *= exp(-BITKEY_DEVICE_INERTIA_DAMPING * dt)
          if (abs(angularVelocityY) < BITKEY_DEVICE_INERTIA_STOP_THRESHOLD) {
            startSnapToNearestAnchor()
          }
        }
        RotationMotionMode.SNAP -> {
          val dt = frameDeltaSeconds(frameTimeNanos, lastFrameNanos)
          lastFrameNanos = frameTimeNanos
          val deltaToTarget = snapTargetRotationY - currentModelRotationY
          currentModelRotationY += deltaToTarget * (1.0f - exp(-BITKEY_DEVICE_ROTATION_SNAP_RATE * dt))
          applyModelTransform()

          if (abs(deltaToTarget) < BITKEY_DEVICE_ROTATION_SNAP_THRESHOLD) {
            currentModelRotationY = snapTargetRotationY
            applyModelTransform()
            startIdleMotion()
          }
        }
        null -> {
          lastFrameNanos = frameTimeNanos
        }
      }
    }
  )
}

@Composable
internal actual fun BitkeyDeviceMediaInteractionOverlay(
  modifier: Modifier,
  interactionState: BitkeyDeviceMediaInteractionState,
) = Unit

internal actual fun supportsBitkeyDevice3DMedia(hardwareType: HardwareType): Boolean =
  hardwareType == HardwareType.W3

@Composable
internal actual fun supportsInteractiveBitkeyWaitingMedia(hardwareType: HardwareType): Boolean =
  supportsBitkeyDevice3DMedia(hardwareType)

private fun defaultTranslationY(content: DeviceStatusCard.VideoContent): Float =
  when (content) {
    DeviceStatusCard.VideoContent.BITKEY_ROTATE -> 0.0f
    DeviceStatusCard.VideoContent.BITKEY_WAITING_3D -> BITKEY_DEVICE_WAITING_REST_TRANSLATION_Y
  }

private fun waitingBobTranslationOffsetY(bobElapsedSeconds: Float): Float {
  val phase = (bobElapsedSeconds / BITKEY_DEVICE_WAITING_BOB_PERIOD_SECONDS) * (2.0f * Math.PI.toFloat())
  return sin(phase) * BITKEY_DEVICE_WAITING_BOB_AMPLITUDE_Y
}

private fun frameDeltaSeconds(
  frameTimeNanos: Long,
  previousFrameNanos: Long,
): Float {
  if (previousFrameNanos == 0L) {
    return 1.0f / 60.0f
  }

  return ((frameTimeNanos - previousFrameNanos) / 1_000_000_000.0f)
    .coerceAtMost(0.1f)
}

private fun Float.normalizeDegrees(): Float {
  var normalizedValue = this % 360.0f
  if (normalizedValue < 0.0f) {
    normalizedValue += 360.0f
  }
  return normalizedValue
}

private fun nearestAnchorRotation(rotationDegrees: Float): Float {
  val normalizedRotation = rotationDegrees.normalizeDegrees()
  val nearestAnchorDegrees = listOf(0.0f, 180.0f)
    .minBy { anchorDegrees ->
      abs(shortestAngularDeltaDegrees(normalizedRotation, anchorDegrees))
    }

  return rotationDegrees + shortestAngularDeltaDegrees(normalizedRotation, nearestAnchorDegrees)
}

private fun anchorRotationInDirection(
  rotationDegrees: Float,
  directionX: Float,
): Float {
  val directionSign = when {
    directionX > BITKEY_DEVICE_DIRECTION_EPSILON -> 1.0f
    directionX < -BITKEY_DEVICE_DIRECTION_EPSILON -> -1.0f
    else -> 0.0f
  }

  if (directionSign == 0.0f) {
    return nearestAnchorRotation(rotationDegrees)
  }

  val normalizedRotation = rotationDegrees.normalizeDegrees()
  val directionalDelta = listOf(0.0f, 180.0f)
    .map { anchorDegrees ->
      directionalAngularDeltaDegrees(
        fromDegrees = normalizedRotation,
        toDegrees = anchorDegrees,
        directionSign = directionSign
      )
    }.filter { deltaDegrees ->
      deltaDegrees * directionSign > BITKEY_DEVICE_DIRECTION_EPSILON
    }.minByOrNull { deltaDegrees ->
      abs(deltaDegrees)
    }

  return rotationDegrees + (
    directionalDelta ?: shortestAngularDeltaDegrees(
      fromDegrees = normalizedRotation,
      toDegrees = listOf(0.0f, 180.0f).minBy { anchorDegrees ->
        abs(shortestAngularDeltaDegrees(normalizedRotation, anchorDegrees))
      }
    )
  )
}

private fun shortestAngularDeltaDegrees(
  fromDegrees: Float,
  toDegrees: Float,
): Float = ((toDegrees - fromDegrees + 540.0f) % 360.0f) - 180.0f

private fun directionalAngularDeltaDegrees(
  fromDegrees: Float,
  toDegrees: Float,
  directionSign: Float,
): Float {
  val deltaDegrees = shortestAngularDeltaDegrees(fromDegrees, toDegrees)
  val isHalfTurn = abs(abs(deltaDegrees) - 180.0f) < BITKEY_DEVICE_DIRECTION_EPSILON

  return if (isHalfTurn) {
    180.0f * directionSign
  } else {
    deltaDegrees
  }
}

private fun fillLightDirection(
  azimuthDegrees: Float = BITKEY_DEVICE_FILL_LIGHT_AZIMUTH_DEGREES,
  elevationDegrees: Float = BITKEY_DEVICE_FILL_LIGHT_ELEVATION_DEGREES,
): Position {
  val azimuthRadians = azimuthDegrees.toRadians()
  val elevationRadians = elevationDegrees.toRadians()
  val horizontalComponent = cos(elevationRadians)

  return Position(
    x = sin(azimuthRadians) * horizontalComponent,
    y = -sin(elevationRadians),
    z = -cos(azimuthRadians) * horizontalComponent
  )
}

private fun hdriRotationMatrix(
  yawDegrees: Float,
  pitchDegrees: Float,
): FloatArray {
  val yawRadians = yawDegrees.toRadians()
  val pitchRadians = pitchDegrees.toRadians()
  val yawCosine = cos(yawRadians)
  val yawSine = sin(yawRadians)
  val pitchCosine = cos(pitchRadians)
  val pitchSine = sin(pitchRadians)

  return floatArrayOf(
    yawCosine, 0.0f, -yawSine,
    pitchSine * yawSine, pitchCosine, pitchSine * yawCosine,
    pitchCosine * yawSine, -pitchSine, pitchCosine * yawCosine
  )
}

private fun waitingIntroStartDelayMillis(): Long =
  (
    FORM_DS_V2_WAITING_REVEAL_DELAY_MILLIS -
      BITKEY_DEVICE_WAITING_INTRO_DURATION_MILLIS -
      BITKEY_DEVICE_WAITING_BUTTON_REVEAL_GAP_MILLIS
  ).coerceAtLeast(0).toLong()

@Suppress("DiscouragedApi")
private fun bitkeyDeviceScreenVideoResourceId(context: Context): Int =
  context.resources.getIdentifier(
    BITKEY_DEVICE_SCREEN_VIDEO_RESOURCE_NAME,
    "raw",
    context.packageName
  )

private fun lerp(
  startValue: Float,
  endValue: Float,
  fraction: Float,
): Float = startValue + (endValue - startValue) * fraction

private fun Float.toRadians(): Float = (this / 180.0f * Math.PI).toFloat()

private fun createCustomInlayResources(
  engine: Engine,
  context: Context,
  modelNode: ModelNode,
  serialNumber: String,
  serialNumberTypeface: Typeface,
): BitkeyDeviceCustomInlayResources? {
  val inlayRenderableNode = modelNode.renderableNodes.getOrNull(BITKEY_DEVICE_INLAY_NODE_NAME) ?: return null
  val customTexture = createCustomInlayTexture(
    engine = engine,
    context = context,
    serialNumber = serialNumber,
    serialNumberTypeface = serialNumberTypeface
  ) ?: return null
  val customMaterialInstance = MaterialInstance.duplicate(
    inlayRenderableNode.materialInstance,
    "$BITKEY_DEVICE_INLAY_NODE_NAME-$serialNumber"
  )
  customMaterialInstance.setParameter("baseColorMap", customTexture, TextureSampler())

  return BitkeyDeviceCustomInlayResources(
    materialInstance = customMaterialInstance,
    texture = customTexture
  )
}

private fun createCustomInlayTexture(
  engine: Engine,
  context: Context,
  serialNumber: String,
  serialNumberTypeface: Typeface,
): Texture? {
  val baseBitmap = context.assets.open(BITKEY_DEVICE_INLAY_TEXTURE_ASSET_LOCATION).use {
      inputStream ->
    BitmapFactory.decodeStream(inputStream)
  } ?: return null

  val mutableBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
  baseBitmap.recycle()

  val overlayRect = scaledRect(mutableBitmap, BITKEY_DEVICE_SERIAL_OVERLAY_RECT_LEFT, BITKEY_DEVICE_SERIAL_OVERLAY_RECT_TOP, BITKEY_DEVICE_SERIAL_OVERLAY_RECT_RIGHT, BITKEY_DEVICE_SERIAL_OVERLAY_RECT_BOTTOM)
  val backgroundSampleRect = scaledRect(mutableBitmap, BITKEY_DEVICE_SERIAL_BACKGROUND_SAMPLE_LEFT, BITKEY_DEVICE_SERIAL_BACKGROUND_SAMPLE_TOP, BITKEY_DEVICE_SERIAL_BACKGROUND_SAMPLE_RIGHT, BITKEY_DEVICE_SERIAL_BACKGROUND_SAMPLE_BOTTOM)
  val backgroundColor = sampleAverageColor(mutableBitmap, backgroundSampleRect)
  val scale = mutableBitmap.height / BITKEY_DEVICE_INLAY_TEXTURE_BASE_HEIGHT

  Canvas(mutableBitmap).apply {
    drawRoundRect(
      overlayRect,
      BITKEY_DEVICE_SERIAL_TEXT_CORNER_RADIUS * scale,
      BITKEY_DEVICE_SERIAL_TEXT_CORNER_RADIUS * scale,
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = backgroundColor
      }
    )

    // Rotate the serial text 180 degrees so it faces the correct direction on the device.
    save()
    rotate(180.0f, overlayRect.centerX(), overlayRect.centerY())

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      color = BITKEY_DEVICE_SERIAL_TEXT_COLOR
      textSize = BITKEY_DEVICE_SERIAL_TEXT_SIZE * scale
      typeface = serialNumberTypeface
      letterSpacing = 0.01f
      isLinearText = true
    }
    val displaySerial = "SN:${serialNumber.trim()}"
    val maxTextWidth = overlayRect.width() - (BITKEY_DEVICE_SERIAL_TEXT_PADDING_RIGHT * scale)
    while (textPaint.measureText(displaySerial) > maxTextWidth && textPaint.textSize > BITKEY_DEVICE_SERIAL_TEXT_MIN_SIZE * scale) {
      textPaint.textSize *= 0.96f
    }

    val fontMetrics = textPaint.fontMetrics
    val baseline = overlayRect.top + ((overlayRect.height() - fontMetrics.bottom + fontMetrics.top) / 2.0f) - fontMetrics.top

    drawText(
      displaySerial,
      scaledX(mutableBitmap, BITKEY_DEVICE_SERIAL_TEXT_LEFT),
      baseline,
      textPaint
    )

    restore()
  }

  return ImageTexture.Builder()
    .bitmap(mutableBitmap)
    .build(engine).also {
      mutableBitmap.recycle()
    }
}

private fun scaledRect(
  bitmap: Bitmap,
  left: Float,
  top: Float,
  right: Float,
  bottom: Float,
): RectF =
  RectF(
    scaledX(bitmap, left),
    scaledY(bitmap, top),
    scaledX(bitmap, right),
    scaledY(bitmap, bottom)
  )

private fun scaledX(
  bitmap: Bitmap,
  value: Float,
): Float = bitmap.width * (value / BITKEY_DEVICE_INLAY_TEXTURE_BASE_WIDTH)

private fun scaledY(
  bitmap: Bitmap,
  value: Float,
): Float = bitmap.height * (value / BITKEY_DEVICE_INLAY_TEXTURE_BASE_HEIGHT)

private fun sampleAverageColor(
  bitmap: Bitmap,
  sampleRect: RectF,
): Int {
  val rect = Rect(
    sampleRect.left.toInt().coerceIn(0, bitmap.width - 1),
    sampleRect.top.toInt().coerceIn(0, bitmap.height - 1),
    sampleRect.right.toInt().coerceIn(1, bitmap.width),
    sampleRect.bottom.toInt().coerceIn(1, bitmap.height)
  )
  var redTotal = 0L
  var greenTotal = 0L
  var blueTotal = 0L
  var alphaTotal = 0L
  var sampleCount = 0L

  for (y in rect.top until rect.bottom step 2) {
    for (x in rect.left until rect.right step 2) {
      val color = bitmap[x, y]
      alphaTotal += Color.alpha(color)
      redTotal += Color.red(color)
      greenTotal += Color.green(color)
      blueTotal += Color.blue(color)
      sampleCount += 1
    }
  }

  if (sampleCount == 0L) {
    return Color.BLACK
  }

  return Color.argb(
    (alphaTotal / sampleCount).toInt(),
    (redTotal / sampleCount).toInt(),
    (greenTotal / sampleCount).toInt(),
    (blueTotal / sampleCount).toInt()
  )
}

private fun loadBitkeyDeviceSerialTypeface(context: Context): Typeface {
  return try {
    Typeface.createFromAsset(context.assets, BITKEY_DEVICE_SERIAL_TEXT_FONT_ASSET_LOCATION)
  } catch (_: RuntimeException) {
    Typeface.MONOSPACE
  }
}

private fun createScreenBatteryOverlayNode(
  materialLoader: MaterialLoader,
  screenRenderableNode: ModelNode.RenderableNode,
  batteryPercentage: Int,
): ImageNode {
  val overlayBitmap = createScreenBatteryOverlayBitmap(batteryPercentage)
  val halfExtent = screenRenderableNode.axisAlignedBoundingBox.halfExtent
  val overlaySize = Size(
    x = halfExtent[0] * 2.0f * BITKEY_DEVICE_SCREEN_OVERLAY_SIZE_SCALE,
    y = halfExtent[1] * 2.0f * BITKEY_DEVICE_SCREEN_OVERLAY_SIZE_SCALE,
    z = 0.0f
  )

  return ImageNode(
    materialLoader = materialLoader,
    bitmap = overlayBitmap,
    size = overlaySize,
    center = Position(z = halfExtent[2] + BITKEY_DEVICE_SCREEN_OVERLAY_DEPTH_OFFSET)
  ).apply {
    setCulling(true)
    isShadowCaster = false
    isShadowReceiver = false
  }
}

private fun createScreenBatteryOverlayBitmap(batteryPercentage: Int): Bitmap {
  val normalizedBatteryPercentage = batteryPercentage.coerceIn(0, 100)
  val isLow = normalizedBatteryPercentage <= 10
  val tintColor = if (isLow) BITKEY_DEVICE_SCREEN_OVERLAY_LOW_COLOR else BITKEY_DEVICE_SCREEN_OVERLAY_COLOR
  val fillFraction = when {
    normalizedBatteryPercentage <= 10 -> 2.0f / 12.0f
    normalizedBatteryPercentage <= 25 -> 3.0f / 12.0f
    normalizedBatteryPercentage <= 50 -> 6.5f / 12.0f
    normalizedBatteryPercentage <= 75 -> 9.5f / 12.0f
    else -> 1.0f
  }
  val overlayBitmap = createBitmap(
    width = BITKEY_DEVICE_SCREEN_OVERLAY_TEXTURE_SIZE,
    height = BITKEY_DEVICE_SCREEN_OVERLAY_TEXTURE_SIZE
  )
  val overlaySize = BITKEY_DEVICE_SCREEN_OVERLAY_TEXTURE_SIZE.toFloat()
  val displayText = "$normalizedBatteryPercentage%"
  val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
    color = tintColor
    textSize = BITKEY_DEVICE_SCREEN_OVERLAY_TEXT_SIZE
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    isLinearText = true
  }
  val rowWidth = BITKEY_DEVICE_SCREEN_OVERLAY_ICON_SIZE +
    BITKEY_DEVICE_SCREEN_OVERLAY_ICON_TEXT_SPACING + textPaint.measureText(displayText)
  val rowLeft = (overlaySize - rowWidth) / 2.0f
  val iconTop = overlaySize - BITKEY_DEVICE_SCREEN_OVERLAY_BOTTOM_PADDING -
    BITKEY_DEVICE_SCREEN_OVERLAY_ICON_SIZE
  val iconRect = RectF(
    rowLeft,
    iconTop,
    rowLeft + BITKEY_DEVICE_SCREEN_OVERLAY_ICON_SIZE,
    iconTop + BITKEY_DEVICE_SCREEN_OVERLAY_ICON_SIZE
  )
  val fontMetrics = textPaint.fontMetrics
  val textBaseline = iconRect.top + ((iconRect.height() - fontMetrics.bottom + fontMetrics.top) / 2.0f) - fontMetrics.top

  Canvas(overlayBitmap).apply {
    drawBitkeyDeviceBatteryIcon(
      canvas = this,
      iconRect = iconRect,
      fillFraction = fillFraction,
      tintColor = tintColor
    )
    drawText(
      displayText,
      iconRect.right + BITKEY_DEVICE_SCREEN_OVERLAY_ICON_TEXT_SPACING,
      textBaseline,
      textPaint
    )
  }

  return overlayBitmap
}

private fun drawBitkeyDeviceBatteryIcon(
  canvas: Canvas,
  iconRect: RectF,
  fillFraction: Float,
  tintColor: Int,
) {
  val width = iconRect.width()
  val height = iconRect.height()
  val outlineWidth = width * (2.0f / 24.0f)
  val bodyRect = RectF(
    iconRect.left + (1.0f / 24.0f * width),
    iconRect.top + (5.0f / 24.0f * height),
    iconRect.left + (19.0f / 24.0f * width),
    iconRect.top + (19.0f / 24.0f * height)
  )
  val capRect = RectF(
    iconRect.left + (21.5f / 24.0f * width),
    iconRect.top + (9.5f / 24.0f * height),
    iconRect.left + (23.5f / 24.0f * width),
    iconRect.top + (14.5f / 24.0f * height)
  )
  val fillLeft = iconRect.left + (4.5f / 24.0f * width)
  val fillRight = iconRect.left + (16.5f / 24.0f * width)
  val fillRect = RectF(
    fillLeft,
    iconRect.top + (8.5f / 24.0f * height),
    fillLeft + ((fillRight - fillLeft) * fillFraction.coerceIn(0.0f, 1.0f)),
    iconRect.top + (15.5f / 24.0f * height)
  )
  val cornerRadius = width * (2.0f / 24.0f)

  canvas.drawRoundRect(
    fillRect,
    cornerRadius * 0.6f,
    cornerRadius * 0.6f,
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = tintColor
    }
  )
  canvas.drawRoundRect(
    bodyRect,
    cornerRadius,
    cornerRadius,
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = outlineWidth
      color = tintColor
    }
  )
  canvas.drawRoundRect(
    capRect,
    cornerRadius * 0.4f,
    cornerRadius * 0.4f,
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = tintColor
    }
  )
}
