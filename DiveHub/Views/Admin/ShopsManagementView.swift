//
//  ShopsManagementView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import Combine

struct ShopsManagementView: View {
    @StateObject private var viewModel = ShopsAdminViewModel()
    @StateObject private var localizationService = LocalizationService.shared
    @State private var showingAddShop = false
    @State private var selectedShop: Shop?
    @State private var searchText = ""
    
    var filteredShops: [Shop] {
        if searchText.isEmpty {
            return viewModel.shops
        }
        return viewModel.shops.filter { shop in
            shop.name.localizedCaseInsensitiveContains(searchText) ||
            shop.description.localizedCaseInsensitiveContains(searchText) ||
            shop.brands.joined(separator: " ").localizedCaseInsensitiveContains(searchText)
        }
    }
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                if viewModel.isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if viewModel.shops.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "storefront")
                            .font(.system(size: 60))
                            .foregroundColor(.gray)
                        Text("ui_admin_no_shops_found".localized)
                            .font(.headline)
                            .foregroundColor(.gray)
                        Button("ui_add_first_shop".localized) {
                            showingAddShop = true
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        ForEach(filteredShops) { shop in
                            ShopRowView(shop: shop)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    selectedShop = shop
                                }
                        }
                        .onDelete(perform: deleteShops)
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("ui_admin_shops_management".localized)
            .diveHubNavigationChrome()
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showingAddShop = true }) {
                        Image(systemName: "plus.circle.fill")
                            .font(.title3)
                    }
                }
            }
            .searchable(text: $searchText, prompt: "Search shops")
            .sheet(isPresented: $showingAddShop) {
                AddEditShopView(shop: nil) { shop in
                    Task {
                        await viewModel.createShop(shop)
                    }
                }
            }
            .sheet(item: $selectedShop) { shop in
                AddEditShopView(shop: shop) { updatedShop in
                    Task {
                        await viewModel.updateShop(shop.id, updatedShop)
                    }
                }
            }
            .onAppear {
                Task {
                    await viewModel.loadShops()
                }
            }
        }
    }
    
    private func deleteShops(at offsets: IndexSet) {
        for index in offsets {
            let shop = filteredShops[index]
            Task {
                await viewModel.deleteShop(shop.id)
            }
        }
    }
}

struct ShopRowView: View {
    let shop: Shop
    
