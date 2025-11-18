// LiveActivityManager.swift
// mtgPirate
//
// Helper for starting and updating CardMatchingActivity Live Activities

import Foundation
import ActivityKit
import SwiftUI

class LiveActivityManager {
    static let shared = LiveActivityManager()
    private var activity: Activity<CardMatchingActivityAttributes>?

    func startActivity() {
        let attributes = CardMatchingActivityAttributes(sessionId: UUID().uuidString)
        let initialState = CardMatchingActivityAttributes.ContentState(
            phase: .matching,
            currentCardName: "Black Lotus",
            currentIndex: 1,
            totalCards: 10,
            ambiguousCount: 0,
            totalPrice: 1000.0,
            lastUpdate: Date()
        )
        do {
            activity = try Activity<CardMatchingActivityAttributes>.request(
                attributes: attributes,
                contentState: initialState,
                pushType: nil
            )
        } catch {
            print("Failed to start Live Activity: \(error)")
        }
    }

    func updateActivity() {
        guard let activity = activity else { return }
        let newState = CardMatchingActivityAttributes.ContentState(
            phase: .resolving,
            currentCardName: "Mox Sapphire",
            currentIndex: 2,
            totalCards: 10,
            ambiguousCount: 1,
            totalPrice: 2000.0,
            lastUpdate: Date()
        )
        Task {
            await activity.update(using: newState)
        }
    }

    func endActivity() {
        guard let activity = activity else { return }
        Task {
            await activity.end(dismissalPolicy: .immediate)
        }
        self.activity = nil
    }
}
