//
//  HelpSupportView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct HelpSupportView: View {
    @State private var searchText = ""
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        List {
                Section(localizationService.localizedString("frequentlyAskedQuestions", table: "help")) {
                    FAQRow(
                        question: localizationService.localizedString("howToBookDive", table: "help"),
                        answer: localizationService.localizedString("howToBookDiveAnswer", table: "help")
                    )
                    FAQRow(
                        question: localizationService.localizedString("howToLogDive", table: "help"),
                        answer: localizationService.localizedString("howToLogDiveAnswer", table: "help")
                    )
                    FAQRow(
                        question: localizationService.localizedString("whatIncludedInPro", table: "help"),
                        answer: localizationService.localizedString("whatIncludedInProAnswer", table: "help")
                    )
                    FAQRow(
                        question: localizationService.localizedString("howToAddFriends", table: "help"),
                        answer: localizationService.localizedString("howToAddFriendsAnswer", table: "help")
                    )
                }
                
                Section(localizationService.localizedString("contactSupport", table: "help")) {
                    Link(destination: URL(string: "mailto:support@divehub.com")!) {
                        HStack {
                            Image(systemName: "envelope.fill")
                            Text(localizationService.localizedString("emailSupport", table: "help"))
                        }
                    }
                    
                    Link(destination: URL(string: "tel:+1234567890")!) {
                        HStack {
                            Image(systemName: "phone.fill")
                            Text(localizationService.localizedString("callSupport", table: "help"))
                        }
                    }
                    
                    NavigationLink(destination: LiveChatView()) {
                        HStack {
                            Image(systemName: "message.fill")
                            Text(localizationService.localizedString("liveChat", table: "help"))
                        }
                    }
                }
                
                Section(localizationService.localizedString("resources", table: "help")) {
                    NavigationLink(destination: TermsOfServiceView()) {
                        Text(localizationService.localizedString("termsOfService", table: "help"))
                    }
                    
                    NavigationLink(destination: PrivacyPolicyView()) {
                        Text(localizationService.localizedString("privacyPolicy", table: "help"))
                    }
                    
                    Link(destination: URL(string: "https://divehub.com/faq")!) {
                        Text(localizationService.localizedString("websiteFAQ", table: "help"))
                    }
                }
        }
        .navigationTitle(localizationService.localizedString("helpSupport", table: "common"))
        .searchable(text: $searchText, prompt: localizationService.localizedString("searchHelpTopics", table: "help"))
    }
}

struct FAQRow: View {
    let question: String
    let answer: String
    @State private var isExpanded = false
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button(action: {
                withAnimation {
                    isExpanded.toggle()
                }
            }) {
                HStack {
                    Text(question)
                        .font(.headline)
                        .foregroundColor(.primary)
                    Spacer()
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .foregroundColor(.secondary)
                }
            }
            
            if isExpanded {
                Text(answer)
                    .font(.body)
                    .foregroundColor(.secondary)
                    .padding(.top, 4)
            }
        }
        .padding(.vertical, 4)
    }
}

struct LiveChatView: View {
    @StateObject private var localizationService = LocalizationService.shared
    @State private var messageText = ""
    @State private var subject = ""
    @State private var isLoading = false
    @State private var showSuccess = false
    @State private var errorMessage: String?
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        Form {
            Section {
                TextField(localizationService.localizedString("subject", table: "help"), text: $subject)
                TextEditor(text: $messageText)
                    .frame(height: 200)
            } header: {
                Text(localizationService.localizedString("yourMessage", table: "help"))
            }
            
            Section {
                Button(action: sendMessage) {
                    if isLoading {
                        ProgressView()
                    } else {
                        Text(localizationService.localizedString("send", table: "common"))
                    }
                }
                .disabled(isLoading || messageText.isEmpty || subject.isEmpty)
            }
            
            if let error = errorMessage {
                Section {
                    Text(error)
                        .foregroundColor(.red)
                }
            }
        }
        .navigationTitle(localizationService.localizedString("liveChat", table: "help"))
        .alert(localizationService.localizedString("messageSent", table: "help"), isPresented: $showSuccess) {
            Button(localizationService.localizedString("ok", table: "common")) {
                dismiss()
            }
        } message: {
            Text(localizationService.localizedString("messageSentDescription", table: "help"))
        }
    }
    
    private func sendMessage() {
        guard !messageText.isEmpty, !subject.isEmpty else {
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        
        Task {
            // Check authentication
            guard AuthenticationService.shared.isAuthenticated else {
                await MainActor.run {
                    errorMessage = localizationService.localizedString("pleaseSignIn", table: "errors")
                    isLoading = false
                }
                return
            }
            
            // Send support message via email (fallback) or API
            guard AuthenticationService.shared.currentUser?.email != nil else {
                await MainActor.run {
                    errorMessage = localizationService.localizedString("pleaseSignIn", table: "errors")
                    isLoading = false
                }
                return
            }
            
            
            let encodedSubject = subject.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
            let encodedBody = messageText.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
            let mailtoURL = "mailto:support@divehub.com?subject=\(encodedSubject)&body=\(encodedBody)"
            
            
            guard let url = URL(string: mailtoURL) else {
                await MainActor.run {
                    errorMessage = localizationService.localizedString("failedToSend", table: "errors")
                    isLoading = false
                }
                return
            }
            
            await MainActor.run {
                
                UIApplication.shared.open(url, options: [:]) { success in
                    
                    Task { @MainActor in
                        if success {
                            showSuccess = true
                            isLoading = false
                        } else {
                            errorMessage = localizationService.localizedString("failedToSend", table: "errors")
                            isLoading = false
                        }
                    }
                }
            }
        }
    }
}

struct TermsOfServiceView: View {
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        ScrollView {
            Text(localizationService.localizedString("termsOfServiceContent", table: "help"))
                .padding()
        }
        .navigationTitle(localizationService.localizedString("termsOfService", table: "help"))
    }
}

struct PrivacyPolicyView: View {
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        ScrollView {
            Text(localizationService.localizedString("privacyPolicyContent", table: "help"))
                .padding()
        }
        .navigationTitle(localizationService.localizedString("privacyPolicy", table: "help"))
    }
}

#Preview {
    NavigationStack {
        HelpSupportView()
    }
}
