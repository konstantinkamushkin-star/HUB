//
//  CreatePostView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import PhotosUI
import Combine
import UIKit

private struct CreatePostDiveEditorImage: Identifiable {
    let id = UUID()
    let image: UIImage
}

struct CreatePostView: View {
    @Environment(\.dismiss) var dismiss
    @StateObject private var viewModel = CreatePostViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @AppStorage(FeatureFlags.underwaterEditorKey) private var diveEditorEnabled = true
    @AppStorage("create_post_draft_text") private var draftText = ""
    @AppStorage("create_post_draft_dive_id") private var draftDiveId = ""
    @State private var diveEditorSheet: CreatePostDiveEditorImage?
    @State private var content = ""
    @State private var selectedPhotos: [PhotosPickerItem] = []
    @State private var selectedPhotoImages: [UIImage] = []
    @State private var showDiveLogPicker = false
    @State private var diveLogs: [DiveLog] = []
    @State private var selectedDiveLog: DiveLog?
    @State private var restoreDraftDiveAfterLoad = false
    
    private let quickTags = ["#wreck", "#reef", "#nightdive", "#deep"]
    
    private var canPost: Bool {
        !content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !selectedPhotoImages.isEmpty || selectedDiveLog != nil
    }
    
    private var placeholderText: String {
        selectedDiveLog == nil ? "Share your dive experience..." : "Tell about this dive..."
    }
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Text block
                    VStack(alignment: .leading, spacing: 10) {
                        ZStack(alignment: .topLeading) {
                            TextField("", text: $content, axis: .vertical)
                                .lineLimit(4...14)
                                .font(.body)
                            if content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                Text(placeholderText)
                                    .foregroundColor(.secondary)
                                    .padding(.top, 8)
                                    .allowsHitTesting(false)
                            }
                        }
                        
                        if let selectedDiveLog {
                            HStack(spacing: 8) {
                                Image(systemName: "info.circle")
                                    .foregroundColor(.secondary)
                                Text("Add dive details?")
                                    .font(.footnote)
                                    .foregroundColor(.secondary)
                                Spacer()
                                Text(selectedDiveLog.location.name)
                                    .font(.footnote)
                                    .foregroundColor(.secondary)
                            }
                        } else if !selectedPhotoImages.isEmpty {
                            HStack(spacing: 8) {
                                Image(systemName: "lightbulb")
                                    .foregroundColor(.secondary)
                                Text("Add dive details?")
                                    .font(.footnote)
                                    .foregroundColor(.secondary)
                            }
                        }
                        