    var body: some View {
        HStack(spacing: 12) {
            // Shop image
            if let firstPhoto = shop.photos.first, !firstPhoto.isEmpty {
                AsyncImage(url: URL(string: firstPhoto)) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } placeholder: {
                    Rectangle()
                        .fill(Color.gray.opacity(0.3))
                }
                .frame(width: 60, height: 60)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            } else {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.gray.opacity(0.3))
                    .frame(width: 60, height: 60)
                    .overlay(
                        Image(systemName: "storefront")
                            .foregroundColor(.gray)
                    )
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text(shop.displayName)
                    .font(.headline)
                
                Text(shop.type.displayName)
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                if !shop.brands.isEmpty {
                    Text(shop.brands.prefix(3).joined(separator: ", "))
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
                
                HStack {
                    if shop.averageRating > 0 {
                        Label("\(String(format: "%.1f", shop.averageRating))", systemImage: "star.fill")
                            .font(.caption)
                            .foregroundColor(.orange)
                    }
                    
                    if let city = shop.location.city {
                        Label(city, systemImage: "location")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
            
            Spacer()
            
            if shop.serviceAvailable {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundColor(.green)
            }
        }
        .padding(.vertical, 4)
    }
}

struct AddEditShopView: View {
    let shop: Shop?
    let onSave: (Shop) -> Void
    
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var description = ""
    @State private var type: ShopType = .offline
    @State private var brands: [String] = []
    @State private var newBrand = ""
    @State private var serviceAvailable = false
    @State private var latitude: Double = 0
    @State private var longitude: Double = 0
    @State private var country = ""
    @State private var city = ""
    @State private var address = ""
    @State private var email = ""
    @State private var phone = ""
    @State private var website = ""
    @State private var photoUrls: [String] = []
    @State private var newPhotoUrl = ""
    
    var body: some View {
        NavigationView {
            Form {
                Section("ui_basic_information".localized) {
                    TextField("ui_admin_name".localized, text: $name)
                    TextField("ui_admin_description".localized, text: $description, axis: .vertical)
                        .lineLimit(3...6)
                    
                    Picker("ui_admin_type".localized, selection: $type) {
                        ForEach(ShopType.allCases, id: \.self) { shopType in
                            Text(shopType.displayName).tag(shopType)
                        }
                    }
                    
                    Toggle("ui_admin_service_available".localized, isOn: $serviceAvailable)
                }
                
                Section("ui_brands".localized) {
                    ForEach(brands, id: \.self) { brand in
                        Text(brand)
                    }
                    .onDelete { indexSet in
                        brands.remove(atOffsets: indexSet)
                    }
                    
                    HStack {
                        TextField("ui_admin_add_brand".localized, text: $newBrand)
                        Button("ui_add".localized) {
                            if !newBrand.isEmpty {
                                brands.append(newBrand)
                                newBrand = ""
                            }
                        }
                    }
                }
                
                Section("ui_admin_location".localized) {
                    TextField("ui_admin_country".localized, text: $country)
                    TextField("ui_admin_city".localized, text: $city)
                    TextField("ui_admin_address".localized, text: $address)
                    TextField("ui_admin_latitude".localized, value: $latitude, format: .number)
                    TextField("ui_admin_longitude".localized, value: $longitude, format: .number)
                }
                
                Section("ui_contact".localized) {
                    TextField("ui_admin_email".localized, text: $email)
                        .keyboardType(.emailAddress)
                        .autocapitalization(.none)
                    TextField("ui_admin_phone".localized, text: $phone)
                        .keyboardType(.phonePad)
                    TextField("ui_admin_website".localized, text: $website)
                        .keyboardType(.URL)
                        .autocapitalization(.none)
                }
                
                Section("ui_photos".localized) {
                    ForEach(photoUrls, id: \.self) { url in
                        Text(url)
                            .font(.caption)
                    }
                    .onDelete { indexSet in
                        photoUrls.remove(atOffsets: indexSet)
                    }
                    
                    HStack {
                        TextField("ui_admin_photo_url".localized, text: $newPhotoUrl)
                            .keyboardType(.URL)
                            .autocapitalization(.none)
                        Button("ui_add".localized) {
                            if !newPhotoUrl.isEmpty {
                                photoUrls.append(newPhotoUrl)
                                newPhotoUrl = ""
                            }
                        }
                    }
                }
            }
            .navigationTitle(shop == nil ? "Add Shop" : "Edit Shop")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("ui_cancel".localized) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("ui_save".localized) {
                        saveShop()
                    }
                    .disabled(name.isEmpty)
                }
            }
            .onAppear {
                if let shop = shop {
                    loadShopData(shop)
                }
            }
        }
    }
    
    private func loadShopData(_ shop: Shop) {
        name = shop.name
        description = shop.description
        type = shop.type
        brands = shop.brands
        serviceAvailable = shop.serviceAvailable
        latitude = shop.location.latitude
        longitude = shop.location.longitude
        country = shop.location.country ?? ""
        city = shop.location.city ?? ""
        address = shop.location.address ?? ""
        email = shop.contactInfo?.email ?? ""
        phone = shop.contactInfo?.phone ?? ""
        website = shop.contactInfo?.website ?? ""
        photoUrls = shop.photos
    }
    
    private func saveShop() {
        var updatedShop = shop ?? Shop(
            id: UUID().uuidString,
            name: name,
            description: description,
            localizedName: nil,
            localizedDescription: nil,
            type: type,
            brands: brands,
            serviceAvailable: serviceAvailable,
            averageRating: 0,
            reviewCount: 0,
            location: Shop.Location(
                latitude: latitude,
                longitude: longitude,
                address: address.isEmpty ? nil : address,
                city: city.isEmpty ? nil : city,
                country: country.isEmpty ? nil : country
            ),
            photos: photoUrls,
            contactInfo: Shop.ContactInfo(
                phone: phone.isEmpty ? nil : phone,
                email: email.isEmpty ? nil : email,
                website: website.isEmpty ? nil : website
            ),
            createdAt: Date(),
            updatedAt: Date()
        )
        
        updatedShop.name = name
        updatedShop.description = description
        updatedShop.type = type
        updatedShop.brands = brands
        updatedShop.serviceAvailable = serviceAvailable
        updatedShop.location.latitude = latitude
        updatedShop.location.longitude = longitude
        updatedShop.location.country = country.isEmpty ? nil : country
        updatedShop.location.city = city.isEmpty ? nil : city
        updatedShop.location.address = address.isEmpty ? nil : address
        updatedShop.photos = photoUrls
        updatedShop.contactInfo = Shop.ContactInfo(
            phone: phone.isEmpty ? nil : phone,
            email: email.isEmpty ? nil : email,
            website: website.isEmpty ? nil : website
        )
        
        onSave(updatedShop)
        dismiss()
    }
}

