import Foundation
import Shared
import SwiftUI

// MARK: -

public struct TimerView: View {

    // MARK: - Private Properties

    private let viewModel: FormMainContentModelTimer

    // MARK: - Life Cycle

    public init(viewModel: FormMainContentModelTimer) {
        self.viewModel = viewModel
    }

    // MARK: - View

    public var body: some View {
        HStack {
            Spacer()
            ZStack {
                CircularProgressView(
                    progress: viewModel.timerProgress,
                    direction: viewModel.direction,
                    remainingDuration: TimeInterval(viewModel.timerRemainingSeconds)
                )

                VStack {
                    Spacer()
                    ModeledText(model: .standard(viewModel.title, font: .title1, textAlignment: .center))
                    ModeledText(model: .standard(viewModel.subtitle, font: .body3Regular, textAlignment: .center, textColor: .foreground60))
                    Spacer()
                }
            }
            .frame(
                width: DesignSystemMetrics.Timer.size,
                height: DesignSystemMetrics.Timer.size
            )
            Spacer()
        }
    }
}

struct TimerView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            TimerView(
                viewModel: .init(
                    title: "Updating",
                    subtitle: "",
                    timerProgress: 0.10,
                    direction: .clockwise,
                    timerRemainingSeconds: 10800
                )
            ).previewDisplayName("Timer elapsed")

            TimerView(
                viewModel: .init(
                    title: "Updating",
                    subtitle: "",
                    timerProgress: 0.00,
                    direction: .clockwise,
                    timerRemainingSeconds: 10000
                )
            ).previewDisplayName("Timer full")
            
            TimerView(
                viewModel: .init(
                    title: "Replacement ready",
                    subtitle: "",
                    timerProgress: 1.00,
                    direction: .clockwise,
                    timerRemainingSeconds: 0
                )
            ).previewDisplayName("Timer empty")
            
            TimerView(
                viewModel: .init(
                    title: "12 hours",
                    subtitle: "remaining",
                    timerProgress: 0.10,
                    direction: .counterclockwise,
                    timerRemainingSeconds: 10800
                )
            ).previewDisplayName("Timer elapsed counter-clockwise")
            
            TimerView(
                viewModel: .init(
                    title: "12 hours",
                    subtitle: "remaining",
                    timerProgress: 0.00,
                    direction: .counterclockwise,
                    timerRemainingSeconds: 10000
                )
            ).previewDisplayName("Timer full counter-clockwise")
            
            TimerView(
                viewModel: .init(
                    title: "Replacement ready",
                    subtitle: "",
                    timerProgress: 1.00,
                    direction: .counterclockwise,
                    timerRemainingSeconds: 0
                )
            ).previewDisplayName("Timer empty counter-clockwise")
        }
    }
}
