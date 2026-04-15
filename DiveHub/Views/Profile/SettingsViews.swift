//
//  SettingsViews.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import UIKit

struct LanguageSettingsView: View {
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        Form {
            Section(localizationService.localizedString("selectLanguage")) {
                ForEach(AppLanguage.allCases, id: \.self) { language in
                    Button(action: {
                        localizationService.currentLanguage = language
                    }) {
                        HStack {
                            Text(language.displayName)
                            Spacer()
                            if localizationService.currentLanguage == language {
                                Image(systemName: "checkmark")
                                    .foregroundColor(.divePrimary)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle(localizationService.localizedString("language"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct AppearanceSettingsView: View {
    @StateObject private var settingsService = SettingsService.shared
    @StateObject private var localizationService = LocalizationService.shared

    var body: some View {
        Form {
            Section {
                Text(localizationService.localizedString("appearanceFooter", table: "settings"))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Section(localizationService.localizedString("appearance", table: "settings")) {
                ForEach(AppThemePreference.allCases, id: \.rawValue) { pref in
                    Button {
                        settingsService.saveThemePreference(pref)
                    } label: {
                        HStack {
                            Text(title(for: pref))
                            Spacer()
                            if settingsService.themePreference == pref {
                                Image(systemName: "checkmark")
                                    .foregroundColor(.divePrimary)
                            }
                        }
                    }
                }
            }
            Section {
                Text(localizationService.localizedString("interfaceScaleFooter", table: "settings"))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Section(localizationService.localizedString("interfaceScale", table: "settings")) {
                ForEach(InterfaceScalePreset.allCases) { preset in
                    Button {
                        settingsService.saveInterfaceScalePreset(preset)
                    } label: {
                        HStack {
                            Text(interfaceScaleTitle(for: preset))
                            Spacer()
                            if settingsService.interfaceScalePreset == preset {
                                Image(systemName: "checkmark")
                                    .foregroundColor(.divePrimary)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle(localizationService.localizedString("appearance", table: "settings"))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func title(for pref: AppThemePreference) -> String {
        switch pref {
        case .system:
            return localizationService.localizedString("appearanceSystem", table: "settings")
        case .light:
            return localizationService.localizedString("appearanceLight", table: "settings")
        case .dark:
            return localizationService.localizedString("appearanceDark", table: "settings")
        }
    }

    private func interfaceScaleTitle(for preset: InterfaceScalePreset) -> String {
        localizationService.localizedString("interfaceScale_\(preset.rawValue)", table: "settings")
    }
}

struct SubscriptionView: View {
    @StateObject private var authService = AuthenticationService.shared
    @StateObject private var localizationService = LocalizationService.shared
    @State private var selectedPlan: SubscriptionPlan = .monthly
    @State private var showPaymentForm = false
    @State private var showCancellationConfirmation = false
    @State private var isProcessing = false
    @State private var errorMessage: String?
    
    enum SubscriptionPlan {
        case monthly
        case annual
    }
    
    var body: some View {
        Form {
            if authService.currentUser?.role == .diverBasic {
                Section(localizationService.localizedString("upgradeToPro", table: "settings")) {
                    Picker(localizationService.localizedString("plan", table: "settings"), selection: $selectedPlan) {
                        Text(localizationService.localizedString("monthly", table: "settings")).tag(SubscriptionPlan.monthly)
                        Text(localizationService.localizedString("annual", table: "settings")).tag(SubscriptionPlan.annual)
                    }
                    
                    Button(localizationService.localizedString("subscribe", table: "settings")) {
                        showPaymentForm = true
                    }
                    .disabled(isProcessing)
                }
                
                Section {
                    Text(localizationService.localizedString("proFeatures", table: "settings"))
                    Text(localizationService.localizedString("noAds", table: "settings"))
                    Text(localizationService.localizedString("advancedLogbook", table: "settings"))
                    Text(localizationService.localizedString("sensorIntegration", table: "settings"))
                    Text(localizationService.localizedString("friendTracking", table: "settings"))
                    Text(localizationService.localizedString("groupChats", table: "settings"))
                    Text(localizationService.localizedString("groupBooking", table: "settings"))
                    Text(localizationService.localizedString("gearProfiles", table: "settings"))
                    Text(localizationService.localizedString("achievementSystem", table: "settings"))
                    Text(localizationService.localizedString("prioritySupport", table: "settings"))
                }
            } else {
                Section(localizationService.localizedString("currentSubscription", table: "settings")) {
                    Text(localizationService.localizedString("proSubscriptionActive", table: "settings"))
                        .foregroundColor(.green)
                    
                    if let expiresAt = authService.currentUser?.subscriptionExpiresAt {
                        Text(localizationService.localizedString("expiresOn", table: "settings") + ": \(expiresAt.formatted(date: .abbreviated, time: .omitted))")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    
                    Button(role: .destructive, action: {
                        showCancellationConfirmation = true
                    }) {
                        Text(localizationService.localizedString("cancelSubscription", table: "settings"))
                    }
                    .disabled(isProcessing)
                }
            }
            
            if let error = errorMessage {
                Section {
                    Text(error)
                        .foregroundColor(.red)
                        .font(.caption)
                }
            }
        }
        .navigationTitle(localizationService.localizedString("subscription", table: "settings"))
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showPaymentForm) {
            PaymentFormView(
                plan: selectedPlan,
                onComplete: { success in
                    showPaymentForm = false
                    if success {
                        Task {
                            await handleSubscription()
                        }
                    }
                }
            )
        }
        .sheet(isPresented: $showCancellationConfirmation) {
            SubscriptionCancellationView(
                onConfirm: {
                    showCancellationConfirmation = false
                    Task {
                        await handleCancellation()
                    }
                },
                onCancel: {
                    showCancellationConfirmation = false
                }
            )
        }
    }
    
    private func handleSubscription() async {
        isProcessing = true
        errorMessage = nil
        do {
            try await authService.upgradeToPro(monthly: selectedPlan == .monthly)
        } catch {
            errorMessage = error.localizedDescription
        }
        isProcessing = false
    }
    
    private func handleCancellation() async {
        isProcessing = true
        errorMessage = nil
        do {
            try await authService.cancelSubscription()
        } catch {
            errorMessage = error.localizedDescription
        }
        isProcessing = false
    }
}

struct PaymentFormView: View {
    let plan: SubscriptionView.SubscriptionPlan
    let onComplete: (Bool) -> Void
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @State private var selectedPaymentMethod: PaymentMethod = .applePay
    @State private var cardNumber = ""
    @State private var cardHolderName = ""
    @State private var expiryDate = ""
    @State private var cvv = ""
    @State private var isProcessing = false
    
    enum PaymentMethod: String, CaseIterable {
        case applePay = "Apple Pay"
        case creditCard = "Credit Card"
        
        var displayName: String {
            return self.rawValue
        }
    }
    
    var planPrice: String {
        switch plan {
        case .monthly:
            return "$9.99/month"
        case .annual:
            return "$99.99/year"
        }
    }
    
    var body: some View {
        NavigationStack {
            Form {
                Section(localizationService.localizedString("plan", table: "settings")) {
                    Text(plan == .monthly ? localizationService.localizedString("monthly", table: "settings") : localizationService.localizedString("annual", table: "settings"))
                    Text(planPrice)
                        .font(.headline)
                        .foregroundColor(.divePrimary)
                }
                
                Section(localizationService.localizedString("paymentMethod", table: "settings")) {
                    Picker("", selection: $selectedPaymentMethod) {
                        ForEach(PaymentMethod.allCases, id: \.self) { method in
                            Text(method.displayName).tag(method)
                        }
                    }
                    .pickerStyle(.segmented)
                    
                    if selectedPaymentMethod == .creditCard {
                        TextField(localizationService.localizedString("cardNumber", table: "settings"), text: $cardNumber)
                            .keyboardType(.numberPad)
                        TextField(localizationService.localizedString("cardHolderName", table: "settings"), text: $cardHolderName)
                        TextField(localizationService.localizedString("expiryDate", table: "settings"), text: $expiryDate)
                            .keyboardType(.numberPad)
                        TextField("ui_profile_cvv".localized, text: $cvv)
                            .keyboardType(.numberPad)
                    } else {
                        Text(localizationService.localizedString("applePayDescription", table: "settings"))
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                
                Section {
                    Button(localizationService.localizedString("payNow", table: "settings")) {
                        isProcessing = true
                        // Simulate payment processing
                        Task {
                            try? await Task.sleep(nanoseconds: 1_500_000_000) // 1.5 seconds
                            await MainActor.run {
                                isProcessing = false
                                onComplete(true)
                            }
                        }
                    }
                    .disabled(isProcessing || (selectedPaymentMethod == .creditCard && (cardNumber.isEmpty || cardHolderName.isEmpty || expiryDate.isEmpty || cvv.isEmpty)))
                    
                    if isProcessing {
                        ProgressView()
                            .frame(maxWidth: .infinity, alignment: .center)
                    }
                }
            }
            .navigationTitle(localizationService.localizedString("payment", table: "booking"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localizationService.localizedString("cancel")) {
                        dismiss()
                    }
                }
            }
        }
    }
}

struct SubscriptionCancellationView: View {
    let onConfirm: () -> Void
    let onCancel: () -> Void
    @StateObject private var localizationService = LocalizationService.shared
    @State private var cancellationReason = ""
    @State private var selectedReason: CancellationReason = .tooExpensive
    
    enum CancellationReason: String, CaseIterable {
        case tooExpensive = "Too Expensive"
        case notUsingFeatures = "Not Using Features"
        case foundAlternative = "Found Alternative"
        case other = "Other"
        
        var displayName: String {
            return self.rawValue
        }
    }
    
    var body: some View {
        NavigationStack {
            Form {
                Section(localizationService.localizedString("cancelSubscription", table: "settings")) {
                    Text(localizationService.localizedString("cancellationWarning", table: "settings"))
                        .font(.caption)
                        .foregroundColor(.orange)
                }
                
                Section(localizationService.localizedString("reasonForCancellation", table: "settings")) {
                    Picker("", selection: $selectedReason) {
                        ForEach(CancellationReason.allCases, id: \.self) { reason in
                            Text(reason.displayName).tag(reason)
                        }
                    }
                    
                    if selectedReason == .other {
                        TextEditor(text: $cancellationReason)
                            .frame(height: 100)
                    }
                }
                
                Section {
                    Button(role: .destructive, action: onConfirm) {
                        Text(localizationService.localizedString("confirmCancellation", table: "settings"))
                    }
                    
                    Button(action: onCancel) {
                        Text(localizationService.localizedString("keepSubscription", table: "settings"))
                    }
                }
            }
            .navigationTitle(localizationService.localizedString("cancelSubscription", table: "settings"))
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

struct CertificationsView: View {
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var authService = AuthenticationService.shared
    @State private var certifications: [Certification] = []
    @State private var showAddCertification = false
    @State private var isLoading = false
    @State private var errorMessage: String?
    
    var body: some View {
        List {
            if let error = errorMessage, error.contains("sign out") || error.contains("session") {
                Section {
                    VStack(spacing: 12) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.largeTitle)
                            .foregroundColor(.orange)
                        Text("ui_profile_session_expired".localized)
                            .font(.headline)
                        Text(error)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                        Button("ui_sign_out_and_sign_in".localized) {
                            authService.signOut()
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                }
            } else if certifications.isEmpty && !isLoading {
                Text(localizationService.localizedString("noCertifications", table: "settings"))
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding()
            } else {
                ForEach(certifications) { cert in
                    NavigationLink(destination: CertificationDetailView(certification: cert)) {
                        CertificationRow(certification: cert)
                    }
                }
                .onDelete(perform: deleteCertifications)
            }
        }
        .navigationTitle(localizationService.localizedString("certifications"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: {
                    showAddCertification = true
                }) {
                    Image(systemName: "plus")
                }
                .disabled(errorMessage != nil && (errorMessage?.contains("sign out") ?? false || errorMessage?.contains("session") ?? false))
            }
        }
        .sheet(isPresented: $showAddCertification) {
            AddCertificationView { certification, imageData in
                Task {
                    await saveCertification(certification, imageData: imageData)
                }
            }
        }
        .refreshable {
            await loadCertifications()
        }
        .task {
            await loadCertifications()
        }
        .alert("ui_logbook_error".localized, isPresented: .constant(errorMessage != nil && !(errorMessage?.contains("sign out") ?? false) && !(errorMessage?.contains("session") ?? false))) {
            Button("ok".localized) {
                errorMessage = nil
            }
        } message: {
            if let error = errorMessage {
                Text(error)
            }
        }
    }
    
    private func loadCertifications() async {
        guard let userId = authService.currentUser?.id else { return }
        
        // Check if tokens are available
        let hasToken = KeychainService.shared.getAccessToken() != nil
        if !hasToken {
            errorMessage = "Please sign out and sign in again to refresh your session."
            isLoading = false
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        do {
            certifications = try await NetworkService.shared.getCertifications(userId: userId)
            isLoading = false
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
        }
    }
    
    private func saveCertification(_ certification: Certification, imageData: Data?) async {
        guard let userId = authService.currentUser?.id else {
            errorMessage = "User not logged in"
            return
        }
        
        // Check if tokens are available
        let hasToken = KeychainService.shared.getAccessToken() != nil
        if !hasToken {
            errorMessage = "Please sign out and sign in again to refresh your session."
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        do {
            let savedCert = try await NetworkService.shared.createCertification(
                userId: userId,
                certification: certification,
                cardImageData: imageData
            )
            await MainActor.run {
                certifications.append(savedCert)
                isLoading = false
            }
        } catch {
            await MainActor.run {
                isLoading = false
                // Check if it's a 401 error and provide a helpful message
                if let networkError = error as? NetworkError,
                   case .serverError(401) = networkError {
                    errorMessage = "Your session has expired. Please sign out and sign in again."
                } else {
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
    
    private func deleteCertifications(at offsets: IndexSet) {
        Task {
            for index in offsets {
                let cert = certifications[index]
                do {
                    try await NetworkService.shared.deleteCertification(certificationId: cert.id)
                    certifications.remove(at: index)
                } catch {
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}

struct AddCertificationView: View {
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @State private var organization = ""
    @State private var level = ""
    @State private var issueDate = Date()
    @State private var instructorNumber = ""
    @State private var showImagePicker = false
    @State private var selectedImage: UIImage?
    @State private var isSaving = false
    @State private var isProcessingOCR = false
    @State private var showOCRAlert = false
    var onSave: (Certification, Data?) async -> Void
    
    var body: some View {
        NavigationStack {
            Form {
                Section(localizationService.localizedString("certificationDetails", table: "settings")) {
                    TextField(localizationService.localizedString("organization", table: "settings"), text: $organization)
                        .autocapitalization(.allCharacters)
                    TextField(localizationService.localizedString("level", table: "settings"), text: $level)
                    DatePicker(localizationService.localizedString("issueDate", table: "settings"), selection: $issueDate, displayedComponents: .date)
                    TextField(localizationService.localizedString("instructorNumber", table: "settings"), text: $instructorNumber)
                        .keyboardType(.default)
                }
                
                Section(localizationService.localizedString("cardPhoto", table: "settings")) {
                    Button(action: {
                        showImagePicker = true
                    }) {
                        if let image = selectedImage {
                            Image(uiImage: image)
                                .resizable()
                                .aspectRatio(contentMode: .fit)
                                .frame(maxHeight: 200)
                        } else {
                            Label(localizationService.localizedString("addPhoto", table: "settings"), systemImage: "camera.fill")
                        }
                    }
                    
                    if let image = selectedImage {
                        Button(action: {
                            Task {
                                await processImageWithOCR(image: image)
                            }
                        }) {
                            HStack {
                                if isProcessingOCR {
                                    ProgressView()
                                        .scaleEffect(0.8)
                                } else {
                                    Image(systemName: "text.viewfinder")
                                }
                                Text(localizationService.localizedString("extractData", table: "settings"))
                            }
                        }
                        .disabled(isProcessingOCR)
                    }
                }
            }
            .navigationTitle(localizationService.localizedString("addCertification", table: "settings"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(localizationService.localizedString("cancel")) {
                        dismiss()
                    }
                    .disabled(isSaving)
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(localizationService.localizedString("save")) {
                        isSaving = true
                        let certification = Certification(
                            id: UUID().uuidString,
                            organization: organization,
                            level: level,
                            cardImageURL: nil,
                            issueDate: issueDate,
                            verificationStatus: .pending,
                            instructorNumber: instructorNumber.isEmpty ? nil : instructorNumber
                        )
                        let imageData = selectedImage?.jpegData(compressionQuality: 0.8)
                        Task {
                            await onSave(certification, imageData)
                            await MainActor.run {
                                isSaving = false
                                dismiss()
                            }
                        }
                    }
                    .disabled(organization.isEmpty || level.isEmpty || isSaving)
                }
            }
            .sheet(isPresented: $showImagePicker) {
                ImagePicker(selectedImage: $selectedImage)
            }
            .onChange(of: selectedImage) { oldValue, newValue in
                if newValue != nil {
                    // Auto-process OCR when image is selected
                    Task {
                        if let image = newValue {
                            await processImageWithOCR(image: image)
                        }
                    }
                }
            }
            .alert(localizationService.localizedString("dataExtracted", table: "settings"), isPresented: $showOCRAlert) {
                Button(localizationService.localizedString("ok")) {
                    showOCRAlert = false
                }
            } message: {
                Text(localizationService.localizedString("dataExtractedMessage", table: "settings"))
            }
        }
    }
    
    private func processImageWithOCR(image: UIImage) async {
        await MainActor.run {
            isProcessingOCR = true
        }
        
        let certificateData = await OCRService.shared.extractCertificateData(from: image)
        
        await MainActor.run {
            if let org = certificateData.organization, !org.isEmpty {
                organization = org
            }
            if let certLevel = certificateData.level, !certLevel.isEmpty {
                level = certLevel
            }
            if let date = certificateData.issueDate {
                issueDate = date
            }
            if let instructorNum = certificateData.instructorNumber, !instructorNum.isEmpty {
                instructorNumber = instructorNum
            }
            
            isProcessingOCR = false
            
            // Show alert if any data was extracted
            if certificateData.organization != nil || certificateData.level != nil {
                showOCRAlert = true
            }
        }
    }
}

struct CertificationRow: View {
    let certification: Certification
    
    var body: some View {
        HStack {
            if let imageUrl = certification.cardImageURL, let url = URL(string: imageUrl) {
                AsyncImage(url: url) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } placeholder: {
                    Image(systemName: "doc.text")
                        .foregroundColor(.secondary)
                }
                .frame(width: 50, height: 50)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            } else {
                Image(systemName: "doc.text")
                    .foregroundColor(.secondary)
                    .frame(width: 50, height: 50)
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text(certification.level)
                    .font(.headline)
                Text(certification.organization)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                if let issueDate = certification.issueDate {
                    Text(issueDate.formatted(date: .abbreviated, time: .omitted))
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            Spacer()
            if certification.verificationStatus == .verified {
                Image(systemName: "checkmark.seal.fill")
                    .foregroundColor(.green)
            } else if certification.verificationStatus == .rejected {
                Image(systemName: "xmark.seal.fill")
                    .foregroundColor(.red)
            }
        }
    }
}

struct CertificationDetailView: View {
    let certification: Certification
    @StateObject private var localizationService = LocalizationService.shared
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Card Image
                if let imageUrl = certification.cardImageURL, let url = URL(string: imageUrl) {
                    AsyncImage(url: url) { image in
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                    } placeholder: {
                        ProgressView()
                    }
                    .frame(maxHeight: 400)
                    .cornerRadius(12)
                    .padding()
                }
                
                // Details
                VStack(alignment: .leading, spacing: 16) {
                    SettingsDetailRow(
                        label: localizationService.localizedString("organization", table: "settings"),
                        value: certification.organization
                    )
                    SettingsDetailRow(
                        label: localizationService.localizedString("level", table: "settings"),
                        value: certification.level
                    )
                    if let issueDate = certification.issueDate {
                        SettingsDetailRow(
                            label: localizationService.localizedString("issueDate", table: "settings"),
                            value: issueDate.formatted(date: .long, time: .omitted)
                        )
                    }
                    if let instructorNumber = certification.instructorNumber {
                        SettingsDetailRow(
                            label: localizationService.localizedString("instructorNumber", table: "settings"),
                            value: instructorNumber
                        )
                    }
                    SettingsDetailRow(
                        label: localizationService.localizedString("status", table: "settings"),
                        value: statusText(for: certification.verificationStatus)
                    )
                }
                .padding()
            }
        }
        .navigationTitle(certification.level)
        .navigationBarTitleDisplayMode(.inline)
    }
    
    private func statusText(for status: Certification.VerificationStatus) -> String {
        switch status {
        case .pending:
            return localizationService.localizedString("pending", table: "common")
        case .verified:
            return localizationService.localizedString("verified", table: "settings")
        case .rejected:
            return localizationService.localizedString("rejected", table: "settings")
        }
    }
}

struct SettingsDetailRow: View {
    let label: String
    let value: String
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
            Text(value)
                .font(.body)
        }
    }
}

struct GearProfilesView: View {
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var authService = AuthenticationService.shared
    @State private var gearProfiles: [GearProfile] = []
    @State private var showAddProfile = false
    @State private var isLoading = false
    
    var body: some View {
        List {
            Section {
                Button(action: {
                    showAddProfile = true
                }) {
                    HStack {
                        Image(systemName: "plus.circle.fill")
                        Text("ui_profile_add_gear_profile".localized)
                    }
                }
            }
            
            Section("ui_gear_profiles".localized) {
                if gearProfiles.isEmpty {
                    Text("ui_profile_no_gear_profiles_yet_create_one_to_save_your_gear_sizes".localized)
                        .foregroundColor(.secondary)
                        .font(.caption)
                } else {
                    ForEach(gearProfiles) { profile in
                        NavigationLink(destination: GearProfileDetailView(profile: profile)) {
                            GearProfileRow(profile: profile)
                        }
                    }
                    .onDelete(perform: deleteProfiles)
                }
            }
        }
        .navigationTitle(localizationService.localizedString("gearProfiles"))
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showAddProfile) {
            AddGearProfileView { profile in
                gearProfiles.append(profile)
                saveProfiles()
            }
        }
        .task {
            loadProfiles()
        }
    }
    
    private func loadProfiles() {
        if let data = UserDefaults.standard.data(forKey: "gear_profiles"),
           let profiles = try? JSONDecoder().decode([GearProfile].self, from: data) {
            gearProfiles = profiles
        }
    }
    
    private func saveProfiles() {
        if let data = try? JSONEncoder().encode(gearProfiles) {
            UserDefaults.standard.set(data, forKey: "gear_profiles")
        }
    }
    
    private func deleteProfiles(at offsets: IndexSet) {
        gearProfiles.remove(atOffsets: offsets)
        saveProfiles()
    }
}

struct GearProfileRow: View {
    let profile: GearProfile
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(profile.name)
                .font(.headline)
            Text("ui_profile_value_items".localized)
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }
}

struct GearProfileDetailView: View {
    let profile: GearProfile
    @StateObject private var localizationService = LocalizationService.shared
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        List {
            Section("ui_profile_name".localized) {
                Text(profile.name)
            }
            
            Section("ui_gear_items".localized) {
                ForEach(profile.items) { item in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.category.rawValue.capitalized)
                            .font(.headline)
                        Text("ui_profile_size_value".localized)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        if let notes = item.notes, !notes.isEmpty {
                            Text(notes)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
            }
        }
        .navigationTitle(profile.name)
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct AddGearProfileView: View {
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    @State private var profileName = ""
    @State private var selectedCategory: GearItem.GearCategory = .wetsuit
    @State private var size = ""
    @State private var notes = ""
    @State private var items: [GearProfile.GearProfileItem] = []
    @State private var showAddItem = false
    
    var onSave: (GearProfile) -> Void
    
    var body: some View {
        NavigationView {
            Form {
                Section("ui_profile_name".localized) {
                    TextField("ui_profile_enter_profile_name".localized, text: $profileName)
                }
                
                Section("ui_gear_items".localized) {
                    ForEach(items) { item in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.category.rawValue.capitalized)
                                .font(.headline)
                            Text("ui_profile_size_value".localized)
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                    }
                    .onDelete(perform: deleteItems)
                    
                    Button(action: {
                        showAddItem = true
                    }) {
                        HStack {
                            Image(systemName: "plus.circle")
                            Text("ui_profile_add_gear_item".localized)
                        }
                    }
                }
            }
            .navigationTitle("ui_profile_new_gear_profile".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("ui_cancel".localized) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("ui_save".localized) {
                        let profile = GearProfile(
                            id: UUID().uuidString,
                            name: profileName.isEmpty ? "Untitled Profile" : profileName,
                            items: items,
                            createdAt: Date(),
                            updatedAt: Date()
                        )
                        onSave(profile)
                        dismiss()
                    }
                    .disabled(profileName.isEmpty && items.isEmpty)
                }
            }
            .sheet(isPresented: $showAddItem) {
                AddGearItemView { item in
                    items.append(item)
                }
            }
        }
    }
    
    private func deleteItems(at offsets: IndexSet) {
        items.remove(atOffsets: offsets)
    }
}

struct AddGearItemView: View {
    @Environment(\.dismiss) var dismiss
    @State private var selectedCategory: GearItem.GearCategory = .wetsuit
    @State private var size = ""
    @State private var notes = ""
    
    var onSave: (GearProfile.GearProfileItem) -> Void
    
    var body: some View {
        NavigationView {
            Form {
                Section("ui_profile_category".localized) {
                    Picker("ui_profile_category".localized, selection: $selectedCategory) {
                        ForEach(GearItem.GearCategory.allCases, id: \.self) { category in
                            Text(category.rawValue.capitalized).tag(category)
                        }
                    }
                }
                
                Section("ui_size".localized) {
                    TextField("ui_profile_enter_size_placeholder".localized, text: $size)
                        .keyboardType(.default)
                }
                
                Section("ui_notes_optional".localized) {
                    TextEditor(text: $notes)
                        .frame(height: 100)
                }
            }
            .navigationTitle("ui_profile_add_gear_item".localized)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("ui_cancel".localized) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("ui_add".localized) {
                        let item = GearProfile.GearProfileItem(
                            id: UUID().uuidString,
                            category: selectedCategory,
                            size: size,
                            notes: notes.isEmpty ? nil : notes
                        )
                        onSave(item)
                        dismiss()
                    }
                    .disabled(size.isEmpty)
                }
            }
        }
    }
}


struct NotificationSettingsView: View {
    @StateObject private var settingsService = SettingsService.shared
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        Form {
            Section(localizationService.localizedString("notifications")) {
                Toggle(localizationService.localizedString("pushNotifications", table: "settings"), isOn: Binding(
                    get: { settingsService.notificationSettings.pushNotifications },
                    set: { newValue in
                        settingsService.notificationSettings.pushNotifications = newValue
                        settingsService.saveNotificationSettings()
                    }
                ))
                Toggle(localizationService.localizedString("bookingReminders", table: "settings"), isOn: Binding(
                    get: { settingsService.notificationSettings.bookingReminders },
                    set: { newValue in
                        settingsService.notificationSettings.bookingReminders = newValue
                        settingsService.saveNotificationSettings()
                    }
                ))
                Toggle(localizationService.localizedString("friendActivity", table: "settings"), isOn: Binding(
                    get: { settingsService.notificationSettings.friendActivity },
                    set: { newValue in
                        settingsService.notificationSettings.friendActivity = newValue
                        settingsService.saveNotificationSettings()
                    }
                ))
                Toggle(localizationService.localizedString("newMessages", table: "settings"), isOn: Binding(
                    get: { settingsService.notificationSettings.newMessages },
                    set: { newValue in
                        settingsService.notificationSettings.newMessages = newValue
                        settingsService.saveNotificationSettings()
                    }
                ))
            }
        }
        .navigationTitle(localizationService.localizedString("notifications"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct PrivacySettingsView: View {
    @StateObject private var settingsService = SettingsService.shared
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        Form {
            Section(localizationService.localizedString("privacy")) {
                Toggle(localizationService.localizedString("shareLocation", table: "settings"), isOn: Binding(
                    get: { settingsService.privacySettings.shareLocation },
                    set: { newValue in
                        settingsService.privacySettings.shareLocation = newValue
                        settingsService.savePrivacySettings()
                    }
                ))
                Toggle(localizationService.localizedString("publicProfile", table: "settings"), isOn: Binding(
                    get: { settingsService.privacySettings.publicProfile },
                    set: { newValue in
                        settingsService.privacySettings.publicProfile = newValue
                        settingsService.savePrivacySettings()
                    }
                ))
                Toggle(localizationService.localizedString("showInFriendSearch", table: "settings"), isOn: Binding(
                    get: { settingsService.privacySettings.showInFriendSearch },
                    set: { newValue in
                        settingsService.privacySettings.showInFriendSearch = newValue
                        settingsService.savePrivacySettings()
                    }
                ))
                Toggle(localizationService.localizedString("shareLogbook", table: "settings"), isOn: Binding(
                    get: { settingsService.privacySettings.shareLogbook },
                    set: { newValue in
                        settingsService.privacySettings.shareLogbook = newValue
                        settingsService.savePrivacySettings()
                    }
                ))
            }
        }
        .navigationTitle(localizationService.localizedString("privacy"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct InstructorModeToggleView: View {
    @AppStorage("instructorModeEnabled") private var instructorModeEnabled = true
    @StateObject private var localizationService = LocalizationService.shared
    
    var body: some View {
        Toggle(isOn: $instructorModeEnabled) {
            VStack(alignment: .leading, spacing: 4) {
                Text(localizationService.localizedString("instructorMode", table: "settings"))
                    .font(.body)
                Text(localizationService.localizedString("instructorModeDescription", table: "settings"))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .onChange(of: instructorModeEnabled) { oldValue, newValue in
            // Post notification to refresh MainTabView
            NotificationCenter.default.post(name: .instructorModeChanged, object: nil)
        }
    }
}

struct MeasurementUnitsSettingsView: View {
    @StateObject private var settingsService = SettingsService.shared
    @StateObject private var localizationService = LocalizationService.shared
    @State private var selectedUnit: MeasurementUnit = .metric
    @State private var showRecalculationAlert = false
    
    var body: some View {
        Form {
            Section {
                Text(localizationService.localizedString("unitsDescription", table: "settings"))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Section(localizationService.localizedString("measurementUnits", table: "settings")) {
                ForEach(MeasurementUnit.allCases, id: \.self) { unit in
                    Button(action: {
                        if selectedUnit != unit {
                            selectedUnit = unit
                            let newUnits = unit == .metric ? MeasurementUnits.metric : MeasurementUnits.imperial
                            settingsService.saveMeasurementUnits(newUnits)
                            showRecalculationAlert = true
                        }
                    }) {
                        HStack {
                            Text(unit.displayName)
                            Spacer()
                            if (unit == .metric && settingsService.measurementUnits.depth == .meters) ||
                               (unit == .imperial && settingsService.measurementUnits.depth == .feet) {
                                Image(systemName: "checkmark")
                                    .foregroundColor(.divePrimary)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle(localizationService.localizedString("measurementUnits", table: "settings"))
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            selectedUnit = settingsService.measurementUnits.depth == .meters ? .metric : .imperial
        }
        .alert(localizationService.localizedString("unitsChanged", table: "settings"), isPresented: $showRecalculationAlert) {
            Button(localizationService.localizedString("ok")) {
                showRecalculationAlert = false
            }
        } message: {
            Text(localizationService.localizedString("unitsRecalculated", table: "settings"))
        }
    }
}

/// Связка приложения с NestJS и (опционально) Python AI: Dive Editor → `/api/v1/image/*` и `/api/v1/underwater-ai/process`.
#if DEBUG
struct DeveloperBackendSettingsView: View {
    @StateObject private var localizationService = LocalizationService.shared
    @State private var nestBaseURL: String = ""
    @State private var adminWebBaseURLField: String = ""
    @State private var visionBaseURL: String = ""
    @State private var statusText: String?

    var body: some View {
        Form {
            Section {
                Text(localizationService.localizedString("devBackendConnectionHint", table: "settings"))
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text("ui_profile_neural_test_mac_instructions".localized)
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            Section(localizationService.localizedString("devNestBaseURL", table: "settings")) {
                TextField("ui_profile_lan_url_placeholder".localized, text: $nestBaseURL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    #if os(iOS)
                    .keyboardType(.URL)
                    #endif
            }
            Section {
                Text(localizationService.localizedString("devAdminWebURLHint", table: "settings"))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Section(localizationService.localizedString("devAdminWebURL", table: "settings")) {
                TextField("ui_profile_https_dive_hub_ru".localized, text: $adminWebBaseURLField)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    #if os(iOS)
                    .keyboardType(.URL)
                    #endif
            }
            Section {
                Text(localizationService.localizedString("devVisionModuleHint", table: "settings"))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Section(localizationService.localizedString("devVisionModuleURL", table: "settings")) {
                TextField("ui_profile_localhost_url_placeholder".localized, text: $visionBaseURL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    #if os(iOS)
                    .keyboardType(.URL)
                    #endif
            }
            if let statusText {
                Section {
                    Text(statusText)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            Section {
                Button(localizationService.localizedString("devSaveURLs", table: "settings")) {
                    save()
                }
                Button(localizationService.localizedString("devResetURLs", table: "settings"), role: .cancel) {
                    reset()
                }
            }
        }
        .navigationTitle(localizationService.localizedString("devBackendConnection", table: "settings"))
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            nestBaseURL = UserDefaults.standard.string(forKey: NetworkService.apiBaseURLUserDefaultsKey) ?? ""
            adminWebBaseURLField = UserDefaults.standard.string(forKey: NetworkService.adminWebBaseURLUserDefaultsKey) ?? ""
            visionBaseURL = UserDefaults.standard.string(forKey: NetworkService.underwaterVisionModuleBaseURLKey) ?? ""
        }
    }

    private func save() {
        let nest = nestBaseURL.trimmingCharacters(in: .whitespacesAndNewlines)
        let adminWeb = adminWebBaseURLField.trimmingCharacters(in: .whitespacesAndNewlines)
        let vis = visionBaseURL.trimmingCharacters(in: .whitespacesAndNewlines)
        if nest.isEmpty {
            UserDefaults.standard.removeObject(forKey: NetworkService.apiBaseURLUserDefaultsKey)
        } else {
            UserDefaults.standard.set(nest, forKey: NetworkService.apiBaseURLUserDefaultsKey)
        }
        if adminWeb.isEmpty {
            UserDefaults.standard.removeObject(forKey: NetworkService.adminWebBaseURLUserDefaultsKey)
        } else {
            UserDefaults.standard.set(adminWeb, forKey: NetworkService.adminWebBaseURLUserDefaultsKey)
        }
        if vis.isEmpty {
            UserDefaults.standard.removeObject(forKey: NetworkService.underwaterVisionModuleBaseURLKey)
        } else {
            UserDefaults.standard.set(vis, forKey: NetworkService.underwaterVisionModuleBaseURLKey)
        }
        statusText = "Saved. API: \(NetworkService.shared.baseURL) · Admin web: \(NetworkService.shared.adminWebBaseURL)"
    }

    private func reset() {
        nestBaseURL = ""
        adminWebBaseURLField = ""
        visionBaseURL = ""
        UserDefaults.standard.removeObject(forKey: NetworkService.apiBaseURLUserDefaultsKey)
        UserDefaults.standard.removeObject(forKey: NetworkService.adminWebBaseURLUserDefaultsKey)
        UserDefaults.standard.removeObject(forKey: NetworkService.underwaterVisionModuleBaseURLKey)
        statusText = "Reset. API: \(NetworkService.shared.baseURL)"
    }
}
#endif

#Preview {
    NavigationView {
        LanguageSettingsView()
    }
}
