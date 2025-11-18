//
//  mtgPirateApp.swift
//  mtgPirate
//
//  Created by Jared Fowler on 11/14/25.
//

import SwiftUI
import ActivityKit

@main
struct mtgPirateApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onAppear {
                    LiveActivityManager.shared.startActivity()
                }
        }
    }
}
