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
                    
                    NavigationLink(destination: NewSupportTopicView()) {
                        HStack {
                            Image(systemName: "message.fill")
                            Text(localizationService.localizedString("liveChat", table: "help"))
                        }
                    }
                    
                    NavigationLink(destination: SupportTicketFormView(kind: .feedback)) {
                        HStack {
                            Image(systemName: "text.bubble.fill")
                            Text(localizationService.localizedString("supportFormFeedbackTitle", table: "help"))
                        }
                    }
                    
                    NavigationLink(destination: SupportTicketFormView(kind: .bug)) {
                        HStack {
                            Image(systemName: "exclamationmark.triangle.fill")
                            Text(localizationService.localizedString("supportFormBugTitle", table: "help"))
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
