import AVKit
import Foundation
import Shared
import SwiftUI

// MARK: -

public struct PairNewHardwareView: View {

    // MARK: - Private Properties

    private let viewModel: PairNewHardwareBodyModel
    private let backgroundVideoPlayer: AVPlayer
    private let toolbarModel: ToolbarModel

    // MARK: - Life Cycle

    public init(viewModel: PairNewHardwareBodyModel) {
        self.viewModel = viewModel
        self.backgroundVideoPlayer = AVPlayer(url: viewModel.backgroundVideo.videoUrl)
        self.toolbarModel = viewModel.toolbarModel(
            onRefreshClick: { [weak backgroundVideoPlayer] in
                // Replay the background video
                backgroundVideoPlayer?.seek(to: .zero) { _ in
                    backgroundVideoPlayer?.play()
                }
            }
        )
    }

    // MARK: - View

    public var body: some View {
        GeometryReader { reader in
            VStack(spacing: 0) {
                ToolbarView(viewModel: toolbarModel)
                    .padding(.top, reader.safeAreaInsets.top == 0 ? DesignSystemMetrics.verticalPadding : 0)
                    .padding(.horizontal, DesignSystemMetrics.horizontalPadding)

                Spacer()

                ContentView(headerModel: viewModel.header, buttonModel: viewModel.primaryButton)
                    .padding(.horizontal, DesignSystemMetrics.horizontalPadding)
                    .padding(.bottom, DesignSystemMetrics.verticalPadding)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .background(
                VStack {
                    Spacer()

                    VideoView(
                        videoPlayer: backgroundVideoPlayer,
                        videoStartingPosition: viewModel.backgroundVideo.startingPosition
                    )
                    // We need an explicit ID here so SwiftUI will re-draw the VideoView for different videos
                    .id(viewModel.backgroundVideo.content.name)
                    .frame(height: 640)
                    .padding(.horizontal, -100) // Let the video width extend beyond the edges a bit
                    
                    Spacer(minLength: 250) // The minLength ensures enough space on smaller devices
                }
            )
            .background(.black)
        }
    }

}

// MARK: -

private struct ContentView: View {

    let headerModel: FormHeaderModel
    let buttonModel: ButtonModel

    var body: some View {
        VStack {
            FormHeaderView(viewModel: headerModel, headlineFont: .title1, headlineTextColor: .white, sublineTextColor: .white)
            Spacer()
                .frame(height: 56)
            ButtonView(model: buttonModel)
        }
    }

}

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
