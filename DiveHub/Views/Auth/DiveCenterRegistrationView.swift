//
//  DiveCenterRegistrationView.swift
//  DiveHub
//

import SwiftUI
import MapKit

struct DiveCenterRegistrationView: View {
    @StateObject private var localizationService = LocalizationService.shared
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var descriptionText = ""
    @State private var contactEmail = ""
    @State private var contactPhone = ""
    @State private var country = ""
    @State private var city = ""
    @State private var address = ""
    @State private var website = ""

    /// Точка для API: выбор на карте или после поиска по адресу.
    @State private var pickedCoordinate: CLLocationCoordinate2D?
    @State private var cameraPosition: MapCameraPosition = .region(
        MKCoordinateRegion(
            center: CLLocationCoordinate2D(latitude: 25.0, longitude: -40.0),
            span: MKCoordinateSpan(latitudeDelta: 45, longitudeDelta: 45)
        )
    )

    @State private var isSubmitting = false
    @State private var isGeocoding = false
    @State private var personalDataConsentAccepted = false
    @State private var errorMessage: String?
    @State private var showSuccess = false
    @State private var successDetail: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(localizationService.localizedString("diveCenterRegistrationIntro", table: "auth"))
                        .font(.footnote)
                        .foregroundColor(.secondary)
                        .listRowBackground(Color.clear)
                }

                Section {
                    TextField(localizationService.localizedString("diveCenterLegalName", table: "auth"), text: $name)
                }

                Section {
                    TextField(
                        localizationService.localizedString("diveCenterDescriptionField", table: "auth"),
                        text: $descriptionText,
                        axis: .vertical
                    )
                    .lineLimit(3...6)
                }

                Section(header: Text(localizationService.localizedString("partnerContactSection", table: "auth"))) {
                    TextField(localizationService.localizedString("partnerContactEmail", table: "auth"), text: $contactEmail)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField(localizationService.localizedString("partnerContactPhone", table: "auth"), text: $contactPhone)
                        .keyboardType(.phonePad)
                }

                Section(header: Text(localizationService.localizedString("partnerLocationSection", table: "auth"))) {
                    TextField(localizationService.localizedString("country", table: "common"), text: $country)
                    TextField(localizationService.localizedString("partnerCity", table: "auth"), text: $city)
                    TextField(localizationService.localizedString("partnerAddressFull", table: "auth"), text: $address, axis: .vertical)
                        .lineLimit(2...4)
                }

                Section {
                    TextField(localizationService.localizedString("partnerWebsite", table: "auth"), text: $website)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }

                Section(
                    header: Text(localizationService.localizedString("partnerMapPickSection", table: "auth")),
                    footer: Text(localizationService.localizedString("partnerMapPickHint", table: "auth"))
                ) {
                    MapReader { proxy in
                        Map(position: $cameraPosition) {
                            if let coord = pickedCoordinate {
                                Annotation("", coordinate: coord) {
                                    Image(systemName: "mappin.circle.fill")
                                        .font(.title)
                                        .foregroundStyle(.red)
                                        .background(Circle().fill(.white).padding(-4))
                                }
                            }
                        }
                        .mapStyle(.standard(elevation: .realistic))
                        .frame(height: 220)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .contentShape(Rectangle())
                        .onTapGesture { point in
                            if let coord = proxy.convert(point, from: .local) {
                                pickedCoordinate = coord
                                errorMessage = nil
                            }
                        }
                    }
                    .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))

                    Button {
                        Task { await geocodeFromAddressFields() }
                    } label: {
                        HStack {
                            if isGeocoding {
                                ProgressView()
                            }
                            Text(localizationService.localizedString("partnerFindByAddress", table: "auth"))
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .disabled(isGeocoding)

                    if pickedCoordinate != nil {
                        Text(localizationService.localizedString("partnerLocationSelected", table: "auth"))
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                if let errorMessage {
                    Section {
                        Text(errorMessage)
                            .foregroundColor(.red)
                            .font(.footnote)
                    }
                }

                Section("ui_343n".localized) {
                    Toggle(isOn: $personalDataConsentAccepted) {
                        Text("ui_auth_consent_full_text".localized)
                    }
                    HStack(spacing: 16) {
                        Link("Политика конфиденциальности", destination: ConsentTexts.privacyPolicyURL)
                        Link("Пользовательское соглашение", destination: ConsentTexts.userAgreementURL)
                    }
                    .font(.caption)
                    Text("ui_auth_documents_open_in_browser".localized)
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }

                Section {
                    Button(action: submit) {
                        HStack {
                            if isSubmitting {
                                ProgressView()
                            }
                            Text(localizationService.localizedString("submitPartnerApplication", table: "auth"))
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .disabled(isSubmitting || !isFormValid)
                }
            }
            .navigationTitle(localizationService.localizedString("diveCenterRegistrationTitle", table: "auth"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizationService.localizedString("cancel", table: "common")) {
                        dismiss()
                    }
                }
            }
            .alert(
                localizationService.localizedString("partnerRegistrationSuccessTitle", table: "auth"),
                isPresented: $showSuccess
            ) {
                Button(localizationService.localizedString("ok", table: "common")) {
                    dismiss()
                }
            } message: {
                Text(successDetail ?? localizationService.localizedString("partnerRegistrationSuccessBody", table: "auth"))
            }
        }
    }

    private var isFormValid: Bool {
        guard pickedCoordinate != nil else { return false }
        return !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !contactEmail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !contactPhone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !country.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !city.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && personalDataConsentAccepted
    }

    private func geocodeFromAddressFields() async {
        errorMessage = nil
        let c = country.trimmingCharacters(in: .whitespacesAndNewlines)
        let ct = city.trimmingCharacters(in: .whitespacesAndNewlines)
        let ad = address.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !c.isEmpty, !ct.isEmpty, !ad.isEmpty else {
            errorMessage = localizationService.localizedString("partnerGeocodeMissingFields", table: "auth")
            return
        }

        let query = [ad, ct, c].joined(separator: ", ")
        isGeocoding = true
        defer { isGeocoding = false }

        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = query

        do {
            let response = try await MKLocalSearch(request: request).start()
            guard let item = response.mapItems.first else {
                errorMessage = localizationService.localizedString("partnerGeocodeNoResults", table: "auth")
                return
            }
            let coord = mapItemCoordinate(item)
            guard coord.latitude.isFinite, coord.longitude.isFinite else {
                errorMessage = localizationService.localizedString("partnerGeocodeNoResults", table: "auth")
                return
            }
            pickedCoordinate = coord
            cameraPosition = .region(
                MKCoordinateRegion(
                    center: coord,
                    span: MKCoordinateSpan(latitudeDelta: 0.06, longitudeDelta: 0.06)
                )
            )
        } catch {
            errorMessage = localizationService.localizedString("partnerGeocodeFailed", table: "auth")
        }
    }

    private func submit() {
        errorMessage = nil
        guard let coord = pickedCoordinate else {
            errorMessage = localizationService.localizedString("partnerRegistrationPickLocation", table: "auth")
            return
        }
        let lat = coord.latitude
        let lon = coord.longitude
        guard (-90...90).contains(lat), (-180...180).contains(lon) else {
            errorMessage = localizationService.localizedString("partnerRegistrationPickLocation", table: "auth")
            return
        }

        let nameTrimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let descTrimmed = descriptionText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard personalDataConsentAccepted else {
            errorMessage = "Для отправки заявки нужно согласие на обработку персональных данных."
            return
        }
        let body = PartnerRegistrationRequestBody(
            kind: "dive_center",
            name: nameTrimmed,
            description: descTrimmed.isEmpty ? nil : descTrimmed,
            contactEmail: contactEmail.trimmingCharacters(in: .whitespacesAndNewlines),
            contactPhone: contactPhone.trimmingCharacters(in: .whitespacesAndNewlines),
            country: country.trimmingCharacters(in: .whitespacesAndNewlines),
            city: city.trimmingCharacters(in: .whitespacesAndNewlines),
            address: address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? nil
                : address.trimmingCharacters(in: .whitespacesAndNewlines),
            website: website.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? nil
                : website.trimmingCharacters(in: .whitespacesAndNewlines),
            latitude: lat,
            longitude: lon,
            personalDataConsent: true,
            personalDataConsentText: ConsentTexts.registrationConsentText()
        )

        isSubmitting = true
        Task {
            do {
                let res = try await NetworkService.shared.submitPartnerRegistration(body: body)
                await MainActor.run {
                    isSubmitting = false
                    successDetail = res.message
                    showSuccess = true
                }
            } catch {
                await MainActor.run {
                    isSubmitting = false
                    if let ne = error as? NetworkError {
                        errorMessage = ne.errorDescription
                    } else {
                        errorMessage = error.localizedDescription
                    }
                }
            }
        }
    }

    private func mapItemCoordinate(_ item: MKMapItem) -> CLLocationCoordinate2D {
        if #available(iOS 26.0, *) {
            return item.location.coordinate
        }
        return item.placemark.coordinate
    }
}

#Preview {
    DiveCenterRegistrationView()
}
