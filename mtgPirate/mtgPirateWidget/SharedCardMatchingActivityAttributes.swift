// SharedCardMatchingActivityAttributes.swift
// Shared between app and widget extension

import SwiftUI
import ActivityKit

/// Activity attributes defining the static data for the Live Activity
struct CardMatchingActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        /// Current phase of the matching process
        var phase: MatchingPhase
        /// Current card being processed (optional)
        var currentCardName: String?
        /// Progress indicators
        var currentIndex: Int
        var totalCards: Int
        /// Number of cards that need resolution
        var ambiguousCount: Int
        /// Total price so far (in dollars)
        var totalPrice: Double
        /// Last updated timestamp
        var lastUpdate: Date
    }
    /// Session identifier
    var sessionId: String
}

/// Phases of the card matching workflow
enum MatchingPhase: String, Codable, Hashable {
    case parsing = "Parsing"
    case matching = "Matching"
    case resolving = "Resolving"
    case exporting = "Exporting"
    case completed = "Complete"
    case error = "Error"
    var emoji: String {
        switch self {
        case .parsing: return "📋"
        case .matching: return "🔍"
        case .resolving: return "⚠️"
        case .exporting: return "📤"
        case .completed: return "✅"
        case .error: return "❌"
        }
    }
    var color: Color {
        switch self {
        case .parsing: return .blue
        case .matching: return .purple
        case .resolving: return .orange
        case .exporting: return .green
        case .completed: return .green
        case .error: return .red
        }
    }
}

