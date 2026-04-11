//
//  CheckoutView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct CheckoutView: View {
    let items: [GearItem]
    @ObservedObject var viewModel: InventoryViewModel
    @Environment(\.dismiss) var dismiss
    
    @State private var issuedToType: Checkout.IssuedToType = .client
    @State private var issuedToId: String = ""
    @State private var issuedToName: String = ""
    @State private var dueDate: Date = Calendar.current.date(byAdding: .day, value: 1, to: Date()) ?? Date()
    @State private var notes: String = ""
    @State private var depositAmount: String = ""
    @State private var itemConditions: [String: Checkout.ItemCondition] = [:]
    @State private var signature: UIImage?
    @State private var showSignaturePad = false
    @State private var currentStep = 0
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Progress indicator
                ProgressView(value: Double(currentStep), total: 3)
                    .padding()
                
                TabView(selection: $currentStep) {
                    // Step 1: Select recipient
                    selectRecipientStep
                        .tag(0)
                    
                    // Step 2: Item conditions
                    itemConditionsStep
                        .tag(1)
                    
                    // Step 3: Review and signature
                    reviewAndSignatureStep
                        .tag(2)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                
                // Navigation buttons
                HStack {
                    if currentStep > 0 {
                        Button("Previous") {
                            withAnimation {
                                currentStep -= 1
                            }
                        }
                    }
                    
                    Spacer()
                    
                    Button(currentStep == 2 ? "Complete" : "Next") {
                        if currentStep == 2 {
                            completeCheckout()
                        } else {
                            withAnimation {
                                currentStep += 1
                            }
                        }
                    }
                    .disabled(!canProceed)
                }
                .padding()
            }
            .navigationTitle("Check Out Equipment")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
            .onAppear {
                initializeItemConditions()
            }
        }
    }
    
    // MARK: - Step 1: Select Recipient
    private var selectRecipientStep: some View {
        Form {
            Section("Recipient Type") {
                Picker("Type", selection: $issuedToType) {
                    Text("Client").tag(Checkout.IssuedToType.client)
                    Text("Instructor").tag(Checkout.IssuedToType.instructor)
                    Text("Employee").tag(Checkout.IssuedToType.employee)
                }
            }
            
            Section("Recipient") {
                TextField("Name", text: $issuedToName)
                // TODO: Add search/select for existing users
            }
            
            Section("Rental Details") {
                DatePicker("Due Date", selection: $dueDate, displayedComponents: [.date, .hourAndMinute])
                
                TextField("Deposit Amount (optional)", text: $depositAmount)
                    .keyboardType(.decimalPad)
            }
            
            Section("Notes") {
                TextEditor(text: $notes)
                    .frame(height: 100)
            }
        }
    }
    
    // MARK: - Step 2: Item Conditions
    private var itemConditionsStep: some View {
        Form {
            Section("Items to Check Out") {
                ForEach(items) { item in
                    VStack(alignment: .leading, spacing: 12) {
                        Text(item.displayName)
                            .font(.headline)
                        
                        Toggle("Has Scratches", isOn: Binding(
                            get: { itemConditions[item.id]?.hasScratches ?? false },
                            set: { newValue in updateCondition(item.id) { $0.hasScratches = newValue } }
                        ))
                        
                        Toggle("Has Punctures", isOn: Binding(
                            get: { itemConditions[item.id]?.hasPunctures ?? false },
                            set: { newValue in updateCondition(item.id) { $0.hasPunctures = newValue } }
                        ))
                        
                        Toggle("Has Seal Issues", isOn: Binding(
                            get: { itemConditions[item.id]?.hasSealIssues ?? false },
                            set: { newValue in updateCondition(item.id) { $0.hasSealIssues = newValue } }
                        ))
                        
                        TextField("Other Defects", text: Binding(
                            get: { itemConditions[item.id]?.otherDefects ?? "" },
                            set: { newValue in updateCondition(item.id) { $0.otherDefects = newValue } }
                        ))
                    }
                    .padding(.vertical, 8)
                }
            }
        }
    }
    
    // MARK: - Step 3: Review and Signature
    private var reviewAndSignatureStep: some View {
        Form {
            Section("Summary") {
                HStack {
                    Text("Recipient")
                    Spacer()
                    Text(issuedToName)
                }
                
                HStack {
                    Text("Items")
                    Spacer()
                    Text("\(items.count)")
                }
                
                HStack {
                    Text("Due Date")
                    Spacer()
                    Text(formatDate(dueDate))
                }
            }
            
            Section("Signature") {
                if let signature = signature {
                    Image(uiImage: signature)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(height: 150)
                        .border(Color.gray, width: 1)
                }
                
                Button("Add Signature") {
                    showSignaturePad = true
                }
            }
            
            Section {
                Text("By signing, the recipient acknowledges responsibility for the equipment and agrees to return it in the same condition.")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .sheet(isPresented: $showSignaturePad) {
            SimpleSignaturePadView(signature: $signature)
        }
    }
    
    private var canProceed: Bool {
        switch currentStep {
        case 0:
            return !issuedToName.isEmpty
        case 1:
            return true
        case 2:
            return signature != nil
        default:
            return false
        }
    }
    
    private func initializeItemConditions() {
        for item in items {
            itemConditions[item.id] = Checkout.ItemCondition(
                id: UUID().uuidString,
                gearItemId: item.id,
                hasScratches: false,
                hasPunctures: false,
                hasSealIssues: false,
                otherDefects: nil,
                photos: [],
                notes: nil
            )
        }
    }
    
    private func updateCondition(_ itemId: String, _ update: (inout Checkout.ItemCondition) -> Void) {
        if var condition = itemConditions[itemId] {
            update(&condition)
            itemConditions[itemId] = condition
        }
    }
    
    private func completeCheckout() {
        // Create checkout record
        let _ = Checkout(
            id: UUID().uuidString,
            diveCenterId: items.first?.diveCenterId ?? "",
            gearItemIds: items.map { $0.id },
            issuedBy: "", // TODO: Get from auth
            issuedByName: nil,
            issuedTo: issuedToId,
            issuedToName: issuedToName,
            issuedToType: issuedToType,
            bookingId: nil,
            dueDate: dueDate,
            returnedAt: nil,
            status: .open,
            conditionAtIssue: Array(itemConditions.values),
            conditionAtReturn: nil,
            notes: notes.isEmpty ? nil : notes,
            signature: signature?.pngData()?.base64EncodedString(),
            depositAmount: Double(depositAmount),
            depositCurrency: "USD",
            depositReturned: false,
            createdAt: Date(),
            updatedAt: Date()
        )
        
        // TODO: Save checkout and update item statuses
        dismiss()
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}

// MARK: - Signature Pad (using SimpleSignaturePadView from InspectionView)

#Preview {
    CheckoutView(items: [], viewModel: InventoryViewModel())
}
