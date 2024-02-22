import Foundation
import Shared
import SwiftUI

// MARK: -

public struct AmountSliderView: View {

    let viewModel: AmountSliderModel

    public var body: some View {
        VStack(spacing: 8) {
            // Primary amount
            HStack {
                Spacer()
                ModeledText(
                    model: .standard(viewModel.primaryAmount, font: .display2, textAlignment: nil)
                )
                .numericTextAnimation(numericText: viewModel.primaryAmount)
                Spacer()
            }

            // Secondary amount
            ModeledText(
                model: .standard(viewModel.secondaryAmount, font: .body1Medium, textAlignment: nil, textColor: .foreground60)
            )

            // Slider
            Slider(
                value: .init(
                    get: { Float(viewModel.value) },
                    set: { viewModel.onValueUpdate(.init(float: $0)) }
                ),
                in: viewModel.valueRange.nativeRange
            )
            .tint(.primary)
            .frame(maxWidth: .infinity)
            .disabled(!viewModel.isEnabled)
        }
        .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
        .padding(.vertical, DesignSystemMetrics.verticalPadding)
        .overlay(
            RoundedRectangle(cornerRadius: 24)
                .stroke(Color.foreground10, lineWidth: 2)
        )
    }

}

private extension KotlinClosedFloatingPointRange {

    var nativeRange: ClosedRange<Float> {
        let lowerBound = (start as! KotlinFloat).floatValue
        let upperBound = (endInclusive as! KotlinFloat).floatValue
        return lowerBound...upperBound
    }

}
