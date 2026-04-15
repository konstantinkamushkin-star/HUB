//
//  APITestView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI
import UIKit

#if DEBUG
struct APITestView: View {
    @State private var testResults: [TestResult] = []
    @State private var isRunning = false
    
    var body: some View {
        NavigationView {
            List {
                Section("ui_1n34nnn_34_234_n_nest_a_python_onnx".localized) {
                    Text("ui_testing_mac_backend_start_neural_test_stack_sh_a_ai_service_star".localized)
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Button("ui_ai_health_nest".localized) {
                        testUnderwaterAIHealth()
                    }
                    .disabled(isRunning)
                    Button("ui_ai_process_14_12_jpeg_a_nn2n".localized) {
                        testUnderwaterAIProcess()
                    }
                    .disabled(isRunning)
                }
                Section("ui_api_tests".localized) {
                    Button("ui_test_backend_connection".localized) {
                        testBackendConnection()
                    }
                    .disabled(isRunning)
                    
                    Button("ui_test_authentication".localized) {
                        testAuthentication()
                    }
                    .disabled(isRunning)
                    
                    Button("ui_test_get_dive_sites".localized) {
                        testGetDiveSites()
                    }
                    .disabled(isRunning)
                    
                    if isRunning {
                        HStack {
                            ProgressView()
                            Text("ui_testing_testing".localized)
                        }
                    }
                }
                
                Section("ui_results".localized) {
                    if testResults.isEmpty {
                        Text("ui_testing_no_tests_run_yet".localized)
                            .foregroundColor(.secondary)
                    } else {
                        ForEach(testResults) { result in
                            HStack {
                                Image(systemName: result.success ? "checkmark.circle.fill" : "xmark.circle.fill")
                                    .foregroundColor(result.success ? .green : .red)
                                VStack(alignment: .leading) {
                                    Text(result.testName)
                                        .font(.headline)
                                    Text(result.message)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("ui_testing_api_testing".localized)
        }
    }
    
    func testBackendConnection() {
        isRunning = true
        Task {
            let base = NetworkService.shared.baseURL
            let ping = base + "/api/v1/underwater-ai/ping"
            do {
                guard let url = URL(string: ping) else { throw URLError(.badURL) }
                let (_, response) = try await URLSession.shared.data(from: url)
                await MainActor.run {
                    if let httpResponse = response as? HTTPURLResponse {
                        let success = (200...299).contains(httpResponse.statusCode)
                        testResults.insert(TestResult(
                            testName: "Backend Connection",
                            success: success,
                            message: "\(ping) → \(httpResponse.statusCode)"
                        ), at: 0)
                    } else {
                        testResults.insert(TestResult(
                            testName: "Backend Connection",
                            success: false,
                            message: "Нет HTTP-ответа"
                        ), at: 0)
                    }
                    isRunning = false
                }
            } catch {
                await MainActor.run {
                    testResults.insert(TestResult(
                        testName: "Backend Connection",
                        success: false,
                        message: "\(ping): \(error.localizedDescription)"
                    ), at: 0)
                    isRunning = false
                }
            }
        }
    }
    
    func testUnderwaterAIHealth() {
        isRunning = true
        Task {
            let ok = await NetworkService.shared.isUnderwaterAIAvailable()
            await MainActor.run {
                testResults.insert(TestResult(
                    testName: "Underwater AI health",
                    success: ok,
                    message: ok
                        ? "Nest видит AI_UNDERWATER_SERVICE_URL; Python ai-service должен быть запущен."
                        : "В backend/.env задайте AI_UNDERWATER_SERVICE_URL=http://127.0.0.1:8010 и запустите ai-service/start.sh"
                ), at: 0)
                isRunning = false
            }
        }
    }
    
    func testUnderwaterAIProcess() {
        isRunning = true
        Task {
            let size = CGSize(width: 128, height: 128)
            let renderer = UIGraphicsImageRenderer(size: size)
            let img = renderer.image { ctx in
                UIColor(red: 0.2, green: 0.5, blue: 0.65, alpha: 1).setFill()
                ctx.fill(CGRect(origin: .zero, size: size))
            }
            guard let jpeg = img.jpegData(compressionQuality: 0.9) else {
                await MainActor.run {
                    testResults.insert(TestResult(
                        testName: "Underwater AI process",
                        success: false,
                        message: "Не удалось сделать JPEG"
                    ), at: 0)
                    isRunning = false
                }
                return
            }
            do {
                let out = try await NetworkService.shared.processUnderwaterPhotoWithAI(
                    imageData: jpeg,
                    depthMeters: 12,
                    strength: 0.75,
                    useAi: true
                )
                let ok = out.count >= 500
                await MainActor.run {
                    testResults.insert(TestResult(
                        testName: "Underwater AI process",
                        success: ok,
                        message: ok ? "Получено JPEG \(out.count) байт (нейросеть/классика на сервере)." : "Ответ слишком короткий"
                    ), at: 0)
                    isRunning = false
                }
            } catch {
                await MainActor.run {
                    testResults.insert(TestResult(
                        testName: "Underwater AI process",
                        success: false,
                        message: error.localizedDescription
                    ), at: 0)
                    isRunning = false
                }
            }
        }
    }
    
    func testAuthentication() {
        isRunning = true
        Task {
            do {
                struct LoginRequest: Codable {
                    let email: String
                    let password: String
                }
                
                struct LoginResponse: Codable {
                    let accessToken: String
                    let refreshToken: String
                }
                
                let request = LoginRequest(email: "test@example.com", password: "test123456")
                let _: LoginResponse = try await NetworkService.shared.request(
                    endpoint: "/api/auth/login",
                    method: .post,
                    body: request
                )
                
                await MainActor.run {
                    testResults.insert(TestResult(
                        testName: "Authentication",
                        success: true,
                        message: "Login successful, token received"
                    ), at: 0)
                    isRunning = false
                }
            } catch {
                await MainActor.run {
                    testResults.insert(TestResult(
                        testName: "Authentication",
                        success: false,
                        message: "Error: \(error.localizedDescription)"
                    ), at: 0)
                    isRunning = false
                }
            }
        }
    }
    
    func testGetDiveSites() {
        isRunning = true
        Task {
            do {
                let sites: [DiveSite] = try await NetworkService.shared.getDiveSites()
                await MainActor.run {
                    testResults.insert(TestResult(
                        testName: "Get Dive Sites",
                        success: true,
                        message: "Found \(sites.count) sites"
                    ), at: 0)
                    isRunning = false
                }
            } catch {
                await MainActor.run {
                    testResults.insert(TestResult(
                        testName: "Get Dive Sites",
                        success: false,
                        message: "Error: \(error.localizedDescription)"
                    ), at: 0)
                    isRunning = false
                }
            }
        }
    }
}

struct TestResult: Identifiable {
    let id = UUID()
    let testName: String
    let success: Bool
    let message: String
}

#Preview {
    APITestView()
}
#endif
