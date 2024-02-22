import AVKit
import Shared
import SwiftUI

struct VideoView: View {

    // MARK: - Private Properties

    private let videoPlayerViewController = AVPlayerViewController()
    private let videoPlayer: AVPlayer
    private let videoStartingPosition: VideoStartingPosition

    // MARK: - Life Cycle

    init(
        videoPlayer: AVPlayer,
        videoGravity: AVLayerVideoGravity = .resizeAspect,
        videoStartingPosition: VideoStartingPosition = .start
    ) {
        self.videoPlayer = videoPlayer
        self.videoStartingPosition = videoStartingPosition

        videoPlayerViewController.player = videoPlayer
        videoPlayerViewController.videoGravity = videoGravity

        // Disable showing controls on the video for the user to control
        videoPlayerViewController.showsPlaybackControls = false
        
        // Disable text in the video being highlighted / able to interact with
        if #available(iOS 16.0, *) {
            videoPlayerViewController.allowsVideoFrameAnalysis = false
        }

        videoPlayer.allowsExternalPlayback = false
        videoPlayer.isMuted = true
    }

    // MARK: - View

    var body: some View {
        VideoViewUIViewRepresentable(videoPlayerViewController: videoPlayerViewController)
            .onAppear { setUpAppLifecycleObservers() }
            .onDisappear() { tearDownAppLifecycleObservers() }
            .task {
                switch videoStartingPosition {
                case .start:
                    break
                case .end:
                    do {
                        if let videoDuration = try await videoPlayer.currentItem?.asset.load(.duration) {
                            await videoPlayer.seek(to: videoDuration, toleranceBefore: .zero, toleranceAfter: .positiveInfinity)
                        }
                    } catch {
                        log(.warn) { "Failed to load video duration" }
                    }

                default:
                    break
                }
                videoPlayer.play()
            }
    }

    // MARK: - Public Methods

    func restartVideo() async {
        await videoPlayer.seek(to: .zero)
        await videoPlayer.play()
    }

    // MARK: - Private Methods

    /**
     * Sets up observers for app lifecycle events in order to support pausing and resuming the video
     * when the app is backgrounded and foregrounded
     */
    private func setUpAppLifecycleObservers() {
        NotificationCenter.default.addObserver(
            forName: UIApplication.willResignActiveNotification,
            object: nil,
            queue: nil
        ) { [weak videoPlayer] _ in
            videoPlayer?.pause()
        }
        NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: nil
        ) { [weak videoPlayer] _ in
            videoPlayer?.play()
        }
    }

    /**
     * Cleans up observers created in `setUpAppLifecycleObservers`
     */
    private func tearDownAppLifecycleObservers() {
        NotificationCenter.default.removeObserver(
            self,
            name: UIApplication.willResignActiveNotification,
            object: self
        )
        NotificationCenter.default.removeObserver(
            self,
            name: UIApplication.didBecomeActiveNotification,
            object: self
        )
    }
}

// MARK: -

struct VideoViewUIViewRepresentable: UIViewControllerRepresentable {

    let videoPlayerViewController: AVPlayerViewController

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        return videoPlayerViewController
    }

    func updateUIViewController(_ uiViewController: AVPlayerViewController, context: Context) {
        uiViewController.view.isUserInteractionEnabled = false
    }

}
