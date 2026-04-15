//
//  InspectionView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct InspectionView: View {
    let item: GearItem
    @ObservedObject var viewModel: InventoryViewModel
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    
    @State private var selectedTemplate: ChecklistTemplate?
    @State private var checklistItems: [Inspection.ChecklistItem] = []
    @State private var notes: String = ""
    @State private var result: Inspection.InspectionResult = .passed
    @State private var signature: UIImage?
    @State private var showSignaturePad = false
    @State private var currentItemIndex = 0

    private func inv(_ key: String) -> String {
        localizationService.localizedString(key, table: "inventory")
    }
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                if checklistItems.isEmpty {
                    templateSelectionView
                } else {
                    checklistView
                }
            }
            .navigationTitle("\(inv("inspection")): \(item.displayName)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(inv("cancel")) {
                        dismiss()
                    }
                }
            }
        }
    }
    
    // MARK: - Template Selection
    private var templateSelectionView: some View {
        Form {
            Section(inv("selectChecklistTemplate")) {
                if viewModel.checklistTemplates.isEmpty {
                    Text(inv("noTemplatesAvailable"))
                        .foregroundColor(.secondary)
                } else {
                    ForEach(viewModel.checklistTemplates.filter { $0.category == item.category || $0.category == .other }) { template in
                        Button(action: {
                            selectedTemplate = template
                            loadTemplate(template)
                        }) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(template.name)
                                    .font(.headline)
                                    .foregroundColor(.primary)
                                
                                if let description = template.description {
                                    Text(description)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                
                                Text("\(template.items.count) \(inv("items"))")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                }
            }
            
            Section {
                Button(inv("startWithoutTemplate")) {
                    startWithoutTemplate()
                }
            }
        }
    }
    
    // MARK: - Checklist View
    private var checklistView: some View {
        VStack(spacing: 0) {
            // Progress indicator
            if checklistItems.count > 1 {
                ProgressView(value: Double(currentItemIndex + 1), total: Double(checklistItems.count))
                    .padding()
            }
            
            // Checklist items
            TabView(selection: $currentItemIndex) {
                ForEach(Array(checklistItems.enumerated()), id: \.element.id) { index, item in
                    ChecklistItemView(
                        item: item,
                        onUpdate: { updatedItem in
                            checklistItems[index] = updatedItem
                        }
                    )
                    .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            
            // Navigation
            HStack {
                if currentItemIndex > 0 {
                    Button(inv("previous")) {
                        withAnimation {
                            currentItemIndex -= 1
                        }
                    }
                }
                
                Spacer()
                
                if currentItemIndex < checklistItems.count - 1 {
                    Button(inv("next")) {
                        withAnimation {
                            currentItemIndex += 1
                        }
                    }
                } else {
                    Button(inv("complete")) {
                        showCompletionView = true
                    }
                }
            }
            .padding()
            
            // Completion view
            if showCompletionView {
                completionView
            }
        }
    }
    
    @State private var showCompletionView = false
    
    // MARK: - Completion View
    private var completionView: some View {
        Form {
            Section(inv("inspectionResult")) {
                Picker(inv("result"), selection: $result) {
                    Text(inv("passed")).tag(Inspection.InspectionResult.passed)
                    Text(inv("failed")).tag(Inspection.InspectionResult.failed)
                    Text(inv("conditional")).tag(Inspection.InspectionResult.conditional)
                }
            }
            
            Section(inv("notes")) {
                TextEditor(text: $notes)
                    .frame(height: 100)
            }
            
            Section(inv("signature")) {
                if let signature = signature {
                    Image(uiImage: signature)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(height: 150)
                        .border(Color.gray, width: 1)
                }
                
                Button(inv("addSignature")) {
                    showSignaturePad = true
                }
            }
            
            Section {
                Button(inv("saveInspection")) {
                    saveInspection()
                }
                .disabled(signature == nil)
            }
        }
        .sheet(isPresented: $showSignaturePad) {
            SimpleSignaturePadView(signature: $signature)
        }
    }
    
    private func loadTemplate(_ template: ChecklistTemplate) {
        checklistItems = template.items.map { templateItem in
            Inspection.ChecklistItem(
                id: templateItem.id,
                title: templateItem.title,
                description: templateItem.description,
                isRequired: templateItem.isRequired,
                status: .notChecked,
                comment: nil,
                photos: [],
                checkedAt: nil
            )
        }
    }
    
    private func startWithoutTemplate() {
        // Create basic checklist
        checklistItems = [
            Inspection.ChecklistItem(
                id: UUID().uuidString,
                title: inv("visualInspection"),
                description: inv("checkVisibleDamage"),
                isRequired: true,
                status: .notChecked,
                comment: nil,
                photos: [],
                checkedAt: nil
            ),
            Inspection.ChecklistItem(
                id: UUID().uuidString,
                title: inv("functionalityTest"),
                description: inv("testBasicFunctionality"),
                isRequired: true,
                status: .notChecked,
                comment: nil,
                photos: [],
                checkedAt: nil
            )
        ]
    }
    
    private func saveInspection() {
        let _ = Inspection(
            id: UUID().uuidString,
            gearItemId: item.id,
            checklistTemplateId: selectedTemplate?.id,
            performedBy: "", // TODO: Get from auth
            performedByName: nil,
            date: Date(),
            status: .completed,
            result: result,
            notes: notes.isEmpty ? nil : notes,
            checklistItems: checklistItems,
            photos: [],
            signature: signature?.pngData()?.base64EncodedString(),
            nextInspectionDate: calculateNextInspectionDate(),
            createdAt: Date(),
            updatedAt: Date()
        )
        
        // TODO: Save inspection and update item
        dismiss()
    }
    
    private func calculateNextInspectionDate() -> Date? {
        let interval = item.inspectionIntervalDays ?? selectedTemplate?.inspectionIntervalDays ?? 90
        return Calendar.current.date(byAdding: .day, value: interval, to: Date())
    }
}

// MARK: - Checklist Item View
struct ChecklistItemView: View {
    let item: Inspection.ChecklistItem
    let onUpdate: (Inspection.ChecklistItem) -> Void
    
    @State private var status: Inspection.ChecklistItem.ChecklistItemStatus = .notChecked
    @State private var comment: String = ""
    @StateObject private var localizationService = LocalizationService.shared

    private func inv(_ key: String) -> String {
        localizationService.localizedString(key, table: "inventory")
    }
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(item.title)
                    .font(.title2)
                    .fontWeight(.bold)
                
                if let description = item.description {
                    Text(description)
                        .font(.body)
                        .foregroundColor(.secondary)
                }
                
                if item.isRequired {
                    Text(inv("required"))
                        .font(.caption)
                        .foregroundColor(.red)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.red.opacity(0.1))
                        .cornerRadius(8)
                }
                
                Divider()
                
                Text(inv("status"))
                    .font(.headline)
                
                Picker(inv("status"), selection: $status) {
                    Text(inv("notChecked")).tag(Inspection.ChecklistItem.ChecklistItemStatus.notChecked)
                    Text(inv("passed")).tag(Inspection.ChecklistItem.ChecklistItemStatus.passed)
                    Text(inv("failed")).tag(Inspection.ChecklistItem.ChecklistItemStatus.failed)
                    Text(inv("na")).tag(Inspection.ChecklistItem.ChecklistItemStatus.notApplicable)
                }
                .pickerStyle(.segmented)
                .onChange(of: status) { oldValue, newValue in
                    updateItem()
                }
                
                if status != .notChecked && status != .notApplicable {
                    Text(inv("comment"))
                        .font(.headline)
                    
                    TextEditor(text: $comment)
                        .frame(height: 100)
                        .border(Color.gray, width: 1)
                        .onChange(of: comment) { oldValue, newValue in
                            updateItem()
                        }
                }
            }
            .padding()
        }
        .onAppear {
            status = item.status
            comment = item.comment ?? ""
        }
    }
    
    private func updateItem() {
        var updated = item
        updated.status = status
        updated.comment = comment.isEmpty ? nil : comment
        if status != .notChecked {
            updated.checkedAt = Date()
        }
        onUpdate(updated)
    }
}

