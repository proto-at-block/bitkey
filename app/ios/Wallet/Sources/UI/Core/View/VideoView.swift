import AVKit
import Shared
import SwiftUI

struct VideoView: View {

    // MARK: - Private Properties

    @ObservedObject
    private var viewModel: VideoViewModel

    private var backgroundColor: Color?

    // MARK: - Life Cycle

    init(viewModel: VideoViewModel, backgroundColor: Color? = nil) {
        self.viewModel = viewModel
        self.backgroundColor = backgroundColor
    }

    // MARK: - View

    var body: some View {
        VideoViewUIViewRepresentable(viewModel: viewModel, backgroundColor: backgroundColor)
    }

}

// MARK: -

struct VideoViewUIViewRepresentable: UIViewControllerRepresentable {

    @ObservedObject
    var viewModel: VideoViewModel

    let backgroundColor: Color?

    func makeUIViewController(context _: Context) -> AVPlayerViewController {
        let viewController = AVPlayerViewController()
        // Disable showing controls on the video for the user to control
        viewController.showsPlaybackControls = false

        // Disable text in the video being highlighted / able to interact with
        if #available(iOS 16.0, *) {
            viewController.allowsVideoFrameAnalysis = false
        }

        viewController.player = viewModel.videoPlayer

        if let color = backgroundColor {
            viewController.view.backgroundColor = UIColor(color)
        }

        return viewController
    }

    func updateUIViewController(_ uiViewController: AVPlayerViewController, context _: Context) {
        uiViewController.view.isUserInteractionEnabled = false

        switch viewModel.videoConfigurationUpdate {
        case let .new(player, gravity):
            uiViewController.player = player
            uiViewController.videoGravity = gravity
            // New video, seek to starting position and play
            Task(priority: .userInitiated) {
                await viewModel.goToStartingPositionAndPlay()
            }

        case let .seamlessNew(gravity):
            uiViewController.videoGravity = gravity
            // New video, seek to starting position and play
            Task(priority: .userInitiated) {
                await viewModel.goToStartingPositionAndPlay()
            }

        case let .update(gravity):
            uiViewController.videoGravity = gravity
        }
    }

}
