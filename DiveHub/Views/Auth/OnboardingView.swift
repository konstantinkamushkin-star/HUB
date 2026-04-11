//
//  OnboardingView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct OnboardingView: View {
    @StateObject private var localizationService = LocalizationService.shared
    @State private var selectedLanguage: AppLanguage
    @State private var currentPage = 0
    @State private var showLogin = false
    
    private let onboardingTable = "onboarding"
    
    init() {
        _selectedLanguage = State(initialValue: LocalizationService.shared.currentLanguage)
    }
    
    var body: some View {
        Group {
            if showLogin {
                LoginView()
            } else {
                VStack {
                    if currentPage == 0 {
                        LanguageSelectionView(selectedLanguage: $selectedLanguage) {
                            localizationService.currentLanguage = selectedLanguage
                            withAnimation {
                                currentPage = 1
                            }
                        }
                    } else {
                        ZStack {
                            TabView(selection: $currentPage) {
                                OnboardingPage(
                                    title: localizationService.localizedString("welcomeTitle", table: onboardingTable),
                                    description: localizationService.localizedString("welcomeDescription", table: onboardingTable),
                                    imageName: "divehub.logo",
                                    pageIndex: 1
                                )
                                
                                OnboardingPage(
                                    title: localizationService.localizedString("exploreTitle", table: onboardingTable),
                                    description: localizationService.localizedString("exploreDescription", table: onboardingTable),
                                    imageName: "map",
                                    pageIndex: 2
                                )
                                
                                OnboardingPage(
                                    title: localizationService.localizedString("logTitle", table: onboardingTable),
                                    description: localizationService.localizedString("logDescription", table: onboardingTable),
                                    imageName: "book",
                                    pageIndex: 3
                                )
                                
                                OnboardingPage(
                                    title: localizationService.localizedString("bookTitle", table: onboardingTable),
                                    description: localizationService.localizedString("bookDescription", table: onboardingTable),
                                    imageName: "person.2",
                                    pageIndex: 4
                                )
                            }
                            .tabViewStyle(.page(indexDisplayMode: .always))
                            
                            VStack {
                                HStack {
                                    Spacer()
                                    Button(action: completeOnboarding) {
                                        Text(localizationService.localizedString("skip", table: onboardingTable))
                                            .font(.subheadline.weight(.semibold))
                                            .foregroundColor(.divePrimary)
                                    }
                                    .padding(.trailing, 20)
                                    .padding(.top, 12)
                                }
                                Spacer()
                            }
                            
                            VStack {
                                Spacer()
                                if currentPage < 4 {
                                    HStack(spacing: 8) {
                                        Image(systemName: "hand.draw")
                                            .font(.caption.weight(.semibold))
                                        Text(localizationService.localizedString("swipeHint", table: onboardingTable))
                                    }
                                    .font(.footnote)
                                    .foregroundColor(.secondary)
                                    .multilineTextAlignment(.center)
                                    .padding(.horizontal, 24)
                                    .padding(.bottom, 44)
                                }
                                if currentPage == 4 {
                                    Button(action: completeOnboarding) {
                                        Text(localizationService.localizedString("getStarted", table: onboardingTable))
                                            .font(.headline)
                                            .foregroundColor(.white)
                                            .frame(maxWidth: .infinity)
                                            .padding()
                                            .background(Color.divePrimary)
                                            .cornerRadius(12)
                                    }
                                    .padding(.horizontal, 40)
                                    .padding(.bottom, 50)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private func completeOnboarding() {
        UserDefaults.standard.set(true, forKey: "has_completed_onboarding")
        DispatchQueue.main.async {
            withAnimation {
                showLogin = true
            }
        }
    }
}

struct LanguageSelectionView: View {
    @ObservedObject private var localizationService = LocalizationService.shared
    @Binding var selectedLanguage: AppLanguage
    var onContinue: () -> Void
    
    private let onboardingTable = "onboarding"
    
    var body: some View {
        VStack(spacing: 30) {
            Text(localizationService.localizedString("selectLanguage", table: onboardingTable))
                .font(.largeTitle)
                .fontWeight(.bold)
            
            VStack(spacing: 16) {
                ForEach(AppLanguage.allCases, id: \.self) { language in
                    Button(action: {
                        selectedLanguage = language
                    }) {
                        HStack {
                            Text(language.displayName)
                                .font(.headline)
                            Spacer()
                            if selectedLanguage == language {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(.divePrimary)
                            }
                        }
                        .padding()
                        .background(selectedLanguage == language ? Color.divePrimary.opacity(0.1) : Color.clear)
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(selectedLanguage == language ? Color.divePrimary : Color.clear, lineWidth: 2)
                        )
                    }
                    .foregroundColor(.primary)
                }
            }
            .padding(.horizontal)
            
            Button(action: onContinue) {
                Text(localizationService.localizedString("continue", table: onboardingTable))
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.divePrimary)
                    .cornerRadius(12)
            }
            .padding(.horizontal)
        }
        .padding()
    }
}

struct OnboardingPage: View {
    let title: String
    let description: String
    let imageName: String
    let pageIndex: Int
    
    var body: some View {
        VStack(spacing: 30) {
            if imageName == "water.waves" || imageName == "divehub.logo" {
                DiveHubLogoMark(color: .divePrimary)
                    .frame(width: 120, height: 90)
            } else {
                Image(systemName: imageName)
                    .font(.system(size: 80))
                    .foregroundColor(.divePrimary)
            }
            
            Text(title)
                .font(.largeTitle)
                .fontWeight(.bold)
                .multilineTextAlignment(.center)
            
            Text(description)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
        }
        .tag(pageIndex)
    }
}

#Preview {
    OnboardingView()
}
