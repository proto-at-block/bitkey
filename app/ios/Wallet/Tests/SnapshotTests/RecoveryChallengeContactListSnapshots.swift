import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class RecoveryChallengeContactListSnapshots: XCTestCase {
    
    func test_recovery_challenge_contact_list_verified_contact_found() {
        let view = FormView(
            viewModel: RecoveryChallengeContactListBodyModelKt.RecoveryChallengeContactListBodyModel(
                onExit: {},
                trustedContacts: [
                    .init(
                        recoveryRelationshipId: "1",
                        trustedContactAlias: "alias",
                        keyCertificate: TrustedContactKeyCertificate(
                            delegatedDecryptionKey: .init(key: AppKeyCompanion().fromPublicKey(value: "")),
                            hwAuthPublicKey: .init(pubKey: .init(value: "")),
                            appGlobalAuthPublicKey: .init(pubKey: .init(value: "")),
                            appAuthGlobalKeyHwSignature: "",
                            trustedContactIdentityKeyAppSignature: ""
                        ),
                        authenticationState: .awaitingVerify
                    )
                ],
                onVerifyClick: {_ in },
                verifiedBy: ["1"],
                onContinue: {})
        )

        assertBitkeySnapshots(view: view)
    }
    
    func test_recovery_challenge_contact_list_verified_empty() {
        let view = FormView(
            viewModel: RecoveryChallengeContactListBodyModelKt.RecoveryChallengeContactListBodyModel(
                onExit: {},
                trustedContacts: [
                    .init(
                        recoveryRelationshipId: "1",
                        trustedContactAlias: "alias",
                        keyCertificate: TrustedContactKeyCertificate(
                            delegatedDecryptionKey: .init(key: AppKeyCompanion().fromPublicKey(value: "")),
                            hwAuthPublicKey: .init(pubKey: .init(value: "")),
                            appGlobalAuthPublicKey: .init(pubKey: .init(value: "")),
                            appAuthGlobalKeyHwSignature: "",
                            trustedContactIdentityKeyAppSignature: ""
                        ),
                        authenticationState: .awaitingVerify
                    )
                ],
                onVerifyClick: {_ in },
                verifiedBy: [],
                onContinue: {})
        )

        assertBitkeySnapshots(view: view)
    }
    
    func test_recovery_challenge_contact_list_mix_verified_awaiting_verify() {
        let view = FormView(
            viewModel: RecoveryChallengeContactListBodyModelKt.RecoveryChallengeContactListBodyModel(
                onExit: {},
                trustedContacts: [
                    .init(
                        recoveryRelationshipId: "1",
                        trustedContactAlias: "alias1",
                        keyCertificate: TrustedContactKeyCertificate(
                            delegatedDecryptionKey: .init(key: AppKeyCompanion().fromPublicKey(value: "")),
                            hwAuthPublicKey: .init(pubKey: .init(value: "")),
                            appGlobalAuthPublicKey: .init(pubKey: .init(value: "")),
                            appAuthGlobalKeyHwSignature: "",
                            trustedContactIdentityKeyAppSignature: ""
                        ),
                        authenticationState: .awaitingVerify
                    ),
                    .init(
                        recoveryRelationshipId: "2",
                        trustedContactAlias: "alias2",
                        keyCertificate: TrustedContactKeyCertificate(
                            delegatedDecryptionKey: .init(key: AppKeyCompanion().fromPublicKey(value: "")),
                            hwAuthPublicKey: .init(pubKey: .init(value: "")),
                            appGlobalAuthPublicKey: .init(pubKey: .init(value: "")),
                            appAuthGlobalKeyHwSignature: "",
                            trustedContactIdentityKeyAppSignature: ""
                        ),
                        authenticationState: .awaitingVerify
                    )
                ],
                onVerifyClick: {_ in },
                verifiedBy: ["2"],
                onContinue: {})
        )

        assertBitkeySnapshots(view: view)
    }
    
}
