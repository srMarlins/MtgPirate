//
//  LiveActivityManager.swift
//  mtgPirate
//
//  Manager for controlling Dynamic Island Live Activities
//

import ActivityKit
import Foundation

/// Manager class for starting, updating, and stopping Live Activities
@available(iOS 16.2, *)
@objc public class LiveActivityManager: NSObject {
    
    // Singleton instance for easy access from Kotlin
    @objc public static let shared = LiveActivityManager()
    
    // Track current activity
    private var currentActivity: Activity<CardMatchingActivityAttributes>?
    
    private override init() {
        super.init()
    }
    
    /// Check if Live Activities are supported and enabled
    @objc public var isSupported: Bool {
        return ActivityAuthorizationInfo().areActivitiesEnabled
    }
    
    /// Start a new Live Activity for card matching
    @objc public func startActivity(sessionId: String, totalCards: Int) {
        // End any existing activity first
        endActivity()
        
        guard isSupported else {
            print("⚠️ Live Activities not supported or not enabled")
            return
        }
        
        let attributes = CardMatchingActivityAttributes(sessionId: sessionId)
        let initialState = CardMatchingActivityAttributes.ContentState(
            phase: .parsing,
            currentCardName: nil,
            currentIndex: 0,
            totalCards: totalCards,
            ambiguousCount: 0,
            totalPrice: 0.0,
            lastUpdate: Date()
        )
        
        do {
            let activity = try Activity<CardMatchingActivityAttributes>.request(
                attributes: attributes,
                content: .init(state: initialState, staleDate: nil),
                pushType: nil
            )
            currentActivity = activity
            print("✅ Live Activity started: \(activity.id)")
        } catch {
            print("❌ Error starting Live Activity: \(error.localizedDescription)")
        }
    }
    
    /// Update the Live Activity with new state
    @objc public func updateActivity(
        phase: String,
        currentCardName: String?,
        currentIndex: Int,
        totalCards: Int,
        ambiguousCount: Int,
        totalPrice: Double
    ) {
        guard let activity = currentActivity else {
            print("⚠️ No active Live Activity to update")
            return
        }
        
        // Parse phase string to enum
        let matchingPhase = MatchingPhase(rawValue: phase) ?? .matching
        
        let updatedState = CardMatchingActivityAttributes.ContentState(
            phase: matchingPhase,
            currentCardName: currentCardName,
            currentIndex: currentIndex,
            totalCards: totalCards,
            ambiguousCount: ambiguousCount,
            totalPrice: totalPrice,
            lastUpdate: Date()
        )
        
        Task {
            await activity.update(using: .init(state: updatedState, staleDate: nil))
            print("🔄 Live Activity updated: \(phase) - \(currentIndex)/\(totalCards)")
        }
    }
    
    /// Update just the phase (simplified)
    @objc public func updatePhase(_ phase: String) {
        guard let activity = currentActivity else { return }
        
        let matchingPhase = MatchingPhase(rawValue: phase) ?? .matching
        var currentState = activity.content.state
        currentState.phase = matchingPhase
        currentState.lastUpdate = Date()
        
        Task {
            await activity.update(using: .init(state: currentState, staleDate: nil))
        }
    }
    
    /// Update progress (current index)
    @objc public func updateProgress(currentIndex: Int, cardName: String?) {
        guard let activity = currentActivity else { return }
        
        var currentState = activity.content.state
        currentState.currentIndex = currentIndex
        currentState.currentCardName = cardName
        currentState.lastUpdate = Date()
        
        Task {
            await activity.update(using: .init(state: currentState, staleDate: nil))
        }
    }
    
    /// Update ambiguous card count
    @objc public func updateAmbiguousCount(_ count: Int) {
        guard let activity = currentActivity else { return }
        
        var currentState = activity.content.state
        currentState.ambiguousCount = count
        currentState.lastUpdate = Date()
        
        Task {
            await activity.update(using: .init(state: currentState, staleDate: nil))
        }
    }
    
    /// Update total price
    @objc public func updatePrice(_ price: Double) {
        guard let activity = currentActivity else { return }
        
        var currentState = activity.content.state
        currentState.totalPrice = price
        currentState.lastUpdate = Date()
        
        Task {
            await activity.update(using: .init(state: currentState, staleDate: nil))
        }
    }
    
    /// End the Live Activity with final state
    @objc public func completeActivity(success: Bool, finalMessage: String?) {
        guard let activity = currentActivity else { return }
        
        let finalPhase: MatchingPhase = success ? .completed : .error
        var finalState = activity.content.state
        finalState.phase = finalPhase
        if let message = finalMessage {
            finalState.currentCardName = message
        }
        finalState.lastUpdate = Date()
        
        Task {
            await activity.end(
                using: .init(state: finalState, staleDate: nil),
                dismissalPolicy: .after(.now + 3) // Dismiss after 3 seconds
            )
            print("✅ Live Activity completed: \(finalPhase.rawValue)")
            currentActivity = nil
        }
    }
    
    /// End/dismiss the Live Activity immediately
    @objc public func endActivity() {
        guard let activity = currentActivity else { return }
        
        Task {
            await activity.end(using: nil, dismissalPolicy: .immediate)
            print("🛑 Live Activity ended")
            currentActivity = nil
        }
    }
    
    /// Check if an activity is currently running
    @objc public var isActivityRunning: Bool {
        return currentActivity != nil
    }
}

// MARK: - Convenience methods for common updates

@available(iOS 16.2, *)
extension LiveActivityManager {
    
    /// Start parsing phase
    @objc public func startParsing(totalCards: Int) {
        if let activity = currentActivity {
            updatePhase("Parsing")
        }
    }
    
    /// Start matching phase
    @objc public func startMatching() {
        updatePhase("Matching")
    }
    
    /// Start resolving phase
    @objc public func startResolving(ambiguousCount: Int) {
        updatePhase("Resolving")
        updateAmbiguousCount(ambiguousCount)
    }
    
    /// Start exporting phase
    @objc public func startExporting() {
        updatePhase("Exporting")
    }
}
