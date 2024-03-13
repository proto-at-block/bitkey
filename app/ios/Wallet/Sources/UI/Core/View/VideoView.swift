import AVKit
import Shared
import SwiftUI

struct VideoView: View {

    // MARK: - Private Properties

    @ObservedObject
    private var viewModel: VideoViewModel

    // MARK: - Life Cycle

    init(viewModel: VideoViewModel) {
        self.viewModel = viewModel
    }

    // MARK: - View

    var body: some View {
        VideoViewUIViewRepresentable(viewModel: viewModel)
    }

}

// MARK: -

struct VideoViewUIViewRepresentable: UIViewControllerRepresentable {

    @ObservedObject
    var viewModel: VideoViewModel

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let viewController = AVPlayerViewController()
        // Disable showing controls on the video for the user to control
        viewController.showsPlaybackControls = false

        // Disable text in the video being highlighted / able to interact with
        if #available(iOS 16.0, *) {
            viewController.allowsVideoFrameAnalysis = false
        }

        viewController.player = viewModel.videoPlayer

        return viewController
    }

    func updateUIViewController(_ uiViewController: AVPlayerViewController, context: Context) {
        uiViewController.view.isUserInteractionEnabled = false

        switch viewModel.videoConfigurationUpdate {
        case .new(let player, let gravity):
            uiViewController.player = player
            uiViewController.videoGravity = gravity
            // New video, seek to starting position and play
            Task(priority: .userInitiated) {
                await viewModel.goToStartingPositionAndPlay()
            }

        case .seamlessNew(let gravity):
            uiViewController.videoGravity = gravity
            // New video, seek to starting position and play
            Task(priority: .userInitiated) {
                await viewModel.goToStartingPositionAndPlay()
            }

        case .update(let gravity):
            uiViewController.videoGravity = gravity
        }
    }

}