                        // Quick tags (killer feature)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(quickTags, id: \.self) { tag in
                                    Button(tag) {
                                        appendTag(tag)
                                    }
                                    .font(.caption)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(Color.diveBackground)
                                    .cornerRadius(12)
                                }
                            }
                        }
                    }
                    
                    Divider()
                    
                    // Photos block (dynamic)
                    if selectedPhotoImages.isEmpty {
                        PhotosPicker(selection: $selectedPhotos, maxSelectionCount: 10, matching: .images) {
                            Label("+ Add photos", systemImage: "plus")
                                .fontWeight(.semibold)
                        }
                        .onChange(of: selectedPhotos) { _, newItems in
                            Task { await loadSelectedImages(newItems) }
                        }
                    } else {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(Array(selectedPhotoImages.enumerated()), id: \.offset) { index, image in
                                    ZStack(alignment: .bottomLeading) {
                                        Image(uiImage: image)
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                            .frame(width: 110, height: 110)
                                            .clipShape(RoundedRectangle(cornerRadius: 10))
                                        if diveEditorEnabled {
                                            Button {
                                                diveEditorSheet = CreatePostDiveEditorImage(image: image)
                                            } label: {
                                                Label(localizationService.localizedString("edit", table: "common"), systemImage: "camera.filters")
                                                    .font(.caption2.weight(.semibold))
                                                    .padding(6)
                                                    .background(.ultraThinMaterial)
                                                    .cornerRadius(6)
                                            }
                                            .padding(6)
                                        }
                                    }
                                    .overlay(alignment: .topTrailing) {
                                        Button(action: { removePhoto(at: index) }) {
                                            Image(systemName: "xmark.circle.fill")
                                                .foregroundColor(.white)
                                                .background(Color.black.opacity(0.6))
                                                .clipShape(Circle())
                                        }
                                        .padding(4)
                                    }
                                }
                                
                                PhotosPicker(selection: $selectedPhotos, maxSelectionCount: 10, matching: .images) {
                                    ZStack {
                                        RoundedRectangle(cornerRadius: 10)
                                            .stroke(style: StrokeStyle(lineWidth: 1, dash: [4]))
                                            .frame(width: 110, height: 110)
                                        Image(systemName: "plus")
                                            .font(.title3)
                                    }
                                }
                                .onChange(of: selectedPhotos) { _, newItems in
                                    Task { await loadSelectedImages(newItems) }
                                }
                            }
                        }
                    }
                    
                    Divider()
                    
                    // Dive block (dynamic)
                    if let dive = selectedDiveLog {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Dive: \(dive.location.name.isEmpty ? "Unknown site" : dive.location.name)")
                                .font(.headline)
                            Text("Depth: \(Int(dive.maxDepth))m")
                                .font(.subheadline)
                            Text("Time: \(dive.bottomTime) min")
                                .font(.subheadline)
                            Text("📍 \(dive.location.name.isEmpty ? "Unknown" : dive.location.name)")
                                .font(.subheadline)
                            
                            HStack(spacing: 16) {
                                Button("Edit") {
                                    showDiveLogPicker = true
                                }
                                Button("Remove") {
                                    selectedDiveLog = nil
                                    draftDiveId = ""
                                }
                                .foregroundColor(.red)
                            }
                        }
                        .padding()
                        .background(Color.diveBackground)
                        .cornerRadius(12)
                        
                        if let temperature = dive.waterTemperature {
                            Text("Water: \(Int(temperature))°C")
                                .font(.footnote)
                                .foregroundColor(.secondary)
                        }
                    } else {
                        Button(action: { showDiveLogPicker = true }) {
                            Label("+ Add dive", systemImage: "plus")
                                .fontWeight(.semibold)
                        }
                    }
                    
                    Divider()
                    
                    // Optional preview
                    if canPost {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Preview post")
                                .font(.headline)
                            if !content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                Text(content)
                                    .font(.subheadline)
                                    .lineLimit(3)
                            }
                            HStack(spacing: 10) {
                                if !selectedPhotoImages.isEmpty {
                                    Text("📷 \(selectedPhotoImages.count)")
                                }
                                if selectedDiveLog != nil {
                                    Text("🤿 Dive attached")
                                }
                            }
                            .font(.caption)
                            .foregroundColor(.secondary)
                        }
                    }
                    
                    Divider()
                    
                    // Key UX buttons under content
                    HStack(spacing: 12) {
                        PhotosPicker(selection: $selectedPhotos, maxSelectionCount: 10, matching: .images) {
                            Label("+ Photo", systemImage: "photo.on.rectangle")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                        .onChange(of: selectedPhotos) { _, newItems in
                            Task { await loadSelectedImages(newItems) }
                        }
                        
                        Button {
                            showDiveLogPicker = true
                        } label: {
                            Label("+ Dive", systemImage: "figure.open.water.swim")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                    }
                }
            }
            .padding()
            .navigationTitle("Create Post")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Post") {
                        Task {
                            let success = await viewModel.createPost(
                                content: content.isEmpty ? nil : content,
                                diveLogId: selectedDiveLog?.id,
                                photos: selectedPhotoImages
                            )
                            if success {
                                clearDraft()
                                dismiss()
                            }
                        }
                    }
                    .disabled(viewModel.isLoading || !canPost)
                }
            }
            .sheet(isPresented: $showDiveLogPicker) {
                DiveLogPickerView(diveLogs: diveLogs, selectedLog: $selectedDiveLog)
            }
            .fullScreenCover(item: $diveEditorSheet) { payload in
                NavigationStack {
                    DiveEditorEditorView(image: payload.image) {
                        diveEditorSheet = nil
                    }
                }
            }
            .task {
                if content.isEmpty {
                    content = draftText
                }
                restoreDraftDiveAfterLoad = !draftDiveId.isEmpty
                await loadDiveLogs()
            }
            .onChange(of: content) { _, value in
                draftText = value
            }
            .onChange(of: selectedDiveLog?.id) { _, value in
                draftDiveId = value ?? ""
            }
        }
    }
    
    private func loadDiveLogs() async {
        guard let userId = AuthenticationService.shared.currentUser?.id else { return }
        do {
            diveLogs = try await NetworkService.shared.getDiveLogs(userId: userId)
            if restoreDraftDiveAfterLoad, let restored = diveLogs.first(where: { $0.id == draftDiveId }) {
                selectedDiveLog = restored
            }
            restoreDraftDiveAfterLoad = false
        } catch {
            // Handle error
        }
    }
    
    private func loadSelectedImages(_ newItems: [PhotosPickerItem]) async {
        var images: [UIImage] = []
        for item in newItems {
            if let data = try? await item.loadTransferable(type: Data.self),
               let image = UIImage(data: data) {
                images.append(image)
            }
        }
        selectedPhotoImages = images
    }
    
    private func removePhoto(at index: Int) {
        selectedPhotoImages.remove(at: index)
        if index < selectedPhotos.count {
            selectedPhotos.remove(at: index)
        }
    }
    
    private func appendTag(_ tag: String) {
        if content.isEmpty {
            content = "\(tag) "
        } else if !content.contains(tag) {
            content += " \(tag)"
            if !content.hasSuffix(" ") {
                content += " "
            }
        }
    }
    
    private func clearDraft() {
        draftText = ""
        draftDiveId = ""
    }
}

