import AVKit
import Shared
import SwiftUI

// MARK: -

class VideoViewModel: ObservableObject {

    // MARK: - Public Types

    /// Represents an update that we want to publish to the `VideoView`.
    /// We encapsulate the update in one property instead of multiple `Published` properties to avoid multiple updates to the `VideoView`.
    enum VideoConfigurationUpdate {
        /// We want to play a new video with a new `AVPlayer`
        case new(player: AVPlayer, gravity: AVLayerVideoGravity)

        /// We want to play a new video, but the `AVPlayer` should not be updated. This allows more seamless transition.
        case seamlessNew(gravity: AVLayerVideoGravity)

        /// We are just updating the current video.
        case update(gravity: AVLayerVideoGravity)
    }

    // MARK: - Public Properties

    @Published
    private(set) var videoConfigurationUpdate: VideoConfigurationUpdate
    private(set) var videoPlayer: AVPlayer

    // MARK: - Private Properties

    private var videoStartingPosition: VideoStartingPosition
    private var videoUrl: URL

    // MARK: - Life Cycle

    /**
     * - parameter videoShouldPauseOnResignActive: Whether the video pauses when resigning active (i.e. any system alert, like an NFC modal, pops on top, or app is backgrounded
     * to the app switcher view). If false, the video will only pause on the app fully being backgrounded / foregrounded, but not on resigning / resuming active.
     */
    init(
        videoUrl: URL,
        videoGravity: AVLayerVideoGravity = .resizeAspect,
        videoShouldLoop: Bool = false,
        videoShouldPauseOnResignActive: Bool = false,
        videoStartingPosition: VideoStartingPosition = .start
    ) {
        self.videoPlayer = AVPlayer(url: videoUrl)
        self.videoStartingPosition = videoStartingPosition
        self.videoUrl = videoUrl

        videoPlayer.allowsExternalPlayback = false
        videoPlayer.isMuted = true

        self.videoConfigurationUpdate = .new(player: videoPlayer, gravity: videoGravity)

        setUpVideoObservers(
            videoShouldLoop: videoShouldLoop,
            videoShouldPauseOnResignActive: videoShouldPauseOnResignActive
        )
    }

    deinit {
        tearDownVideoLoopObserver()
        tearDownAppLifecycleObservers()
    }

    // MARK: - Public Methods

    /**
     * Updates the view model properties.
     *
     * - parameter videoShouldPauseOnResignActive: Whether the video pauses when resigning active (i.e. any system alert, like an NFC modal, pops on top, or app is backgrounded
     * to the app switcher view). If false, the video will only pause on the app fully being backgrounded / foregrounded, but not on resigning / resuming active.
     */
    func update(
        videoUrl: URL,
        videoGravity: AVLayerVideoGravity = .resizeAspect,
        videoShouldLoop: Bool = false,
        videoShouldPauseOnResignActive: Bool = false,
        videoStartingPosition: VideoStartingPosition = .start
    ) {
        let videoIsNew = videoUrl != self.videoUrl
        self.videoUrl = videoUrl
        self.videoStartingPosition = videoStartingPosition

        if videoIsNew {
            // If the video is new but starting from the beginning, don't update the AVPlayer for a more seamless experience.
            // When we want the video to be paused at the end, though, there is more potential for interference if the AVPlayer doesn't
            // fully change, so we want to fully replace it in that case
            if videoStartingPosition == .start {
                self.videoPlayer.replaceCurrentItem(with: .init(url: videoUrl))
                self.videoConfigurationUpdate = .seamlessNew(gravity: videoGravity)
            } else {
                self.videoPlayer = AVPlayer(url: videoUrl)
                self.videoConfigurationUpdate = .new(player: videoPlayer, gravity: videoGravity)
            }
        } else {
            self.videoConfigurationUpdate = .update(gravity: videoGravity)
        }

        setUpVideoObservers(
            videoShouldLoop: videoShouldLoop,
            videoShouldPauseOnResignActive: videoShouldPauseOnResignActive
        )
    }

    @objc
    func replayVideo() {
        videoPlayer.seek(to: .zero) { _ in
            self.videoPlayer.play()
        }
    }

    @MainActor
    func goToStartingPositionAndPlay() async {
        switch videoStartingPosition {
        case .start:
            videoPlayer.play()

        case .end:
            do {
                if let videoDuration = try await videoPlayer.currentItem?.asset.load(.duration) {
                    await videoPlayer.seek(to: videoDuration, toleranceBefore: .zero, toleranceAfter: .zero)
                }
            } catch {
                log(.warn) { "Failed to load video duration" }
            }

        default:
            break
        }
    }

    // MARK: - Private Methods

    private func setUpVideoObservers(
        videoShouldLoop: Bool,
        videoShouldPauseOnResignActive: Bool = true
    ) {
        if videoShouldLoop {
            setUpVideoLoopObserver()
        } else {
            tearDownVideoLoopObserver()
        }

        setUpAppLifecycleObservers(videoShouldPauseOnResignActive: videoShouldPauseOnResignActive)
    }

    // MARK: - Private Methods - Video Loop

    private func setUpVideoLoopObserver() {
        addObserver(selector: #selector(replayVideo), name: .AVPlayerItemDidPlayToEndTime)
    }

    private func tearDownVideoLoopObserver() {
        removeObserver(name: .AVPlayerItemDidPlayToEndTime)
    }

    // MARK: - Private Methods - App Lifecycle

    @objc
    private func pauseVideo() {
        videoPlayer.pause()
    }

    @objc
    private func playVideo() {
        videoPlayer.play()
    }

    private func setUpAppLifecycleObservers(videoShouldPauseOnResignActive: Bool) {
        if videoShouldPauseOnResignActive {
            addObserver(selector: #selector(pauseVideo), name: UIApplication.willResignActiveNotification)
            addObserver(selector: #selector(playVideo), name: UIApplication.didBecomeActiveNotification)
        } else {
            addObserver(selector: #selector(pauseVideo), name: UIApplication.didEnterBackgroundNotification)
            addObserver(selector: #selector(playVideo), name: UIApplication.willEnterForegroundNotification)
        }
    }

    private func tearDownAppLifecycleObservers() {
        removeObserver(name: UIApplication.willResignActiveNotification)
        removeObserver(name: UIApplication.didBecomeActiveNotification)
        removeObserver(name: UIApplication.willEnterForegroundNotification)
        removeObserver(name: UIApplication.didEnterBackgroundNotification)
    }

    // MARK: - Private Methods - Helpers

    private func addObserver(selector: Selector, name: NSNotification.Name) {
        NotificationCenter.default.addObserver(
            self,
            selector: selector,
            name: name,
            object: nil
        )
    }

    private func removeObserver(name: NSNotification.Name) {
        NotificationCenter.default.removeObserver(
            self,
            name: name,
            object: self
        )
    }

}
