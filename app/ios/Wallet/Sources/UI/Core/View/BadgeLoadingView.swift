import Lottie
import Shared
import SwiftUI

// MARK: -

struct BadgeLoadingView: View {

    // MARK: - Private Properties

    private let size: IconSize
    private var animation: LottieAnimation {
        .loadingBadge
    }

    // MARK: - Life Cycle

    public init(size: IconSize) {
        self.size = size
    }

    public var body: some View {
        // We need to manually pause the animation when performing snapshot tests to avoid flakes
        if ProcessInfo.isTesting {
            LottieView(animation: animation)
                .paused(at: .progress(0.5))
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(iconSize: size)
        } else {
            LottieView(animation: animation)
                .playing(loopMode: .loop)
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(iconSize: size)
        }
    }
}
