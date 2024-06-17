import Foundation
import Shared
import SwiftUI

public struct KeypadView: View {
    let viewModel: KeypadModel
    public var body: some View {
        VStack {
            HStack {
                KeypadButtonView(buttonContent: .DigitOne(), onClick: viewModel.onButtonPress)
                KeypadButtonView(buttonContent: .DigitTwo(), onClick: viewModel.onButtonPress)
                KeypadButtonView(buttonContent: .DigitThree(), onClick: viewModel.onButtonPress)
            }
            HStack {
                KeypadButtonView(buttonContent: .DigitFour(), onClick: viewModel.onButtonPress)
                KeypadButtonView(buttonContent: .DigitFive(), onClick: viewModel.onButtonPress)
                KeypadButtonView(buttonContent: .DigitSix(), onClick: viewModel.onButtonPress)
            }
            HStack {
                KeypadButtonView(buttonContent: .DigitSeven(), onClick: viewModel.onButtonPress)
                KeypadButtonView(buttonContent: .DigitEight(), onClick: viewModel.onButtonPress)
                KeypadButtonView(buttonContent: .DigitNine(), onClick: viewModel.onButtonPress)
            }
            HStack {
                KeypadButtonView(
                    buttonContent: .Decimal(),
                    onClick: viewModel.onButtonPress,
                    visible: viewModel.showDecimal
                )
                KeypadButtonView(buttonContent: .DigitZero(), onClick: viewModel.onButtonPress)
                KeypadButtonView(buttonContent: .Delete(), onClick: viewModel.onButtonPress)
            }
        }
    }
}

private struct KeypadButtonView: View {
    let buttonContent: KeypadButton
    let onClick: (KeypadButton) -> Void
    var visible: Bool = true
    var body: some View {
        Button {
            onClick(buttonContent)
        } label: {
            KeypadButtonContentView(buttonContent: buttonContent, visible: visible)
                .frame(maxWidth: .infinity, alignment: .center)
        }
        .frame(minHeight: 48, maxHeight: 64)
    }
}

private struct KeypadButtonContentView: View {
    let buttonContent: KeypadButton
    let visible: Bool
    var body: some View {
        switch buttonContent {
        case is KeypadButton.Decimal:
            Circle()
                .foregroundColor(visible ? .foreground : .clear)
                .frame(width: 6, height: 6)

        case is KeypadButton.Delete:
            Image(uiImage: .smallIconCaretLeft)
                .foregroundColor(.foreground)

        case let digit as KeypadButton.Digit:
            ModeledText(model: .standard("\(digit.value)", font: .keypad, textAlignment: nil))
        default:
            fatalError("Unexpected keypad button")
        }
    }
}
