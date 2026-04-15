//
//  ContentView.swift
//  DiveHub
//
//  Created by admin on 16.01.2026.
//

import SwiftUI

struct ContentView: View {
    var body: some View {
        VStack {
            Image(systemName: "globe")
                .imageScale(.large)
                .foregroundStyle(.tint)
            Text("ui_common_hello_world".localized)
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
