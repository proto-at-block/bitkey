//
//  FormMainContentModelDataListExtensions.swift
//  Wallet
//
//  Created by Jurvis Tan on 1/30/24.
//

import Shared

// Convenience initializer that mirrors default parameter values in Kotlin.
extension FormMainContentModelDataList.Data {
    convenience init(
        withTitle title: String,
        titleIcon: IconModel? = nil,
        onTitle: (() -> Void)? = nil,
        sideText: String,
        sideTextType: FormMainContentModelDataList.DataSideTextType = .medium,
        sideTextTreatment: FormMainContentModelDataList.DataSideTextTreatment = .primary,
        secondarySideText: String? = nil,
        secondarySideTextType: FormMainContentModelDataList.DataSideTextType = .regular,
        secondarySideTextTreatment: FormMainContentModelDataList.DataSideTextTreatment = .secondary,
        showBottomDivider: Bool = false,
        explainer: FormMainContentModelDataList.DataExplainer? = nil,
        onClick: (() -> Void)? = nil
    ) {
        self.init(
            title: title,
            titleIcon: titleIcon,
            onTitle: onTitle,
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
