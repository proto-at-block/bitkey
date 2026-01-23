package build.wallet.statemachine.root

import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Duration of some generic action success state. This is used to ensure that we give
 * customer enough time to see the success state before we move on to the next screen.
 *
 * This is usually used in combination with [build.wallet.time.withMinimumDelay].
 *
 * TODO: move this to `UiDelays.kt` once data state machines no longer depend on this type.
 */
@JvmInline
value class ActionSuccessDuration(val value: Duration = 2.seconds)

/**
 * Duration of the welcome screen.
 */
@JvmInline
value class WelcomeToBitkeyScreenDuration(val value: Duration = 3.seconds)

/**
 * Delay before we start animating the splash screen.
 */
@JvmInline
value class SplashScreenDelay(val value: Duration = 2.seconds)

/**
 * Delay before we reset the state of "Copy Address" button from "Copied" back to "Ready".
 */
@JvmInline
value class RestoreCopyAddressStateDelay(val value: Duration = 4.seconds)

/**
 * Delay before we start animating clearing the getting started tasks on home screen.
 */
@JvmInline
value class ClearGettingStartedTasksDelay(val value: Duration = 1.seconds)

/**
 * Duration of the animation of clearing the getting started tasks on home screen.
 */
@JvmInline
value class GettingStartedTasksAnimationDuration(val value: Duration = 1.1.seconds)

/** The duration of delay for the animation of the Bitkey word mark to appear */
@JvmInline
value class BitkeyWordMarkAnimationDelay(val value: Duration = 700.milliseconds)

/** The duration of the animation of the Bitkey word mark to appear */
@JvmInline
value class BitkeyWordMarkAnimationDuration(val value: Duration = 500.milliseconds)

/**
 * Duration of the address QR code loading animation.
 */
@JvmInline
value class AddressQrCodeLoadingDuration(val value: Duration = 500.milliseconds)

/**
 * The frequency at which the remaining recovery delay formatted as readable words is updated.
 * Set to 1 minute because this is the minimum value that we can show, anything less than 1 minute
 * is shown as "Less than 1 minute".
 */
@JvmInline
value class RemainingRecoveryDelayWordsUpdateFrequency(val value: Duration = 1.minutes)
