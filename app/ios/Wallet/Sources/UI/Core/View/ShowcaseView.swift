import Shared
import SwiftUI

struct ShowcaseView: View {

    let model: FormMainContentModel.Showcase
    
    @SwiftUI.State
    private var videoViewModel: VideoViewModel? = nil

    public var body: some View {
        VStack(alignment: .center, spacing: .zero) {
            switch model.content {
            case _ as FormMainContentModel.ShowcaseContentVideoContent:
                if let vm = videoViewModel {
                    VideoView(viewModel: vm, backgroundColor: .white)
                        .aspectRatio(contentMode: .fill)
                        .padding(.horizontal, 24)
                }
                
            case let content as FormMainContentModel.ShowcaseContentIconContent:
                Image(uiImage: content.icon.uiImage)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .padding(.horizontal, 24)
            default:
                fatalError("Unhandled FormMainContentModelShowcase.Content case: \(String(describing: model.content))")
            }

            ModeledText(model: TextModel.standard(
                model.title,
                font: .body1Medium,
                textAlignment: .center
            ))
            .frame(maxWidth: .infinity)

            Spacer().frame(height: 6)

            ModeledText(model: TextModel.standard(
                model.body.string,
                font: .body2Regular,
                textAlignment: .center
            ))
            .frame(maxWidth: .infinity)
        }.onAppear {
           update(newModel: model)
        }
    }
    
    private func update(newModel: FormMainContentModel.Showcase) {
        if let video = (newModel.content as? FormMainContentModel.ShowcaseContentVideoContent)?.video {
            if self.videoViewModel?.videoUrl != video.videoUrl {
                self.videoViewModel = .init(
                    videoUrl: video.videoUrl,
                    videoGravity: .resizeAspectFill,
                    videoShouldLoop: video.looping,
                    videoShouldPauseOnResignActive: false
                )
            }
        } else {
            self.videoViewModel = nil
        }
    }
}


extension FormMainContentModel.ShowcaseContentVideoContentVideo {
    
    var videoUrl: URL {
        switch self {
        case .bitkeyReset:
            return .bitkeyResetVideoUrl
        default:
            fatalError("Unhandled FormMainContentModelShowcase.Content.VideoContent.Video case: \(self)")
        }
    }
}
