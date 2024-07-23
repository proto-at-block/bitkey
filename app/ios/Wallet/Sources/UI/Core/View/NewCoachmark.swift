
import Shared
import SwiftUI

/*
 A coachmark shown in a list item to identify a new feature for users
 */
struct NewCoachmark: View {
    let treatment: NewCoachmarkTreatment

    init(treatment: NewCoachmarkTreatment) {
        self.treatment = treatment
    }

    private var backgroundColor: Color {
        switch treatment {
        case .light:
            return Color.bitkeyPrimary.opacity(0.1)
        case .dark:
            return Color.bitkeyPrimary
        case .disabled:
            return Color.foreground30
        default:
            return Color.bitkeyPrimary
        }
    }

    private var textColor: Color {
        switch treatment {
        case .light:
            return Color.bitkeyPrimary
        case .dark:
            return Color.white
        case .disabled:
            return Color.gray
        default:
            return Color.white
        }
    }

    var body: some View {
        VStack(alignment: .leading) {
            Text("New")
                .font(FontTheme(
                    name: "Inter-SemiBold",
                    size: "12",
                    lineHeight: "18",
                    kerning: "-0.13"
                ).font)
                .foregroundColor(textColor)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .background(backgroundColor)
        .clipShape(RoundedRectangle(
            cornerSize: CGSize(width: 1000, height: 1000),
            style: .continuous
        ))
    }
}

struct NewCoachmark_Preview: PreviewProvider {
    static var previews: some View {
        NewCoachmark(treatment: .light).previewDisplayName("new coachmark - light")
        NewCoachmark(treatment: .dark).previewDisplayName("new coachmark - dark")
        NewCoachmark(treatment: .disabled).previewDisplayName("new coachmark - disabled")
    }
}
