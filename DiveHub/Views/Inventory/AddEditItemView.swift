//
//  AddEditItemView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct AddEditItemView: View {
    let item: GearItem?
    @ObservedObject var viewModel: InventoryViewModel
    @Environment(\.dismiss) var dismiss
    @StateObject private var localizationService = LocalizationService.shared
    
    @State private var name: String = ""
    @State private var category: GearItem.GearCategory = .other
    @State private var manufacturer: String = ""
    @State private var model: String = ""
    @State private var size: String = ""
    @State private var serialNumber: String = ""
    @State private var barcode: String = ""
    @State private var status: GearItem.GearStatus = .available
    @State private var condition: GearItem.GearCondition = .good
    @State private var locationId: String = ""
    @State private var responsibleUserId: String = ""
    @State private var description: String = ""
    @State private var notes: String = ""
    @State private var purchaseDate: Date = Date()
    @State private var hasPurchaseDate: Bool = false
    @State private var supplier: String = ""
    @State private var purchasePrice: String = ""
    @State private var productionYear: String = ""
    @State private var maxPressure: String = ""
    @State private var material: String = ""
    @State private var inspectionIntervalDays: String = ""
    @State private var tags: [String] = []
    @State private var newTag: String = ""
    
    @State private var errors: [String: String] = [:]
    @State private var isSaving = false
    
    var isEditMode: Bool {
        item != nil
    }

    private func inv(_ key: String) -> String {
        localizationService.localizedString(key, table: "inventory")
    }
    
    var body: some View {
        NavigationView {
            Form {
                // Basic Information Section
                Section(inv("basicInformation")) {
                    Picker(inv("category"), selection: $category) {
                        ForEach(GearItem.GearCategory.allCases, id: \.self) { cat in
                            Text(cat.displayName).tag(cat)
                        }
                    }
                    
                    TextField(inv("name"), text: $name)
                        .validate(required: true, errors: $errors, key: "name")
                    
                    TextField(inv("manufacturer"), text: $manufacturer)
                    TextField(inv("model"), text: $model)
                    TextField(inv("size"), text: $size)
                    
                    TextField(inv("serialNumber"), text: $serialNumber)
                        .validate(unique: true, existingSerial: serialNumber, items: viewModel.gearItems, errors: $errors, key: "serialNumber")
                    
                    TextField(inv("barcode"), text: $barcode)
                    
                    Picker(inv("status"), selection: $status) {
                        ForEach(GearItem.GearStatus.allCases, id: \.self) { stat in
                            Text(stat.displayName).tag(stat)
                        }
                    }
                    
                    Picker(inv("condition"), selection: $condition) {
                        ForEach(GearItem.GearCondition.allCases, id: \.self) { cond in
                            Text(cond.displayName).tag(cond)
                        }
                    }
                }
                
                // Location & Responsibility
                Section(inv("locationResponsibility")) {
                    Picker(inv("location"), selection: $locationId) {
                        Text(inv("notAssigned")).tag("")
                        ForEach(viewModel.locations.filter { $0.isActive }) { location in
                            Text(location.name).tag(location.id)
                        }
                    }
                    
                    // TODO: Add responsible user picker
                }
                
                // Purchase Information
                Section(inv("purchaseInformation")) {
                    Toggle(inv("hasPurchaseDate"), isOn: $hasPurchaseDate)
                    
                    if hasPurchaseDate {
                        DatePicker(inv("purchaseDate"), selection: $purchaseDate, displayedComponents: .date)
                    }
                    
                    TextField(inv("supplier"), text: $supplier)
                    TextField(inv("purchasePrice"), text: $purchasePrice)
                        .keyboardType(.decimalPad)
                }
                
                // Technical Details
                Section(inv("technicalDetails")) {
                    TextField(inv("productionYear"), text: $productionYear)
                        .keyboardType(.numberPad)
                    
                    TextField(inv("maxPressureBar"), text: $maxPressure)
                        .keyboardType(.decimalPad)
                    
                    TextField(inv("material"), text: $material)
                }
                
                // Inspection Schedule
                Section(inv("inspectionSchedule")) {
                    TextField(inv("inspectionIntervalDays"), text: $inspectionIntervalDays)
                        .keyboardType(.numberPad)
                }
                
                // Tags
                Section(inv("tags")) {
                    ForEach(tags, id: \.self) { tag in
                        HStack {
                            Text(tag)
                            Spacer()
                            Button(action: { tags.removeAll { $0 == tag } }) {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.red)
                            }
                        }
                    }
                    
                    HStack {
                        TextField(inv("addTag"), text: $newTag)
                        Button(inv("add")) {
                            if !newTag.isEmpty && !tags.contains(newTag) {
                                tags.append(newTag)
                                newTag = ""
                            }
                        }
                    }
                }
                
                // Description & Notes
                Section(inv("additionalInformation")) {
                    TextEditor(text: $description)
                        .frame(height: 100)
                    
                    TextEditor(text: $notes)
                        .frame(height: 100)
                }
                
                // Error Display
                if !errors.isEmpty {
                    Section {
                        ForEach(Array(errors.keys), id: \.self) { key in
                            Text(errors[key] ?? "")
                                .foregroundColor(.red)
                                .font(.caption)
                        }
                    }
                }
            }
            .navigationTitle(isEditMode ? inv("editItem") : inv("addItem"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(inv("cancel")) {
                        dismiss()
                    }
                }
                
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(inv("save")) {
                        saveItem()
                    }
                    .disabled(isSaving || !isValid)
                }
            }
            .onAppear {
                if let item = item {
                    loadItem(item)
                }
            }
        }
    }
    
    private var isValid: Bool {
        !name.isEmpty && errors.isEmpty
    }
    
    private func loadItem(_ item: GearItem) {
        name = item.name
        category = item.category
        manufacturer = item.manufacturer ?? ""
        model = item.model ?? ""
        size = item.size ?? ""
        serialNumber = item.serialNumber ?? ""
        barcode = item.barcode ?? ""
        status = item.status
        condition = item.condition
        locationId = item.locationId ?? ""
        description = item.description
        notes = item.notes ?? ""
        supplier = item.supplier ?? ""
        purchasePrice = item.purchasePrice.map { String($0) } ?? ""
        productionYear = item.productionYear.map { String($0) } ?? ""
        maxPressure = item.maxPressure.map { String($0) } ?? ""
        material = item.material ?? ""
        inspectionIntervalDays = item.inspectionIntervalDays.map { String($0) } ?? ""
        tags = item.tags
        
        if let purchaseDate = item.purchaseDate {
            self.purchaseDate = purchaseDate
            hasPurchaseDate = true
        }
    }
    
    private func saveItem() {
        errors.removeAll()
        
        // Validate
        if name.isEmpty {
            errors["name"] = inv("nameRequired")
        }
        
        // Check serial number uniqueness
        if !serialNumber.isEmpty {
            let existing = viewModel.gearItems.first { item in
                item.serialNumber == serialNumber && item.id != self.item?.id
            }
            if existing != nil {
                errors["serialNumber"] = inv("serialAlreadyExists")
            }
        }
        
        if !errors.isEmpty {
            return
        }
        
        isSaving = true
        
        Task {
            do {
                let updatedItem = GearItem(
                    id: item?.id ?? UUID().uuidString,
                    diveCenterId: item?.diveCenterId ?? "", // TODO: Get from auth
                    name: name,
                    description: description,
                    category: category,
                    manufacturer: manufacturer.isEmpty ? nil : manufacturer,
                    model: model.isEmpty ? nil : model,
                    size: size.isEmpty ? nil : size,
                    sizes: [],
                    photos: item?.photos ?? [],
                    status: status,
                    condition: condition,
                    rentalPrice: item?.rentalPrice,
                    maintenance: item?.maintenance,
                    createdAt: item?.createdAt ?? Date(),
                    updatedAt: Date(),
                    serialNumber: serialNumber.isEmpty ? nil : serialNumber,
                    barcode: barcode.isEmpty ? nil : barcode,
                    qrCode: item?.qrCode,
                    locationId: locationId.isEmpty ? nil : locationId,
                    locationName: locationId.isEmpty ? nil : viewModel.locations.first { $0.id == locationId }?.name,
                    responsibleUserId: responsibleUserId.isEmpty ? nil : responsibleUserId,
                    responsibleUserName: nil,
                    lastInspectionDate: item?.lastInspectionDate,
                    nextInspectionDate: item?.nextInspectionDate,
                    inspectionIntervalDays: Int(inspectionIntervalDays),
                    purchaseDate: hasPurchaseDate ? purchaseDate : nil,
                    supplier: supplier.isEmpty ? nil : supplier,
                    purchasePrice: Double(purchasePrice),
                    warrantyExpiresAt: item?.warrantyExpiresAt,
                    productionYear: Int(productionYear),
                    maxPressure: Double(maxPressure),
                    material: material.isEmpty ? nil : material,
                    tags: tags,
                    relatedItemIds: item?.relatedItemIds ?? [],
                    documents: item?.documents ?? [],
                    notes: notes.isEmpty ? nil : notes,
                    isDeleted: false,
                    deletedAt: nil,
                    createdBy: item?.createdBy,
                    insuranceStatus: item?.insuranceStatus
                )
                
                if isEditMode {
                    try await viewModel.updateGearItem(updatedItem)
                } else {
                    try await viewModel.createGearItem(updatedItem)
                }
                
                await MainActor.run {
                    dismiss()
                }
            } catch {
                await MainActor.run {
                    errors["general"] = error.localizedDescription
                    isSaving = false
                }
            }
        }
    }
}

// MARK: - Validation Extension
extension View {
    func validate(required: Bool = false, unique: Bool = false, existingSerial: String = "", items: [GearItem] = [], errors: Binding<[String: String]>, key: String) -> some View {
        self
        // Note: Validation logic should be handled in the view that uses this modifier
        // The onChange(of: self) pattern doesn't work because View doesn't conform to Equatable
    }
}

#Preview {
    AddEditItemView(item: nil, viewModel: InventoryViewModel())
}
