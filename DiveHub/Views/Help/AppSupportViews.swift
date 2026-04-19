//
//  AppSupportViews.swift
//  DiveHub — in-app support chat and ticket forms.
//

import SwiftUI
import UIKit

/// Opens a new `APP_SUPPORT_TOPIC` thread and navigates to the chat.
struct NewSupportTopicView: View {
    @StateObject private var localizationService = LocalizationService.shared
    @State private var topicTitle = ""
    @State private var conversation: ChatConversation?
    @State private var isLoading = false
    @State private var errorMessage: String?

    var body: some View {
        Form {
            Section {
                TextField(
                    localizationService.localizedString("supportTopicPlaceholder", table: "help"),
                    text: $topicTitle,
                    axis: .vertical
                )
                .lineLimit(1...3)
            } header: {
                Text(localizationService.localizedString("supportTopicSection", table: "help"))
            }

            if let errorMessage {
                Section {
                    Text(errorMessage).foregroundColor(.red)
                }
            }

            Section {
                Button(action: openChat) {
                    HStack {
                        if isLoading { ProgressView() }
                        Text(localizationService.localizedString("openSupportChat", table: "help"))
                    }
                }
                .disabled(isLoading)
            }
        }
        .navigationTitle(localizationService.localizedString("liveChat", table: "help"))
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(item: $conversation) { conv in
            ChatDetailView(conversation: conv)
        }
    }

    private func openChat() {
        Task {
            isLoading = true
            errorMessage = nil
            defer { isLoading = false }
            guard AuthenticationService.shared.isAuthenticated else {
                errorMessage = localizationService.localizedString("pleaseSignIn", table: "errors")
                return
            }
            do {
                let t = topicTitle.trimmingCharacters(in: .whitespacesAndNewlines)
                let conv = try await NetworkService.shared.openAppSupportTopic(
                    title: t.isEmpty ? nil : t,
                    topicId: nil
                )
                conversation = conv
            } catch {
                errorMessage = NetworkError.userFacingMessage(error)
            }
        }
    }
}

/// Feedback or bug report ticket (`POST /api/support/tickets`).
struct SupportTicketFormView: View {
    enum Kind {
        case feedback
        case bug
        var category: String {
            switch self {
            case .feedback: return "feedback"
            case .bug: return "bug"
            }
        }
        var titleKey: String {
            switch self {
            case .feedback: return "supportFormFeedbackTitle"
            case .bug: return "supportFormBugTitle"
            }
        }
    }

    let kind: Kind

    @StateObject private var localizationService = LocalizationService.shared
    @State private var subject = ""
    @State private var bodyText = ""
    @State private var isLoading = false
    @State private var showSuccess = false
    @State private var errorMessage: String?

    var body: some View {
        Form {
            Section {
                TextField(localizationService.localizedString("subject", table: "help"), text: $subject)
                TextEditor(text: $bodyText)
                    .frame(minHeight: 160)
            } header: {
                Text(localizationService.localizedString("yourMessage", table: "help"))
            }

            if let errorMessage {
                Section {
                    Text(errorMessage).foregroundColor(.red)
                }
            }

            Section {
                Button(action: submit) {
                    if isLoading { ProgressView() }
                    else {
                        Text(localizationService.localizedString("send", table: "common"))
                    }
                }
                .disabled(isLoading || subject.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || bodyText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .navigationTitle(localizationService.localizedString(kind.titleKey, table: "help"))
        .navigationBarTitleDisplayMode(.inline)
        .alert(localizationService.localizedString("messageSent", table: "help"), isPresented: $showSuccess) {
            Button(localizationService.localizedString("ok", table: "common")) {}
        } message: {
            Text(localizationService.localizedString("messageSentDescription", table: "help"))
        }
    }

    private func submit() {
        Task {
            isLoading = true
            errorMessage = nil
            defer { isLoading = false }
            guard AuthenticationService.shared.isAuthenticated else {
                errorMessage = localizationService.localizedString("pleaseSignIn", table: "errors")
                return
            }
            let meta = NetworkService.SupportTicketClientMetadata(
                appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String,
                build: Bundle.main.infoDictionary?["CFBundleVersion"] as? String,
                os: "iOS \(UIDevice.current.systemVersion)",
                locale: Locale.current.identifier
            )
            do {
                try await NetworkService.shared.submitSupportTicket(
                    subject: subject.trimmingCharacters(in: .whitespacesAndNewlines),
                    body: bodyText.trimmingCharacters(in: .whitespacesAndNewlines),
                    category: kind.category,
                    conversationId: nil,
                    metadata: meta
                )
                showSuccess = true
            } catch {
                errorMessage = NetworkError.userFacingMessage(error)
            }
        }
    }
}
