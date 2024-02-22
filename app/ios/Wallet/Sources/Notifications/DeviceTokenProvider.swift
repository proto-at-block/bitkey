public protocol DeviceTokenProvider {
    var deviceToken: String? { get }
    
    func setDeviceToken(deviceToken: String)
}
