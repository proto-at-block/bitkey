import Shared

extension TouchpointPlatform {

    static func from(appVariant: AppVariant) -> TouchpointPlatform {
        switch appVariant {
        case AppVariant.customer: return .apnscustomer
        case AppVariant.development: return .apnsteam
        case AppVariant.team: return .apnsteam
        case AppVariant.alpha: return .apnsteamalpha
        default:
            fatalError("Unhandled app variant -> touchpoint mapping")
        }
    }

}
