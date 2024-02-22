package build.wallet.statemachine.data.money.currency

import build.wallet.money.display.CurrencyPreferenceData
import build.wallet.statemachine.core.StateMachine

interface CurrencyPreferenceDataStateMachine : StateMachine<Unit, CurrencyPreferenceData>
