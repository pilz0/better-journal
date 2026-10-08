package foo.pilz.freaklog.ui.tabs.stats.excelskin

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import foo.pilz.freaklog.ui.tabs.search.substance.roa.toReadableString
import foo.pilz.freaklog.ui.tabs.stats.StatItem

private val ExcelGreen = Color(0xFF107C41)
private val GridLine = Color(0xFFCFCFCF)
private val BandGray = Color(0xFFF5F5F5)
private val BandText = Color(0xFF444444)
private val CellText = Color(0xFF212121)
private val FormulaText = Color(0xFF666666)

private val NumberColWidth = 32.dp
private val ColumnWidths = listOf(124.dp, 92.dp, 92.dp, 104.dp)
private val ColumnTitles = listOf("Substance", "Ingestions", "Experiences", "Total dose")

@Composable
fun ExcelStatsSkin(
    statItems: List<StatItem>,
    fileName: String,
    onDismiss: () -> Unit,
) {
    BackHandler { onDismiss() }
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (controller != null) {
                if (previousStatus != null) controller.isAppearanceLightStatusBars = previousStatus
                if (previousNav != null) controller.isAppearanceLightNavigationBars = previousNav
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = "Return to stats"
            ) { onDismiss() }
    ) {
        ExcelTopBar(fileName, onDismiss)
        FormulaBar(excelSumFormula(statItems.size))
        HorizontalDivider(color = GridLine, thickness = 1.dp)
        SpreadsheetGrid(
            statItems = statItems,
            modifier = Modifier.weight(1f)
        )
        SheetTabBar()
    }
}

@Composable
private fun ExcelTopBar(fileName: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ExcelGreen)
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onDismiss() }
                .padding(6.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileName,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Last saved: never",
                color = Color(0xCCFFFFFF),
                fontSize = 11.sp
            )
        }
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun FormulaBar(formula: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "fx",
            fontStyle = FontStyle.Italic,
            fontSize = 14.sp,
            color = FormulaText,
            fontWeight = FontWeight.SemiBold
        )
        Text(text = formula, fontSize = 14.sp, color = CellText)
    }
}

@Composable
private fun SpreadsheetGrid(statItems: List<StatItem>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
    ) {
        Row {
            GridCell("", NumberColWidth, height = 26.dp, background = BandGray)
            ColumnWidths.forEachIndexed { index, width ->
                GridCell(
                    text = ('A' + index).toString(),
                    width = width,
                    height = 26.dp,
                    background = BandGray,
                    textColor = BandText,
                    align = Alignment.Center
                )
            }
        }
        Row {
            GridCell("1", NumberColWidth, background = BandGray, textColor = BandText, align = Alignment.Center)
            ColumnTitles.forEachIndexed { index, title ->
                GridCell(title, ColumnWidths[index], bold = true)
            }
        }
        statItems.forEachIndexed { index, item ->
            Row {
                GridCell(
                    text = (index + 2).toString(),
                    width = NumberColWidth,
                    background = BandGray,
                    textColor = BandText,
                    align = Alignment.Center
                )
                GridCell(item.substanceName, ColumnWidths[0])
                GridCell(item.ingestionCount.toString(), ColumnWidths[1], align = Alignment.CenterEnd)
                GridCell(item.experienceCount.toString(), ColumnWidths[2], align = Alignment.CenterEnd)
                GridCell(
                    text = item.totalDose?.let { "${it.dose.toReadableString()} ${it.units}" } ?: "",
                    width = ColumnWidths[3],
                    align = Alignment.CenterEnd
                )
            }
        }
        Row {
            GridCell(
                text = (statItems.size + 2).toString(),
                width = NumberColWidth,
                background = BandGray,
                textColor = BandText,
                align = Alignment.Center
            )
            GridCell("SUM", ColumnWidths[0], bold = true)
            GridCell(
                text = statItems.sumOf { it.ingestionCount }.toString(),
                width = ColumnWidths[1],
                bold = true,
                align = Alignment.CenterEnd,
                selected = true
            )
            GridCell("", ColumnWidths[2])
            GridCell("", ColumnWidths[3])
        }
        repeat(8) { extra ->
            Row {
                GridCell(
                    text = (statItems.size + 3 + extra).toString(),
                    width = NumberColWidth,
                    background = BandGray,
                    textColor = BandText,
                    align = Alignment.Center
                )
                ColumnWidths.forEach { GridCell("", it) }
            }
        }
    }
}

@Composable
private fun GridCell(
    text: String,
    width: Dp,
    height: Dp = 34.dp,
    bold: Boolean = false,
    background: Color = Color.White,
    textColor: Color = CellText,
    align: Alignment = Alignment.CenterStart,
    selected: Boolean = false,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(background)
            .border(if (selected) 2.dp else 0.5.dp, if (selected) ExcelGreen else GridLine)
            .padding(horizontal = 6.dp),
        contentAlignment = align
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = textColor,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SheetTabBar() {
    Column(modifier = Modifier.background(Color.White)) {
        HorizontalDivider(color = GridLine, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Sheet1",
                color = ExcelGreen,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = BandText,
                modifier = Modifier.size(18.dp)
            )
            Box(modifier = Modifier.weight(1f))
            Text(text = "Ready", color = BandText, fontSize = 12.sp)
        }
    }
}
