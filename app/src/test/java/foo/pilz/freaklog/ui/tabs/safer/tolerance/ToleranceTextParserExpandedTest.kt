package foo.pilz.freaklog.ui.tabs.safer.tolerance

import org.junit.Assert.assertEquals
import org.junit.Test

class ToleranceTextParserExpandedTest {

    @Test
    fun parsesSingleDay_001() {
        assertEquals(1.0f, requireNotNull(ToleranceTextParser.parseDurationText("1 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_002() {
        assertEquals(2.0f, requireNotNull(ToleranceTextParser.parseDurationText("2 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_003() {
        assertEquals(3.0f, requireNotNull(ToleranceTextParser.parseDurationText("3 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_004() {
        assertEquals(4.0f, requireNotNull(ToleranceTextParser.parseDurationText("4 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_005() {
        assertEquals(5.0f, requireNotNull(ToleranceTextParser.parseDurationText("5 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_006() {
        assertEquals(6.0f, requireNotNull(ToleranceTextParser.parseDurationText("6 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_007() {
        assertEquals(7.0f, requireNotNull(ToleranceTextParser.parseDurationText("7 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_008() {
        assertEquals(8.0f, requireNotNull(ToleranceTextParser.parseDurationText("8 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_009() {
        assertEquals(9.0f, requireNotNull(ToleranceTextParser.parseDurationText("9 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_010() {
        assertEquals(10.0f, requireNotNull(ToleranceTextParser.parseDurationText("10 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_011() {
        assertEquals(11.0f, requireNotNull(ToleranceTextParser.parseDurationText("11 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_012() {
        assertEquals(12.0f, requireNotNull(ToleranceTextParser.parseDurationText("12 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_013() {
        assertEquals(13.0f, requireNotNull(ToleranceTextParser.parseDurationText("13 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_014() {
        assertEquals(14.0f, requireNotNull(ToleranceTextParser.parseDurationText("14 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_015() {
        assertEquals(15.0f, requireNotNull(ToleranceTextParser.parseDurationText("15 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_016() {
        assertEquals(16.0f, requireNotNull(ToleranceTextParser.parseDurationText("16 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_017() {
        assertEquals(17.0f, requireNotNull(ToleranceTextParser.parseDurationText("17 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_018() {
        assertEquals(18.0f, requireNotNull(ToleranceTextParser.parseDurationText("18 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_019() {
        assertEquals(19.0f, requireNotNull(ToleranceTextParser.parseDurationText("19 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_020() {
        assertEquals(20.0f, requireNotNull(ToleranceTextParser.parseDurationText("20 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_021() {
        assertEquals(21.0f, requireNotNull(ToleranceTextParser.parseDurationText("21 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_022() {
        assertEquals(22.0f, requireNotNull(ToleranceTextParser.parseDurationText("22 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_023() {
        assertEquals(23.0f, requireNotNull(ToleranceTextParser.parseDurationText("23 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_024() {
        assertEquals(24.0f, requireNotNull(ToleranceTextParser.parseDurationText("24 days")), 0.0001f)
    }

    @Test
    fun parsesSingleDay_025() {
        assertEquals(25.0f, requireNotNull(ToleranceTextParser.parseDurationText("25 days")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_026() {
        assertEquals(7.0f, requireNotNull(ToleranceTextParser.parseDurationText("1 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_027() {
        assertEquals(14.0f, requireNotNull(ToleranceTextParser.parseDurationText("2 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_028() {
        assertEquals(21.0f, requireNotNull(ToleranceTextParser.parseDurationText("3 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_029() {
        assertEquals(28.0f, requireNotNull(ToleranceTextParser.parseDurationText("4 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_030() {
        assertEquals(35.0f, requireNotNull(ToleranceTextParser.parseDurationText("5 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_031() {
        assertEquals(42.0f, requireNotNull(ToleranceTextParser.parseDurationText("6 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_032() {
        assertEquals(49.0f, requireNotNull(ToleranceTextParser.parseDurationText("7 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_033() {
        assertEquals(56.0f, requireNotNull(ToleranceTextParser.parseDurationText("8 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_034() {
        assertEquals(63.0f, requireNotNull(ToleranceTextParser.parseDurationText("9 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_035() {
        assertEquals(70.0f, requireNotNull(ToleranceTextParser.parseDurationText("10 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_036() {
        assertEquals(77.0f, requireNotNull(ToleranceTextParser.parseDurationText("11 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_037() {
        assertEquals(84.0f, requireNotNull(ToleranceTextParser.parseDurationText("12 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_038() {
        assertEquals(91.0f, requireNotNull(ToleranceTextParser.parseDurationText("13 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_039() {
        assertEquals(98.0f, requireNotNull(ToleranceTextParser.parseDurationText("14 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_040() {
        assertEquals(105.0f, requireNotNull(ToleranceTextParser.parseDurationText("15 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_041() {
        assertEquals(112.0f, requireNotNull(ToleranceTextParser.parseDurationText("16 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_042() {
        assertEquals(119.0f, requireNotNull(ToleranceTextParser.parseDurationText("17 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_043() {
        assertEquals(126.0f, requireNotNull(ToleranceTextParser.parseDurationText("18 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_044() {
        assertEquals(133.0f, requireNotNull(ToleranceTextParser.parseDurationText("19 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_045() {
        assertEquals(140.0f, requireNotNull(ToleranceTextParser.parseDurationText("20 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_046() {
        assertEquals(147.0f, requireNotNull(ToleranceTextParser.parseDurationText("21 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_047() {
        assertEquals(154.0f, requireNotNull(ToleranceTextParser.parseDurationText("22 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_048() {
        assertEquals(161.0f, requireNotNull(ToleranceTextParser.parseDurationText("23 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_049() {
        assertEquals(168.0f, requireNotNull(ToleranceTextParser.parseDurationText("24 weeks")), 0.0001f)
    }

    @Test
    fun parsesSingleWeek_050() {
        assertEquals(175.0f, requireNotNull(ToleranceTextParser.parseDurationText("25 weeks")), 0.0001f)
    }

    @Test
    fun parsesDayRange_051() {
        assertEquals(2.0f, requireNotNull(ToleranceTextParser.parseDurationText("1-3 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_052() {
        assertEquals(3.0f, requireNotNull(ToleranceTextParser.parseDurationText("2-4 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_053() {
        assertEquals(4.0f, requireNotNull(ToleranceTextParser.parseDurationText("3-5 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_054() {
        assertEquals(5.0f, requireNotNull(ToleranceTextParser.parseDurationText("4-6 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_055() {
        assertEquals(6.0f, requireNotNull(ToleranceTextParser.parseDurationText("5-7 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_056() {
        assertEquals(7.0f, requireNotNull(ToleranceTextParser.parseDurationText("6-8 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_057() {
        assertEquals(8.0f, requireNotNull(ToleranceTextParser.parseDurationText("7-9 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_058() {
        assertEquals(9.0f, requireNotNull(ToleranceTextParser.parseDurationText("8-10 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_059() {
        assertEquals(10.0f, requireNotNull(ToleranceTextParser.parseDurationText("9-11 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_060() {
        assertEquals(11.0f, requireNotNull(ToleranceTextParser.parseDurationText("10-12 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_061() {
        assertEquals(12.0f, requireNotNull(ToleranceTextParser.parseDurationText("11-13 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_062() {
        assertEquals(13.0f, requireNotNull(ToleranceTextParser.parseDurationText("12-14 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_063() {
        assertEquals(14.0f, requireNotNull(ToleranceTextParser.parseDurationText("13-15 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_064() {
        assertEquals(15.0f, requireNotNull(ToleranceTextParser.parseDurationText("14-16 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_065() {
        assertEquals(16.0f, requireNotNull(ToleranceTextParser.parseDurationText("15-17 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_066() {
        assertEquals(17.0f, requireNotNull(ToleranceTextParser.parseDurationText("16-18 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_067() {
        assertEquals(18.0f, requireNotNull(ToleranceTextParser.parseDurationText("17-19 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_068() {
        assertEquals(19.0f, requireNotNull(ToleranceTextParser.parseDurationText("18-20 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_069() {
        assertEquals(20.0f, requireNotNull(ToleranceTextParser.parseDurationText("19-21 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_070() {
        assertEquals(21.0f, requireNotNull(ToleranceTextParser.parseDurationText("20-22 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_071() {
        assertEquals(22.0f, requireNotNull(ToleranceTextParser.parseDurationText("21-23 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_072() {
        assertEquals(23.0f, requireNotNull(ToleranceTextParser.parseDurationText("22-24 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_073() {
        assertEquals(24.0f, requireNotNull(ToleranceTextParser.parseDurationText("23-25 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_074() {
        assertEquals(25.0f, requireNotNull(ToleranceTextParser.parseDurationText("24-26 days")), 0.0001f)
    }

    @Test
    fun parsesDayRange_075() {
        assertEquals(26.0f, requireNotNull(ToleranceTextParser.parseDurationText("25-27 days")), 0.0001f)
    }

    @Test
    fun parsesHours_076() {
        assertEquals(0.5f, requireNotNull(ToleranceTextParser.parseDurationText("12 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_077() {
        assertEquals(1.0f, requireNotNull(ToleranceTextParser.parseDurationText("24 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_078() {
        assertEquals(1.5f, requireNotNull(ToleranceTextParser.parseDurationText("36 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_079() {
        assertEquals(2.0f, requireNotNull(ToleranceTextParser.parseDurationText("48 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_080() {
        assertEquals(2.5f, requireNotNull(ToleranceTextParser.parseDurationText("60 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_081() {
        assertEquals(3.0f, requireNotNull(ToleranceTextParser.parseDurationText("72 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_082() {
        assertEquals(3.5f, requireNotNull(ToleranceTextParser.parseDurationText("84 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_083() {
        assertEquals(4.0f, requireNotNull(ToleranceTextParser.parseDurationText("96 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_084() {
        assertEquals(4.5f, requireNotNull(ToleranceTextParser.parseDurationText("108 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_085() {
        assertEquals(5.0f, requireNotNull(ToleranceTextParser.parseDurationText("120 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_086() {
        assertEquals(5.5f, requireNotNull(ToleranceTextParser.parseDurationText("132 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_087() {
        assertEquals(6.0f, requireNotNull(ToleranceTextParser.parseDurationText("144 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_088() {
        assertEquals(6.5f, requireNotNull(ToleranceTextParser.parseDurationText("156 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_089() {
        assertEquals(7.0f, requireNotNull(ToleranceTextParser.parseDurationText("168 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_090() {
        assertEquals(7.5f, requireNotNull(ToleranceTextParser.parseDurationText("180 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_091() {
        assertEquals(8.0f, requireNotNull(ToleranceTextParser.parseDurationText("192 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_092() {
        assertEquals(8.5f, requireNotNull(ToleranceTextParser.parseDurationText("204 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_093() {
        assertEquals(9.0f, requireNotNull(ToleranceTextParser.parseDurationText("216 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_094() {
        assertEquals(9.5f, requireNotNull(ToleranceTextParser.parseDurationText("228 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_095() {
        assertEquals(10.0f, requireNotNull(ToleranceTextParser.parseDurationText("240 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_096() {
        assertEquals(10.5f, requireNotNull(ToleranceTextParser.parseDurationText("252 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_097() {
        assertEquals(11.0f, requireNotNull(ToleranceTextParser.parseDurationText("264 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_098() {
        assertEquals(11.5f, requireNotNull(ToleranceTextParser.parseDurationText("276 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_099() {
        assertEquals(12.0f, requireNotNull(ToleranceTextParser.parseDurationText("288 hours")), 0.0001f)
    }

    @Test
    fun parsesHours_100() {
        assertEquals(12.5f, requireNotNull(ToleranceTextParser.parseDurationText("300 hours")), 0.0001f)
    }

}