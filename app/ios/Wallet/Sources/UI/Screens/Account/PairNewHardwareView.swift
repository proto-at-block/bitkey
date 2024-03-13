import AVKit
import Foundation
import Shared
import SwiftUI

// MARK: -

public struct PairNewHardwareView: View {

    // MARK: - Public Properties

    @ObservedObject
    private(set) var viewModel: ViewModel

    // MARK: - Life Cycle

    public init(viewModel: PairNewHardwareBodyModel) {
        self.viewModel = .init(viewModel: viewModel)
    }

    // MARK: - View

    public var body: some View {
        GeometryReader { reader in
            VStack(spacing: 0) {
                // Toolbar
                ToolbarView(viewModel: viewModel.toolbarModel)
                    .padding(.top, reader.safeAreaInsets.top == 0 ? DesignSystemMetrics.verticalPadding : 0)
                    .padding(.horizontal, DesignSystemMetrics.horizontalPadding)

                Spacer()

                // Header and button
                VStack {
                    FormHeaderView(viewModel: viewModel.viewModel.header, headlineFont: .title1, headlineTextColor: .white, sublineTextColor: .white)
                        .id(viewModel.animatableContentId) // Trigger animation when the text changes
                        .transition(viewModel.transition)

                    Spacer()
                        .frame(height: 24)
                    ButtonView(model: viewModel.viewModel.primaryButton)
                        .id(viewModel.animatableContentId) // Trigger animation when the text changes
                        .transition(viewModel.transition)
                }
                .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
                .padding(.bottom, DesignSystemMetrics.verticalPadding)
                .fixedSize(horizontal: false, vertical: true)
            }
            .background(
                // Background video
                VStack {
                    Spacer()

                    VideoView(viewModel: viewModel.videoViewModel)
                        .frame(height: 640)
                        .padding(.horizontal, -100) // Let the video width extend beyond the edges a bit
                        .clipped()

                    Spacer(minLength: 250) // The minLength ensures enough space on smaller devices
                }
            )
            .background(.black)
        }
    }

}

// MARK: -

extension PairNewHardwareView {
    public class ViewModel: ObservableObject {

        // MARK: - Public Properties

        @Published
        private(set) var viewModel: PairNewHardwareBodyModel

        @Published
        private(set) var toolbarModel: ToolbarModel!

        @Published
        private(set) var videoViewModel: VideoViewModel

        @Published
        private(set) var transition: AnyTransition

        @Published
        private(set) var animatableContentId = UUID()

        // MARK: - Life Cycle

        init(viewModel: PairNewHardwareBodyModel) {
            self.viewModel = viewModel
            self.videoViewModel = .init(
                videoUrl: viewModel.backgroundVideo.videoUrl,
                videoStartingPosition: viewModel.backgroundVideo.startingPosition
            )
            self.transition = viewModel.slideAndFadeTransition()
            self.toolbarModel = viewModel.toolbarModel(
                onRefreshClick: { [weak self] in
                    self?.replayVideo()
                }
            )
        }

        // MARK: - Public Methods

        func update(from viewModel: PairNewHardwareBodyModel) {
            self.videoViewModel.update(
                videoUrl: viewModel.backgroundVideo.videoUrl,
                videoStartingPosition: viewModel.backgroundVideo.startingPosition
            )
            self.toolbarModel = viewModel.toolbarModel(
                onRefreshClick: { [weak self] in
                    self?.replayVideo()
                }
            )

            // Animation the view model update if the content is changing
            if viewModel.header != self.viewModel.header {
                self.transition = viewModel.slideAndFadeTransition()
                withAnimation(.easeInOut(duration: PairNewHardwareBodyModel.Metrics.totalTransitionDuration)) {
                    self.viewModel = viewModel
                    self.animatableContentId = UUID()
                }
            } else {
                self.viewModel = viewModel
            }
        }

        // MARK: - Private Methods

        private func replayVideo() {
            videoViewModel.replayVideo()
        }
    }
}

// MARK: -

private extension PairNewHardwareBodyModel.BackgroundVideo {
    var videoUrl: URL {
        switch content {
        case .bitkeyactivate: return .bitkeyActivateVideoUrl
        case .bitkeyfingerprint: return .bitkeyFingerprintVideoUrl
        case .bitkeypair: return .bitkeyPairVideoUrl
        default:
            fatalError("Unexpected pair new hardware background video: \(content)")
        }
    }
}

// MARK: -

extension PairNewHardwareBodyModel {

    enum Metrics {
        static let slideTransitionXOffset = 50.f
        static let slideTransitionDuration = 0.4
        static let fadeTransitionDuration = 0.2

        static let totalTransitionDuration = max(slideTransitionDuration, fadeTransitionDuration)
    }

    func slideAndFadeTransition() -> AnyTransition {
        func slideTransition(isNegated: Bool) -> AnyTransition {
            return .offset(x: isNegated ? -(Metrics.slideTransitionXOffset) : Metrics.slideTransitionXOffset)
                .animation(.easeInOut(duration: Metrics.slideTransitionDuration))
        }
        let fadeTransition: AnyTransition = .opacity.animation(.easeInOut(duration: Metrics.fadeTransitionDuration))

        return .asymmetric(
            insertion: slideTransition(isNegated: isNavigatingBack).combined(with: fadeTransition),
            removal: slideTransition(isNegated: !isNavigatingBack).combined(with: fadeTransition)
        )
    }

}
