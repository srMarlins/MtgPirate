package optimizer

import model.MultiMatch
import model.ShoppingPlan

object ShoppingOptimizer {
    fun optimize(matches: List<MultiMatch>): ShoppingPlan {
        return ShoppingPlan(emptyList(), 0, 0)
    }
}
