//
//  BuddyFindView.swift
//  DiveHub
//
//  BUDDIES — Find buddy by place + dates (not tied to trips).
//

import SwiftUI

struct BuddyFindView: View {
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var authService = AuthenticationService.shared

    @State private var place = ""
    @State private var dateFrom = Date()
    @State private var dateTo = Calendar.current.date(byAdding: .day, value: 7, to: Date()) ?? Date()
    @State private var certification = "AOWD"
    @State private var diveCountText = ""
    @State private var languagesText = "Russian / English"
    @State private var interestsText = "sharks / wrecks / photography"

    @State private var matches: [BuddySearchMatch] = []
    @State private var matchCount = 0
    @State private var didSearch = false
    @State private var isSaving = false
    @State private var errorText: String?
    @State private var chatConversation: ChatConversation?

    private let certifications = ["OWD", "AOWD", "Rescue", "DM", "Instructor", "Tech"]

    private let iso: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    private var isRussian: Bool {
        localizationService.currentLanguage == .russian
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text(isRussian
                         ? "Заполните короткую анкету и найдите, с кем поехать нырять. Совпадение — по месту и датам, без привязки к поездкам."
                         : "Fill a short form and find someone to dive with. Matches are by place and dates — not tied to trips.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                Section(isRussian ? "Анкета" : "Your plan") {
                    TextField(isRussian ? "Место (например, Phuket)" : "Place (e.g. Phuket)", text: $place)
                        .textInputAutocapitalization(.words)

                    DatePicker(
                        isRussian ? "С" : "From",
                        selection: $dateFrom,
                        displayedComponents: .date
                    )
                    DatePicker(
                        isRussian ? "По" : "To",
                        selection: $dateTo,
                        displayedComponents: .date
                    )

                    Picker(isRussian ? "Сертификация" : "Certification", selection: $certification) {
                        ForEach(certifications, id: \.self) { Text($0).tag($0) }
                    }

                    TextField(isRussian ? "Число дайвов" : "Dive count", text: $diveCountText)
                        .keyboardType(.numberPad)

                    TextField(isRussian ? "Языки" : "Languages", text: $languagesText)
                    TextField(isRussian ? "Интересы" : "Interests", text: $interestsText)
                }

                Section {
                    Button {
                        Task { await findBuddy() }
                    } label: {
                        if isSaving {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                        } else {
                            Text(isRussian ? "Найти бадди" : "Find buddy")
                                .fontWeight(.semibold)
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .disabled(isSaving || place.trimmingCharacters(in: .whitespacesAndNewlines).count < 2)
                }

                if let errorText {
                    Section {
                        Text(errorText.isEmpty
                             ? (isRussian ? "Не удалось загрузить совпадения. Проверьте сеть и API." : "Could not load matches. Check network and API.")
                             : errorText)
                            .foregroundStyle(.red)
                            .font(.footnote)
                    }
                }

                if didSearch {
                    Section {
                        Text(
                            isRussian
                                ? "\(matchCount) дайверов совпали с твоими местом и датами"
                                : "\(matchCount) divers match your place & dates"
                        )
                        .font(.headline)

                        if matches.isEmpty {
                            Text(isRussian
                                 ? "Пока никого с пересечением места и дат. Твоя анкета сохранена — другие увидят тебя."
                                 : "No overlapping place+time yet. Your plan is saved — others can match you.")
                                .foregroundStyle(.secondary)
                                .font(.subheadline)
                        }
                    }

                    ForEach(matches) { match in
                        Section {
                            BuddyMatchCard(match: match, isRussian: isRussian) {
                                Task { await openChat(with: match) }
                            }
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle(isRussian ? "Бадди" : "Buddies")
            .navigationBarTitleDisplayMode(.large)
            .diveHubNavigationChrome()
            .toolbar {
                if didSearch {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button(isRussian ? "Сбросить" : "Clear") {
                            Task { await clearSearch() }
                        }
                    }
                }
            }
            .task {
                await loadExisting()
            }
            .sheet(item: $chatConversation) { conversation in
                NavigationStack {
                    ChatDetailView(conversation: conversation)
                }
            }
            .onAppear {
                prefillFromProfile()
            }
        }
        .id(localizationService.currentLanguage)
    }

    private func prefillFromProfile() {
        guard let user = authService.currentUser else { return }
        if diveCountText.isEmpty, let dives = user.totalDives, dives > 0 {
            diveCountText = "\(dives)"
        }
        if let cert = user.certificationLevel ?? user.diverProfile?.certificationLevel,
           !cert.isEmpty,
           certifications.contains(where: { $0.caseInsensitiveCompare(cert) == .orderedSame || cert.uppercased().contains($0) }) {
            // keep picker on closest
            if let hit = certifications.first(where: { cert.uppercased().contains($0) }) {
                certification = hit
            }
        }
        if let langs = user.diverProfile?.languagesSpoken, !langs.isEmpty, languagesText == "Russian / English" {
            languagesText = langs.joined(separator: " / ")
        }
        if let interests = user.diverProfile?.diveInterests, !interests.isEmpty, interestsText.contains("sharks") {
            interestsText = interests.prefix(4).joined(separator: " / ").lowercased()
        }
        if place.isEmpty, let city = user.diverProfile?.city, !city.isEmpty {
            place = city
        }
    }

    private func parseList(_ raw: String) -> [String] {
        raw
            .split(whereSeparator: { "/,;|".contains($0) })
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    private func findBuddy() async {
        isSaving = true
        errorText = nil
        defer { isSaving = false }
        let diveCount = Int(diveCountText.trimmingCharacters(in: .whitespacesAndNewlines))
        do {
            let response = try await NetworkService.shared.upsertBuddySearch(
                place: place.trimmingCharacters(in: .whitespacesAndNewlines),
                dateFrom: iso.string(from: dateFrom),
                dateTo: iso.string(from: max(dateFrom, dateTo)),
                certificationLevel: certification,
                diveCount: diveCount,
                languages: parseList(languagesText),
                interests: parseList(interestsText)
            )
            matchCount = response.matchCount
            matches = response.matches
            didSearch = true
        } catch {
            errorText = friendlyError(error)
        }
    }

    private func loadExisting() async {
        do {
            if let mine = try await NetworkService.shared.getMyBuddySearch() {
                place = mine.place
                if let from = iso.date(from: mine.dateFrom) { dateFrom = from }
                if let to = iso.date(from: mine.dateTo) { dateTo = to }
                if let cert = mine.certificationLevel, !cert.isEmpty {
                    certification = cert
                }
                if let dives = mine.diveCount {
                    diveCountText = "\(dives)"
                }
                if !mine.languages.isEmpty {
                    languagesText = mine.languages.joined(separator: " / ")
                }
                if !mine.interests.isEmpty {
                    interestsText = mine.interests.joined(separator: " / ")
                }
                let listed = try await NetworkService.shared.listBuddySearchMatches()
                matchCount = listed.matchCount
                matches = listed.matches
                didSearch = true
            }
        } catch {
            // No open search yet is fine; surface real API failures only.
            if let net = error as? NetworkError {
                switch net {
                case .serverError(404), .serverErrorWithDetail(404, _):
                    return
                default:
                    errorText = friendlyError(error)
                }
            }
        }
    }

    private func clearSearch() async {
        do {
            try await NetworkService.shared.closeBuddySearch()
            didSearch = false
            matches = []
            matchCount = 0
        } catch {
            errorText = friendlyError(error)
        }
    }

    private func openChat(with match: BuddySearchMatch) async {
        let peerId = match.user?.id ?? match.search.userId
        do {
            chatConversation = try await NetworkService.shared.openChatConversation(
                peerType: "user",
                peerId: peerId
            )
        } catch {
            errorText = friendlyError(error)
        }
    }

    private func friendlyError(_ error: Error) -> String {
        let raw = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed == "Ошибка" || trimmed.lowercased() == "error" {
            return isRussian
                ? "Не удалось выполнить запрос. Проверьте интернет и что API доступен."
                : "Request failed. Check internet and that the API is reachable."
        }
        return trimmed
    }
}

private struct BuddyMatchCard: View {
    let match: BuddySearchMatch
    let isRussian: Bool
    let onMessage: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(match.user?.displayName ?? "Diver")
                        .font(.headline)
                    Text(match.search.place)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.accentColor)
                    Text(match.search.dateRangeLabel)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button(isRussian ? "Написать" : "Message", action: onMessage)
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)
            }

            HStack(spacing: 10) {
                if let cert = match.search.certificationLevel ?? match.user?.certificationLevel {
                    labelChip(cert)
                }
                if let dives = match.search.diveCount ?? match.user?.totalDives {
                    labelChip("\(dives) dives")
                }
            }

            if !match.search.languages.isEmpty {
                Text("Languages: \(match.search.languages.joined(separator: " / "))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if !match.search.interests.isEmpty {
                Text("Interested in: \(match.search.interests.joined(separator: " / "))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }

    private func labelChip(_ text: String) -> some View {
        Text(text)
            .font(.caption2.weight(.semibold))
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Color.secondary.opacity(0.15))
            .clipShape(Capsule())
    }
}