// ViewModel for shops admin
@MainActor
class ShopsAdminViewModel: ObservableObject {
    @Published var shops: [Shop] = []
    @Published var isLoading = false
    @Published var error: String?
    
    func loadShops() async {
        isLoading = true
        error = nil
        
        do {
            struct ShopsResponse: Codable {
                let success: Bool
                let data: [Shop]
            }
            
            let response: ShopsResponse = try await NetworkService.shared.request(
                endpoint: "/api/v1/shops",
                method: .get
            )
            
            shops = response.data
        } catch let err {
            error = err.localizedDescription
            print("Error loading shops: \(err.localizedDescription)")
        }
        
        isLoading = false
    }
    
    func createShop(_ shop: Shop) async {
        do {
            struct CreateShopRequest: Codable {
                let name: String
                let description: String
                let type: String
                let brands: [String]
                let serviceAvailable: Bool
                let latitude: Double?
                let longitude: Double?
                let country: String?
                let city: String?
                let address: String?
                let email: String?
                let phone: String?
                let website: String?
                let photoUrls: [String]
            }
            
            let request = CreateShopRequest(
                name: shop.name,
                description: shop.description,
                type: shop.type.rawValue,
                brands: shop.brands,
                serviceAvailable: shop.serviceAvailable,
                latitude: shop.location.latitude,
                longitude: shop.location.longitude,
                country: shop.location.country,
                city: shop.location.city,
                address: shop.location.address,
                email: shop.contactInfo?.email,
                phone: shop.contactInfo?.phone,
                website: shop.contactInfo?.website,
                photoUrls: shop.photos
            )
            
            struct ShopResponse: Codable {
                let success: Bool
                let data: Shop
            }
            
            let _: ShopResponse = try await NetworkService.shared.request(
                endpoint: "/api/v1/shops",
                method: .post,
                body: request
            )
            
            await loadShops()
        } catch let err {
            error = err.localizedDescription
            print("Error creating shop: \(err.localizedDescription)")
        }
    }
    
    func updateShop(_ id: String, _ shop: Shop) async {
        do {
            struct UpdateShopRequest: Codable {
                let name: String?
                let description: String?
                let type: String?
                let brands: [String]?
                let serviceAvailable: Bool?
                let latitude: Double?
                let longitude: Double?
                let country: String?
                let city: String?
                let address: String?
                let email: String?
                let phone: String?
                let website: String?
                let photoUrls: [String]?
            }
            
            let request = UpdateShopRequest(
                name: shop.name,
                description: shop.description,
                type: shop.type.rawValue,
                brands: shop.brands,
                serviceAvailable: shop.serviceAvailable,
                latitude: shop.location.latitude,
                longitude: shop.location.longitude,
                country: shop.location.country,
                city: shop.location.city,
                address: shop.location.address,
                email: shop.contactInfo?.email,
                phone: shop.contactInfo?.phone,
                website: shop.contactInfo?.website,
                photoUrls: shop.photos
            )
            
            struct ShopResponse: Codable {
                let success: Bool
                let data: Shop
            }
            
            let _: ShopResponse = try await NetworkService.shared.request(
                endpoint: "/api/v1/shops/\(id)",
                method: .put,
                body: request
            )
            
            await loadShops()
        } catch let err {
            error = err.localizedDescription
            print("Error updating shop: \(err.localizedDescription)")
        }
    }
    
    func deleteShop(_ id: String) async {
        do {
            let _: [String: String] = try await NetworkService.shared.request(
                endpoint: "/api/v1/shops/\(id)",
                method: .delete
            )
            
            await loadShops()
        } catch let err {
            error = err.localizedDescription
            print("Error deleting shop: \(err.localizedDescription)")
        }
    }
}

#Preview {
    ShopsManagementView()
}
