//
//  OAuthService.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import Foundation
import AuthenticationServices
import SwiftUI
import UIKit

@MainActor
class OAuthService: NSObject {
    static let shared = OAuthService()
    
    private var currentContinuation: CheckedContinuation<ASAuthorization, Error>?
    
    private override init() {
        super.init()
    }
    
    // MARK: - Apple Sign In
    
    func signInWithApple() async throws -> ASAuthorization {
        return try await withCheckedThrowingContinuation { continuation in
            currentContinuation = continuation
            
            let request = ASAuthorizationAppleIDProvider().createRequest()
            request.requestedScopes = [.fullName, .email]
            
            let authorizationController = ASAuthorizationController(authorizationRequests: [request])
            authorizationController.delegate = self
            authorizationController.presentationContextProvider = self
            authorizationController.performRequests()
        }
    }
    
    // MARK: - Google Sign In
    
    func signInWithGoogle() async throws -> GoogleSignInResult {
        // Google Sign In будет реализован через GoogleSignIn SDK
        // Пока возвращаем ошибку, что нужно добавить SDK
        throw OAuthError.googleSignInNotConfigured
    }
}

// MARK: - ASAuthorizationControllerDelegate

extension OAuthService: ASAuthorizationControllerDelegate {
    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        currentContinuation?.resume(returning: authorization)
        currentContinuation = nil
    }
    
    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        currentContinuation?.resume(throwing: error)
        currentContinuation = nil
    }
}

// MARK: - ASAuthorizationControllerPresentationContextProviding

extension OAuthService: ASAuthorizationControllerPresentationContextProviding {
    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let window = windowScene.windows.first else {
            fatalError("No window found")
        }
        return window
    }
}

// MARK: - Models

struct GoogleSignInResult {
    let idToken: String
    let accessToken: String?
    let email: String?
    let fullName: String?
    let profileImageURL: String?
}

enum OAuthError: LocalizedError {
    case googleSignInNotConfigured
    case invalidCredentials
    case cancelled
    case unknown(Error)
    
    var errorDescription: String? {
        switch self {
        case .googleSignInNotConfigured:
            return "Google Sign In is not configured. Please add GoogleSignIn SDK."
        case .invalidCredentials:
            return "Invalid OAuth credentials"
        case .cancelled:
            return "Sign in was cancelled"
        case .unknown(let error):
            return error.localizedDescription
        }
    }
}
