//
//  mtgPirateWidgetBundle.swift
//  mtgPirateWidget
//
//  Created by Jared Fowler on 11/17/25.
//

import WidgetKit
import SwiftUI

@main
struct mtgPirateWidgetBundle: WidgetBundle {
    var body: some Widget {
        mtgPirateWidget()
        mtgPirateWidgetControl()
        CardMatchingActivityWidget() // Use your custom Live Activity widget
    }
}
