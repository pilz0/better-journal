package foo.pilz.freaklog.ui.tabs.settings.funny.condition

import java.util.concurrent.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test

class EvaluationBudgetTest {
  @Test(timeout = 5000)
  fun nestedScansCannotExhaustJournalProcessing() {
    val rows = List(3000) { ingestion() }
    val context = evalContext(ingestions = rows)
    assertThrows(IllegalStateException::class.java) {
      AchievementEngine()
          .evaluate(
              "any(ingestions) { i -> any(ingestions) { j -> i.substance != j.substance } }",
              context,
          )
    }
  }

  @Test
  fun cancellationInterruptsEvaluation() {
    assertThrows(CancellationException::class.java) {
      AchievementEngine().evaluate("ingestions.count > 0", evalContext()) {
        throw CancellationException("locked")
      }
    }
  }
}
