import Shared

// Convenience initializer that mirrors default parameter values in Kotlin.
extension FormMainContentModel.DataListData {
    convenience init(
        withTitle title: String,
        secondaryTitle: String? = nil,
        titleTextType: FormMainContentModel.DataListDataTitleTextType = .regular,
        titleIcon: IconModel? = nil,
        onTitle: (() -> Void)? = nil,
        sideText: String,
        sideTextType: FormMainContentModel.DataListDataSideTextType = .medium,
        sideTextTreatment: FormMainContentModel.DataListDataSideTextTreatment = .primary,
        secondarySideText: String? = nil,
        secondarySideTextType: FormMainContentModel.DataListDataSideTextType = .regular,
        secondarySideTextTreatment: FormMainContentModel.DataListDataSideTextTreatment = .secondary,
        showBottomDivider: Bool = false,
        explainer: FormMainContentModel.DataListDataExplainer? = nil,
        onClick: (() -> Void)? = nil
    ) {
        self.init(
            title: title,
            titleIcon: titleIcon,
            onTitle: onTitle,
            titleTextType: titleTextType,
            secondaryTitle: secondaryTitle,
            sideText: sideText,
            sideTextType: sideTextType,
            sideTextTreatment: sideTextTreatment,
            secondarySideText: secondarySideText,
            secondarySideTextType: secondarySideTextType,
            secondarySideTextTreatment: secondarySideTextTreatment,
            showBottomDivider: showBottomDivider,
            explainer: explainer,
            onClick: onClick
        )
    }
}
