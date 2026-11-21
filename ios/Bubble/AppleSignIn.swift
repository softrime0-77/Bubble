import AuthenticationServices
import CryptoKit
import Foundation
import UIKit

/// Sign in with Apple 도우미.
/// - 로비 버튼은 SwiftUI SignInWithAppleButton을 쓰고, 여기서는 nonce(재사용 방지 값)만 만들어 줌
/// - 계정 삭제 때는 Apple 규정대로 토큰을 취소(revoke)해야 해서, 다시 한 번 Apple 확인을 받아 authorizationCode를 얻음
enum AppleNonce {
    /// 무작위 nonce 원문 (Firebase에 그대로 보냄)
    static func make(_ length: Int = 32) -> String {
        let chars = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var bytes = [UInt8](repeating: 0, count: length)
        if SecRandomCopyBytes(kSecRandomDefault, length, &bytes) != errSecSuccess {
            return UUID().uuidString + UUID().uuidString
        }
        return String(bytes.map { chars[Int($0) % chars.count] })
    }

    /// Apple 요청에는 SHA-256 해시를 넣음
    static func sha256(_ s: String) -> String {
        SHA256.hash(data: Data(s.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

/// 코드로 Apple 확인 창을 띄우고 결과를 기다림 (계정 삭제 전 재확인용)
final class AppleAuthorizer: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var continuation: CheckedContinuation<ASAuthorizationAppleIDCredential, Error>?
    private var controller: ASAuthorizationController?

    @MainActor
    func authorize(nonce: String) async throws -> ASAuthorizationAppleIDCredential {
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = []
        request.nonce = AppleNonce.sha256(nonce)
        let c = ASAuthorizationController(authorizationRequests: [request])
        c.delegate = self
        c.presentationContextProvider = self
        controller = c
        return try await withCheckedThrowingContinuation { cont in
            continuation = cont
            c.performRequests()
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        if let cred = authorization.credential as? ASAuthorizationAppleIDCredential {
            continuation?.resume(returning: cred)
        } else {
            continuation?.resume(throwing: ServerError(message: "no Apple credential"))
        }
        continuation = nil
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        continuation?.resume(throwing: error)
        continuation = nil
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first ?? ASPresentationAnchor()
    }
}
