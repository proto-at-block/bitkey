import Shared

extension AppVariant {
    
    static func current() -> AppVariant {
        #if DEVELOPMENT
        return AppVariant.development
        #elseif INTERNAL
            return AppVariant.team
        #elseif BETA
            return AppVariant.beta
        #elseif RELEASE
            return AppVariant.customer
        #else
            return AppVariant.development
        #endif
    }
}