struct DiveLogPickerView: View {
    let diveLogs: [DiveLog]
    @Binding var selectedLog: DiveLog?
    @Environment(\.dismiss) var dismiss
    @State private var searchText = ""
    
    var filteredLogs: [DiveLog] {
        if searchText.isEmpty {
            return diveLogs
        }
        return diveLogs.filter { log in
            log.location.name.localizedCaseInsensitiveContains(searchText) ||
            log.notes.localizedCaseInsensitiveContains(searchText)
        }
    }
    
    var body: some View {
        NavigationView {
            List {
                ForEach(filteredLogs) { log in
                    Button(action: {
                        selectedLog = log
                        dismiss()
                    }) {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(log.date.formatted(date: .long, time: .omitted))
                                    .font(.headline)
                                Text(log.location.name)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            if selectedLog?.id == log.id {
                                Image(systemName: "checkmark")
                                    .foregroundColor(.divePrimary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Select Dive Log")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $searchText, prompt: "Search dive logs")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
    }
}

@MainActor
class CreatePostViewModel: ObservableObject {
    @Published var isLoading = false
    var errorMessage: String?
    
    func createPost(content: String?, diveLogId: String?, photos: [UIImage]) async -> Bool {
        guard AuthenticationService.shared.currentUser?.id != nil else {
            errorMessage = "User not logged in"
            return false
        }
        
        let trimmedContent = content?.trimmingCharacters(in: .whitespacesAndNewlines)
        let hasText = !(trimmedContent?.isEmpty ?? true)
        let hasPhotos = !photos.isEmpty
        let hasDive = diveLogId != nil
        guard hasText || hasPhotos || hasDive else {
            errorMessage = "Add text, photo, or dive details"
            return false
        }
        
        let inferredType: FeedPost.PostType = hasDive ? .dive : (hasPhotos ? .photo : .text)
        
        isLoading = true
        
        var photoURLs: [String] = []
        if !photos.isEmpty {
            for (index, image) in photos.enumerated() {
                guard let data = image.jpegData(compressionQuality: 0.85) else { continue }
                do {
                    let url = try await NetworkService.shared.uploadMediaImage(data, fileName: "post_\(index).jpg")
                    photoURLs.append(url)
                } catch {
                    errorMessage = error.localizedDescription
                    isLoading = false
                    return false
                }
            }
        }
        
        do {
            _ = try await NetworkService.shared.createFeedPost(
                type: inferredType,
                content: trimmedContent,
                diveLogId: diveLogId,
                photos: photoURLs
            )
            isLoading = false
            return true
        } catch {
            errorMessage = error.localizedDescription
            isLoading = false
            return false
        }
    }
}
