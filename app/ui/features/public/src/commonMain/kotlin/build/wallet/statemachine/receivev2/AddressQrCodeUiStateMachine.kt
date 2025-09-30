package build.wallet.statemachine.receivev2

import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.receive.AddressQrCodeUiProps

/**
 * A state machine with receiving functionality (wallet address, QR code).
 */
interface AddressQrCodeUiStateMachine : StateMachine<AddressQrCodeUiProps, BodyModel>
