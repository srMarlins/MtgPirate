//
//  mtgPirateWidgetLiveActivity.swift
//  mtgPirateWidget
//
//  Custom Live Activity for card matching progress (migrated from CardMatchingActivityWidget.swift)

import SwiftUI
import ActivityKit
import WidgetKit

@available(iOS 16.1, *)
struct CardMatchingActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: CardMatchingActivityAttributes.self) { context in
            LockScreenLiveActivityView(context: context)
        } dynamicIsland: { context in
            DynamicIsland {
                // Expanded view (when tapped)
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 8) {
                        Text(context.state.phase.emoji)
                            .font(.title2)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(context.state.phase.rawValue)
                                .font(.caption)
                                .fontWeight(.semibold)
                            if let cardName = context.state.currentCardName {
                                Text(cardName)
                                    .font(.caption2)
                                    .lineLimit(1)
                            }
                        }
                    }
                }
                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 4) {
                        Text("\(context.state.currentIndex)/\(context.state.totalCards)")
                            .font(.title3)
                            .fontWeight(.bold)
                            .foregroundColor(context.state.phase.color)
                        if context.state.ambiguousCount > 0 {
                            HStack(spacing: 4) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .font(.caption2)
                                Text("\(context.state.ambiguousCount)")
                                    .font(.caption)
                            }
                            .foregroundColor(.orange)
                        }
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 8) {
                        // Progress bar
                        GeometryReader { geometry in
                            ZStack(alignment: .leading) {
                                // Background
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(Color.gray.opacity(0.3))
                                    .frame(height: 6)
                                // Progress
                                RoundedRectangle(cornerRadius: 4)
                                    .fill(context.state.phase.color)
                                    .frame(
                                        width: geometry.size.width * progressPercentage(context: context),
                                        height: 6
                                    )
                            }
                        }
                        .frame(height: 6)
                        // Price indicator
                        if context.state.totalPrice > 0 {
                            HStack {
                                Image(systemName: "dollarsign.circle.fill")
                                    .font(.caption2)
                                Text(String(format: "$%.2f", context.state.totalPrice))
                                    .font(.caption)
                                    .fontWeight(.medium)
                                Spacer()
                            }
                            .foregroundColor(.secondary)
                        }
                    }
                    .padding(.horizontal, 8)
                }
            } compactLeading: {
                Text(context.state.phase.emoji)
                    .font(.body)
            } compactTrailing: {
                Text("\(context.state.currentIndex)/\(context.state.totalCards)")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(context.state.phase.color)
            } minimal: {
                Text(context.state.phase.emoji)
                    .font(.caption)
            }
            .keylineTint(context.state.phase.color)
        }
    }
    private func progressPercentage(context: ActivityViewContext<CardMatchingActivityAttributes>) -> CGFloat {
        guard context.state.totalCards > 0 else { return 0 }
        return CGFloat(context.state.currentIndex) / CGFloat(context.state.totalCards)
    }
}

@available(iOS 16.1, *)
struct LockScreenLiveActivityView: View {
    let context: ActivityViewContext<CardMatchingActivityAttributes>
    var body: some View {
        VStack(spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(context.state.phase.emoji)
                            .font(.title3)
                        Text(context.state.phase.rawValue)
                            .font(.headline)
                            .fontWeight(.semibold)
                    }
                    if let cardName = context.state.currentCardName {
                        Text(cardName)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                    }
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 4) {
                    Text("\(context.state.currentIndex)/\(context.state.totalCards)")
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(context.state.phase.color)
                    if context.state.totalPrice > 0 {
                        Text(String(format: "$%.2f", context.state.totalPrice))
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
            // Progress bar
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.gray.opacity(0.3))
                    RoundedRectangle(cornerRadius: 4)
                        .fill(context.state.phase.color)
                        .frame(width: geometry.size.width * progressPercentage)
                }
            }
            .frame(height: 8)
            // Ambiguity warning
            if context.state.ambiguousCount > 0 {
                HStack(spacing: 6) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.orange)
                    Text("\(context.state.ambiguousCount) cards need resolution")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Spacer()
                }
            }
        }
        .padding()
        .background(Color(UIColor.systemBackground))
    }
    private var progressPercentage: CGFloat {
        guard context.state.totalCards > 0 else { return 0 }
        return CGFloat(context.state.currentIndex) / CGFloat(context.state.totalCards)
    }
}
