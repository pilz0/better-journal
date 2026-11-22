package foo.pilz.freaklog.data.substances.classes.roa

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class DoseUnitsTest {

  @Test
  fun `toBase maps mass units and aliases to mg`() {
    assertThat(DoseUnits.toBase("kg")).isEqualTo("mg" to 1e6)
    assertThat(DoseUnits.toBase("g")).isEqualTo("mg" to 1e3)
    assertThat(DoseUnits.toBase("Grams")).isEqualTo("mg" to 1e3)
    assertThat(DoseUnits.toBase(" mg ")).isEqualTo("mg" to 1.0)
    assertThat(DoseUnits.toBase("ug")).isEqualTo("mg" to 1e-3)
    assertThat(DoseUnits.toBase("μg")).isEqualTo("mg" to 1e-3)
    assertThat(DoseUnits.toBase("MCG")).isEqualTo("mg" to 1e-3)
  }

  @Test
  fun `toBase maps volume units and aliases to ml`() {
    assertThat(DoseUnits.toBase("l")).isEqualTo("ml" to 1e3)
    assertThat(DoseUnits.toBase("Litres")).isEqualTo("ml" to 1e3)
    assertThat(DoseUnits.toBase("dl")).isEqualTo("ml" to 100.0)
    assertThat(DoseUnits.toBase("centiliter")).isEqualTo("ml" to 10.0)
    assertThat(DoseUnits.toBase("mL")).isEqualTo("ml" to 1.0)
    assertThat(DoseUnits.toBase("µl")).isEqualTo("ml" to 1e-3)
    assertThat(DoseUnits.toBase("ul")).isEqualTo("ml" to 1e-3)
    assertThat(DoseUnits.toBase("microlitres")).isEqualTo("ml" to 1e-3)
  }

  @Test
  fun `toBase keeps unscaled units as their own base and rejects missing units`() {
    assertThat(DoseUnits.toBase("Pills")).isEqualTo("pills" to 1.0)
    assertThat(DoseUnits.toBase(null)).isNull()
    assertThat(DoseUnits.toBase("  ")).isNull()
  }

  @Test
  fun `displayUnit picks the largest unit the amount reaches`() {
    assertThat(DoseUnits.displayUnit("mg", 2_500_000.0)).isEqualTo("kg" to 1e6)
    assertThat(DoseUnits.displayUnit("mg", 1500.0)).isEqualTo("g" to 1e3)
    assertThat(DoseUnits.displayUnit("mg", 1000.0)).isEqualTo("g" to 1e3)
    assertThat(DoseUnits.displayUnit("mg", 999.0)).isEqualTo("mg" to 1.0)
    assertThat(DoseUnits.displayUnit("mg", 0.06)).isEqualTo("µg" to 1e-3)
    assertThat(DoseUnits.displayUnit("mg", 1e-9)).isEqualTo("µg" to 1e-3)
    assertThat(DoseUnits.displayUnit("ml", 1500.0)).isEqualTo("l" to 1e3)
    assertThat(DoseUnits.displayUnit("ml", 20.0)).isEqualTo("ml" to 1.0)
    assertThat(DoseUnits.displayUnit("ml", 0.3)).isEqualTo("µl" to 1e-3)
  }

  @Test
  fun `displayUnit keeps the base for zero and unscaled units`() {
    assertThat(DoseUnits.displayUnit("mg", 0.0)).isEqualTo("mg" to 1.0)
    assertThat(DoseUnits.displayUnit("ml", 0.0)).isEqualTo("ml" to 1.0)
    assertThat(DoseUnits.displayUnit("pills", 5000.0)).isEqualTo("pills" to 1.0)
  }

  @Test
  fun `displayUnit uses the preferred unit only within the same family`() {
    assertThat(DoseUnits.displayUnit("mg", 1500.0, preferred = "mg")).isEqualTo("mg" to 1.0)
    assertThat(DoseUnits.displayUnit("mg", 0.5, preferred = "µg")).isEqualTo("µg" to 1e-3)
    assertThat(DoseUnits.displayUnit("ml", 1500.0, preferred = "ml")).isEqualTo("ml" to 1.0)
    assertThat(DoseUnits.displayUnit("mg", 1500.0, preferred = "ml")).isEqualTo("g" to 1e3)
    assertThat(DoseUnits.displayUnit("mg", 1500.0, preferred = "pills")).isEqualTo("g" to 1e3)
    assertThat(DoseUnits.displayUnit("pills", 3.0, preferred = "mg")).isEqualTo("pills" to 1.0)
  }
}
