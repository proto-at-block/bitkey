import SnapshotTesting
import UIKit

public extension ViewImageConfig {

    static func iPhone14(_ orientation: Orientation) -> ViewImageConfig {
        let safeArea: UIEdgeInsets
        let size: CGSize
        switch orientation {
        case .landscape:
            safeArea = UIEdgeInsets(top: 0, left: 47, bottom: 21, right: 47)
            size = CGSize(width: 844, height: 390)
        case .portrait:
            safeArea = UIEdgeInsets(top: 47, left: 0, bottom: 34, right: 0)
            size = CGSize(width: 390, height: 844)
        }
        return ViewImageConfig(safeArea: safeArea, size: size, traits: .iPhone14(orientation))
    }

    static func iPhone14Plus(_ orientation: Orientation) -> ViewImageConfig {
        let safeArea: UIEdgeInsets
        let size: CGSize
        switch orientation {
        case .landscape:
            safeArea = UIEdgeInsets(top: 0, left: 47, bottom: 21, right: 47)
            size = CGSize(width: 926, height: 428)
        case .portrait:
            safeArea = UIEdgeInsets(top: 47, left: 0, bottom: 34, right: 0)
            size = CGSize(width: 428, height: 926)
        }
        return ViewImageConfig(safeArea: safeArea, size: size, traits: .iPhone14Plus(orientation))
    }

    static func iPhone14Pro(_ orientation: Orientation) -> ViewImageConfig {
        let safeArea: UIEdgeInsets
        let size: CGSize
        switch orientation {
        case .landscape:
            safeArea = UIEdgeInsets(top: 0, left: 59, bottom: 21, right: 59)
            size = CGSize(width: 852, height: 393)
        case .portrait:
            safeArea = UIEdgeInsets(top: 59, left: 0, bottom: 34, right: 0)
            size = CGSize(width: 393, height: 852)
        }
        return ViewImageConfig(safeArea: safeArea, size: size, traits: .iPhone14Pro(orientation))
    }

    static func iPhone14ProMax(_ orientation: Orientation) -> ViewImageConfig {
        let safeArea: UIEdgeInsets
        let size: CGSize
        switch orientation {
        case .landscape:
            safeArea = UIEdgeInsets(top: 0, left: 59, bottom: 21, right: 59)
            size = CGSize(width: 932, height: 430)
        case .portrait:
            safeArea = UIEdgeInsets(top: 59, left: 0, bottom: 34, right: 0)
            size = CGSize(width: 430, height: 932)
        }
        return ViewImageConfig(safeArea: safeArea, size: size, traits: .iPhone14ProMax(orientation))
    }

    static func iPhone15(_ orientation: Orientation) -> ViewImageConfig {
        let safeArea: UIEdgeInsets
        let size: CGSize
        switch orientation {
        case .landscape:
            safeArea = UIEdgeInsets(top: 0, left: 59, bottom: 21, right: 59)
            size = CGSize(width: 852, height: 393)
        case .portrait:
            safeArea = UIEdgeInsets(top: 59, left: 0, bottom: 34, right: 0)
            size = CGSize(width: 393, height: 852)
        }
        return ViewImageConfig(safeArea: safeArea, size: size, traits: .iPhone15(orientation))
    }

    static func iPhone15Plus(_ orientation: Orientation) -> ViewImageConfig {
        let safeArea: UIEdgeInsets
        let size: CGSize
        switch orientation {
        case .landscape:
            safeArea = UIEdgeInsets(top: 0, left: 59, bottom: 21, right: 59)
            size = CGSize(width: 932, height: 430)
        case .portrait:
            safeArea = UIEdgeInsets(top: 59, left: 0, bottom: 34, right: 0)
            size = CGSize(width: 430, height: 932)
        }
        return ViewImageConfig(safeArea: safeArea, size: size, traits: .iPhone15Plus(orientation))
    }

    static func iPhone15Pro(_ orientation: Orientation) -> ViewImageConfig {
        let safeArea: UIEdgeInsets
        let size: CGSize
        switch orientation {
        case .landscape:
            safeArea = UIEdgeInsets(top: 0, left: 59, bottom: 21, right: 59)
            size = CGSize(width: 852, height: 393)
        case .portrait:
            safeArea = UIEdgeInsets(top: 59, left: 0, bottom: 34, right: 0)
            size = CGSize(width: 393, height: 852)
        }
        return ViewImageConfig(safeArea: safeArea, size: size, traits: .iPhone15Pro(orientation))
    }

    static func iPhone15ProMax(_ orientation: Orientation) -> ViewImageConfig {
        let safeArea: UIEdgeInsets
        let size: CGSize
        switch orientation {
        case .landscape:
            safeArea = UIEdgeInsets(top: 0, left: 59, bottom: 21, right: 59)
            size = CGSize(width: 932, height: 430)
        case .portrait:
            safeArea = UIEdgeInsets(top: 59, left: 0, bottom: 34, right: 0)
            size = CGSize(width: 430, height: 932)
        }
        return ViewImageConfig(safeArea: safeArea, size: size, traits: .iPhone15ProMax(orientation))
    }

    var description: String? {
        switch self {
        case .iPhone8: "iPhone8"
        case .iPhone8Plus: "iPhone8Plus"
        case .iPhoneSe: "iPhoneSe"
        case .iPhoneX: "iPhoneX"
        case .iPhoneXr: "iPhoneXr"
        case .iPhoneXsMax: "iPhoneXsMax"
        case .iPhone12: "iPhone12"
        case .iPhone12Pro: "iPhone12Pro"
        case .iPhone12ProMax: "iPhone12ProMax"
        case .iPhone13: "iPhone13"
        case .iPhone13Mini: "iPhone13Mini"
        case .iPhone13Pro: "iPhone13Pro"
        case .iPhone13ProMax: "iPhone13ProMax"
        case .iPhone14(.portrait): "iPhone14"
        case .iPhone14Plus(.portrait): "iPhone14Plus"
        case .iPhone14Pro(.portrait): "iPhone14Pro"
        case .iPhone14ProMax(.portrait): "iPhone14ProMax"
        case .iPhone15(.portrait): "iPhone15"
        case .iPhone15Plus(.portrait): "iPhone15Plus"
        case .iPhone15Pro(.portrait): "iPhone15Pro"
        case .iPhone15ProMax(.portrait): "iPhone15ProMax"
        default:
            nil
        }
    }
}

extension ViewImageConfig: Equatable {
    public static func == (lhs: ViewImageConfig, rhs: ViewImageConfig) -> Bool {
        return lhs.safeArea == rhs.safeArea &&
            lhs.size == rhs.size &&
            lhs.traits == rhs.traits
    }
}
