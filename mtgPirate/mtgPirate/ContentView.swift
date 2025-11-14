//
//  ContentView.swift
//  mtgPirate
//
//  Created by Jared Fowler on 11/14/25.
//

import SwiftUI
import shared

/// SwiftUI wrapper for the Kotlin Multiplatform Compose UI
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all, edges: .all)
    }
}

/// UIViewControllerRepresentable wrapper to embed Compose UI in SwiftUI
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // Call the Kotlin function that creates the Compose ViewController
        return MainKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Nothing to update - Compose manages its own state
    }
}

#Preview {
    ContentView()
}
