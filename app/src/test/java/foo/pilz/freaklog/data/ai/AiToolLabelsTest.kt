package foo.pilz.freaklog.data.ai

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class AiToolLabelsTest {

  @Test
  fun describesProgressAndSummariesForWebAndVitalsTools() {
    assertThat(AiToolLabels.describe("web_search", mapOf("query" to "psilocin half life")))
        .isEqualTo("Searched the web for “psilocin half life”")
    assertThat(
            AiToolLabels.describe(
                "fetch_webpage",
                mapOf("url" to "https://en.wikipedia.org/wiki/LSD"),
            )
        )
        .isEqualTo("Read en.wikipedia.org")
    assertThat(AiToolLabels.describe("get_vitals", mapOf("experience_id" to 12)))
        .isEqualTo("Checked vitals for experience #12")
    assertThat(AiToolLabels.describe("get_vitals", mapOf("hours_back" to 48)))
        .isEqualTo("Checked vitals (last 48 h)")

    assertThat(AiToolLabels.progress("web_search")).isEqualTo("Searching the web…")
    assertThat(AiToolLabels.progress("fetch_webpage")).isEqualTo("Reading webpage…")
    assertThat(AiToolLabels.progress("get_vitals")).isEqualTo("Reading your vitals…")

    assertThat(
            AiToolLabels.summarise(
                "web_search",
                JSONObject().put("results", JSONArray().put(JSONObject()).put(JSONObject())),
            )
        )
        .isEqualTo("2 results")
    assertThat(
            AiToolLabels.summarise(
                "fetch_webpage",
                JSONObject().put("title", "Pharmacology of LSD"),
            )
        )
        .isEqualTo("Pharmacology of LSD")
    assertThat(
            AiToolLabels.summarise(
                "get_vitals",
                JSONObject()
                    .put(
                        "available_data_types",
                        JSONArray(listOf("heart_rate", "oxygen_saturation")),
                    ),
            )
        )
        .isEqualTo("heart rate, SpO₂")
    assertThat(
            AiToolLabels.summarise(
                "get_vitals",
                JSONObject().put("available_data_types", JSONArray()),
            )
        )
        .isEqualTo("no vitals recorded")
    assertThat(
            AiToolLabels.summarise(
                "web_search",
                JSONObject().put("status", "error").put("message", "Search disabled"),
            )
        )
        .isEqualTo("Search disabled")
  }
}
