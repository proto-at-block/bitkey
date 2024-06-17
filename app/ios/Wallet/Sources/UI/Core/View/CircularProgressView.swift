import Foundation
import Shared
import SwiftUI

// MARK: -

public struct CircularProgressView: View {

    // MARK: - Private Properties

    private var progress: CGFloat {
        remainingDuration / totalDuration
    }

    private let totalDuration: TimeInterval
    @SwiftUI.State
    private var remainingDuration: TimeInterval = 0
    // This needs to be < 1 second otherwise there's a noticable delay in the start of the animation
    // when the view appears
    private static let updateInterval = 0.3
    private let timer = Timer.publish(every: updateInterval, on: .main, in: .common).autoconnect()

    // Visual
    private let timerDirection: TimerDirection
    private let progressColor: Color
    private let strokeWidth: CGFloat

    // MARK: - Life Cycle

    public init(
        progress: Float,
        direction: TimerDirection,
        remainingDuration: TimeInterval,
        progressColor: Color = .primary,
        strokeWidth: CGFloat = DesignSystemMetrics.Timer.strokeWidth
    ) {
        let remainingProgress = 1 - Double(progress)
        self.remainingDuration = remainingDuration
        self.timerDirection = direction
        self.totalDuration = remainingDuration / (remainingProgress == 0 ? 1 : remainingProgress)
        self.progressColor = progressColor
        self.strokeWidth = strokeWidth
    }

    // MARK: - View

    public var body: some View {
        ZStack {
            Circle()
                .stroke(
                    Color.foreground10,
                    lineWidth: strokeWidth
                )
            Circle()
                .trim(
                    from: 0,
                    to: timerDirection == TimerDirection
                        .counterclockwise ? progress : (1 - progress)
                )
                .stroke(
                    progressColor,
                    style: StrokeStyle(
                        lineWidth: strokeWidth,
                        lineCap: .round
                    )
                )
                .rotationEffect(.degrees(-90))
        }
        .animation(.linear(duration: Self.updateInterval), value: remainingDuration)
        .onReceive(timer) { _ in
            remainingDuration -= Self.updateInterval
        }
    }
}