// MARK: - Simple Signature Pad
struct SimpleSignaturePadView: View {
    @Binding var signature: UIImage?
    @Environment(\.dismiss) var dismiss
    @State private var path = Path()
    @StateObject private var localizationService = LocalizationService.shared

    private func inv(_ key: String) -> String {
        localizationService.localizedString(key, table: "inventory")
    }
    
    var body: some View {
        NavigationView {
            VStack {
                Canvas { context, size in
                    context.stroke(path, with: .color(.black), lineWidth: 2)
                }
                .frame(height: 300)
                .background(Color.white)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            path.addLine(to: value.location)
                        }
                )
                
                HStack {
                    Button(inv("clear")) {
                        path = Path()
                    }
                    
                    Spacer()
                    
                    Button(inv("cancel")) {
                        dismiss()
                    }
                    
                    Button(inv("done")) {
                        // TODO: Convert path to image
                        dismiss()
                    }
                }
                .padding()
            }
            .navigationTitle(inv("signature"))
        }
    }
}

#Preview {
    let viewModel = InventoryViewModel()
    let sampleItem = GearItem(
        id: "1",
        diveCenterId: "dc1",
        name: "Regulator",
        category: .regulator,
        status: .available,
        condition: .good
    )
    return InspectionView(item: sampleItem, viewModel: viewModel)
}
