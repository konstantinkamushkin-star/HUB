//
//  AdminWebPanelView.swift
//  DiveHub
//
//  Встраивает веб-админку (admin-web): те же экраны и API, что в браузере.
//

import SwiftUI
import WebKit

struct AdminWebPanelView: View {
    @StateObject private var localizationService = LocalizationService.shared
    @StateObject private var authService = AuthenticationService.shared
    @State private var reloadTick = 0
    @State private var lastLoadError: String?

    var body: some View {
        Group {
            if !authService.isAuthenticated || KeychainService.shared.getAccessToken() == nil {
                ContentUnavailableView(
                    localizationService.localizedString("webPanelNoSession", table: "admin"),
                    systemImage: "lock.shield",
                    description: Text(localizationService.localizedString("webPanelNoSessionHint", table: "admin"))
                )
            } else {
                NavigationStack {
                    VStack(spacing: 0) {
                        ZStack(alignment: .bottom) {
                            AdminWebPanelRepresentable(reloadTick: reloadTick, lastLoadError: $lastLoadError)
                                .id(reloadTick)

                            if let lastLoadError {
                                Text(lastLoadError)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .multilineTextAlignment(.center)
                                    .padding(.horizontal)
                                    .padding(.vertical, 8)
                                    .frame(maxWidth: .infinity)
                                    .background(.ultraThinMaterial)
                            }
                        }
                    }
                    .navigationTitle(localizationService.localizedString("webPanel", table: "admin"))
                    .diveHubNavigationChrome()
                    .toolbar {
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Button {
                                lastLoadError = nil
                                reloadTick += 1
                            } label: {
                                Image(systemName: "arrow.clockwise")
                                    .font(.title3)
                            }
                            .accessibilityLabel(localizationService.localizedString("webPanelRefresh", table: "admin"))
                        }
                    }
                }
            }
        }
    }
}

struct AdminWebPanelRepresentable: UIViewRepresentable {
    /// Увеличивается при ⟳ — пробивает кэш WKWebView для новой сборки admin-web.
    var reloadTick: Int
    @Binding var lastLoadError: String?

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        let prefs = WKWebpagePreferences()
        prefs.allowsContentJavaScript = true
        config.defaultWebpagePreferences = prefs
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.allowsBackForwardNavigationGestures = true
        webView.isOpaque = true
        webView.backgroundColor = .systemBackground
        #if DEBUG
        if #available(iOS 16.4, *) {
            webView.isInspectable = true
        }
        #endif
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        context.coordinator.errorBinding = $lastLoadError
        if context.coordinator.appliedReloadTick != reloadTick {
            context.coordinator.appliedReloadTick = reloadTick
            context.coordinator.beginLoadCycle(webView: webView, cacheBust: reloadTick)
        }
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        var appliedReloadTick = -1
        var errorBinding: Binding<String?>?

        /// Сначала локальная страница с `baseURL` = origin админки: в этом origin пишем `localStorage`, затем `location.replace` на дашборд.
        /// Так обходим ограничение WKWebView: `localStorage` с `about:blank` и инъекции «после» первого ответа Next часто не совпадают с веб-логином.
        func beginLoadCycle(webView: WKWebView, cacheBust: Int) {
            errorBinding?.wrappedValue = nil

            let baseString = NetworkService.shared.adminWebBaseURL
            guard let baseOrigin = URL(string: baseString),
                  let dashboardURL = NetworkService.shared.adminPanelDashboardURL(cacheBust: cacheBust),
                  let payloadB64 = Self.sessionBridgePayloadBase64(dashboardURL: dashboardURL) else {
                errorBinding?.wrappedValue = LocalizationService.shared.localizedString("webPanelLoadError", table: "admin")
                print("⚠️ [AdminWebPanel] missing admin URL or session payload (base=\(baseString))")
                return
            }

            let html = """
            <!DOCTYPE html><html><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/></head><body style="margin:0;font-family:-apple-system,sans-serif;background:#09090b;color:#a1a1aa;padding:20px;">Загрузка админ-панели…</body>
            <script>
            (function(){
              try {
                var p = JSON.parse(atob('\(payloadB64)'));
                localStorage.setItem('divehub_admin_token', p.accessToken);
                if (p.refreshToken) { localStorage.setItem('divehub_admin_refresh', p.refreshToken); }
                else { localStorage.removeItem('divehub_admin_refresh'); }
                localStorage.setItem('divehub_admin_user', JSON.stringify(p.user));
                window.location.replace(p.redirect);
              } catch (e) {
                document.body.innerHTML = '<pre style="color:#f87171;white-space:pre-wrap">' + String(e) + '</pre>';
              }
            })();
            </script></html>
            """

            webView.loadHTMLString(html, baseURL: baseOrigin)
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            DispatchQueue.main.async { self.errorBinding?.wrappedValue = nil }
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            let message = (error as NSError).localizedDescription
            print("⚠️ [AdminWebPanel] navigation failed: \(message)")
            DispatchQueue.main.async { self.errorBinding?.wrappedValue = message }
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            let message = (error as NSError).localizedDescription
            print("⚠️ [AdminWebPanel] provisional navigation failed: \(message)")
            DispatchQueue.main.async { self.errorBinding?.wrappedValue = message }
        }

        /// JSON → base64 для встраивания в `<script>` (те же ключи, что `admin-web/src/lib/auth.ts`).
        static func sessionBridgePayloadBase64(dashboardURL: URL) -> String? {
            guard let access = KeychainService.shared.getAccessToken() else { return nil }
            guard let user = AuthenticationService.shared.currentUser else { return nil }
            let refresh = KeychainService.shared.getRefreshToken()

            var userObject: [String: Any] = [
                "id": user.id,
                "email": user.email,
                "role": user.role.rawValue,
            ]
            if let first = user.firstName { userObject["firstName"] = first }
            if let last = user.lastName { userObject["lastName"] = last }

            var root: [String: Any] = [
                "accessToken": access,
                "user": userObject,
                "redirect": dashboardURL.absoluteString,
            ]
            if let refresh, !refresh.isEmpty {
                root["refreshToken"] = refresh
            }

            guard JSONSerialization.isValidJSONObject(root),
                  let data = try? JSONSerialization.data(withJSONObject: root, options: []) else {
                return nil
            }
            return data.base64EncodedString()
        }
    }
}
