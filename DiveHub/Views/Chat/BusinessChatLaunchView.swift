//
//  BusinessChatLaunchView.swift
//  DiveHub — open or create chat with dive center / shop from listing cards.
//

import SwiftUI

struct BusinessChatLaunchView: View {
    let peerType: String
    let peerId: String
    let title: String
    
    @Environment(\.dismiss) private var dismiss
    @State private var conversation: ChatConversation?
    @State private var errorMessage: String?
    
    var body: some View {
        Group {
            if let conversation {
                ChatDetailView(conversation: conversation)
            } else if let err = errorMessage {
                VStack(spacing: 12) {
                    Text(err)
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                        .padding()
                    Button("Close") { dismiss() }
                }
            } else {
                ProgressView("Opening chat…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await open()
        }
    }
    
    private func open() async {
        do {
            conversation = try await NetworkService.shared.openChatConversation(
                peerType: peerType,
                peerId: peerId
            )
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
