//
//  ImageEditingView.swift
//  DiveHub
//

import SwiftUI

struct ImageEditingView: View {
    @StateObject private var viewModel = UnderwaterPhotoEditorViewModel()
    @State private var showImagePicker = false
    @State private var selectedImage: UIImage?
    @State private var showEditor = false
    @StateObject private var localizationService = LocalizationService.shared

    var body: some View {
        NavigationView {
            Group {
                if selectedImage != nil, showEditor {
                    UnderwaterPhotoEditorView(viewModel: viewModel, onClose: {
                        selectedImage = nil
                        viewModel.setImage(nil)
                        showEditor = false
                    })
                } else {
                    mainContent
                }
            }
            .navigationTitle(localizationService.localizedString("imageEditing"))
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showImagePicker) {
                ImagePicker(selectedImage: $selectedImage)
            }
            .onChange(of: selectedImage) { _, new in
                if let img = new {
                    viewModel.setImage(img)
                    showEditor = true
                }
            }
        }
    }

    private var mainContent: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: "photo.on.rectangle.angled")
                .font(.system(size: 64))
                .foregroundColor(.divePrimary.opacity(0.8))
            Text(localizationService.localizedString("underwaterPhotoEdit", table: "imageEditing"))
                .font(.title2)
                .fontWeight(.semibold)
                .foregroundColor(.diveText)
            Text(localizationService.localizedString("selectPhotoDescription", table: "imageEditing"))
                .font(.subheadline)
                .foregroundColor(.diveTextSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            Button(action: { showImagePicker = true }) {
                Label(localizationService.localizedString("selectPhoto", table: "imageEditing"), systemImage: "photo.badge.plus")
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.divePrimary)
                    .cornerRadius(12)
            }
            .padding(.horizontal, 24)
            .padding(.top, 8)
            Spacer()
        }
        .background(Color.diveBackground)
    }
}

#Preview {
    ImageEditingView()
}
