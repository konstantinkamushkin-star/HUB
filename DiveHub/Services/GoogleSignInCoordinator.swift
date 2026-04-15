//
//  GoogleSignInCoordinator.swift
//  DiveHub
//

import Foundation
import UIKit
import GoogleSignIn

enum GoogleSignInCoordinator {
    /// Замените значения в `GoogleOAuth.plist` на iOS Client ID из Google Cloud Console.
    private static var configuredClientID: String? {
        let raw = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String
        return raw?.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static var isClientIDConfigured: Bool {
        guard let id = configuredClientID, !id.isEmpty else { return false }
        return !id.contains("YOUR_IOS_CLIENT_ID")
    }

    @MainActor
    static func signInAndAuthenticate(
        authService: AuthenticationService,
        personalDataConsent: Bool,
        personalDataConsentText: String
    ) async throws {
        guard isClientIDConfigured, let clientID = configuredClientID else {
            throw AuthError.serverError
        }

        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)

        guard let presenter = UIWindowScene.presentingViewControllerForSignIn() else {
            throw AuthError.networkError
        }

        let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)

        guard let idToken = result.user.idToken?.tokenString else {
            throw AuthError.invalidCredentials
        }

        let accessToken = result.user.accessToken.tokenString
        let profile = result.user.profile
        let email = profile?.email
        let fullName = profile?.name

        try await authService.signInWithGoogle(
            idToken: idToken,
            accessToken: accessToken,
            email: email,
            fullName: fullName,
            personalDataConsent: personalDataConsent,
            personalDataConsentText: personalDataConsentText
        )
    }

    static func wasUserCanceled(_ error: Error) -> Bool {
        let ns = error as NSError
        // Совпадает с GIDSignInErrorDomain в GoogleSignIn SDK.
        return ns.domain == "com.google.GIDSignIn"
            && ns.code == GIDSignInError.Code.canceled.rawValue
    }
}

private extension UIWindowScene {
    @MainActor
    static func presentingViewControllerForSignIn() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
            ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first

        guard let scene else { return nil }

        let root = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController
            ?? scene.windows.first?.rootViewController
        guard let root else { return nil }
        return topMost(from: root)
    }

    @MainActor
    private static func topMost(from root: UIViewController) -> UIViewController {
        if let presented = root.presentedViewController {
            return topMost(from: presented)
        }
        if let nav = root as? UINavigationController, let visible = nav.visibleViewController {
            return topMost(from: visible)
        }
        if let tab = root as? UITabBarController, let selected = tab.selectedViewController {
            return topMost(from: selected)
        }
        return root
    }
}
