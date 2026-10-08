package foo.pilz.freaklog.ui.tabs.journal.outlookskin

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FilterList
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import foo.pilz.freaklog.ui.tabs.journal.experience.teamsskin.ContactAvatar

private val OutlookBlue = Color(0xFF0F6CBD)
private val TextPrimary = Color(0xFF242424)
private val TextSecondary = Color(0xFF616161)
private val DividerColor = Color(0xFFE1E1E1)

@Composable
fun OutlookInboxSkin(
    rows: List<OutlookMailRow>,
    onOpenExperience: (experienceId: Int) -> Unit,
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
                onClickLabel = "Return to journal"
            ) { onDismiss() }
    ) {
        OutlookTopBar()
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(rows) { row ->
                    MailRow(row, onClick = { onOpenExperience(row.experienceId) })
                    HorizontalDivider(
                        color = DividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 68.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(OutlookBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        OutlookBottomBar()
    }
}

@Composable
private fun OutlookTopBar() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OutlookBlue)
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Inbox",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Outlined.FilterList,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Row(
            modifier = Modifier.padding(start = 56.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Focused",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = "Other",
                color = Color(0xB3FFFFFF),
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun MailRow(row: OutlookMailRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        ContactAvatar(row.sender, 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.sender,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    fontWeight = if (row.unread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = row.timeText,
                    fontSize = 12.sp,
                    color = if (row.unread) OutlookBlue else TextSecondary,
                    fontWeight = if (row.unread) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            Text(
                text = row.subject,
                fontSize = 14.sp,
                color = if (row.unread) OutlookBlue else TextPrimary,
                fontWeight = if (row.unread) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = row.preview,
                fontSize = 13.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OutlookBottomBar() {
    Column(modifier = Modifier.background(Color.White)) {
        HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomBarItem(Icons.Filled.Email, "Email", selected = true)
            BottomBarItem(Icons.Outlined.Search, "Search", selected = false)
            BottomBarItem(Icons.Outlined.CalendarMonth, "Calendar", selected = false)
        }
    }
}

@Composable
private fun BottomBarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
) {
    val tint = if (selected) OutlookBlue else TextSecondary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(text = label, fontSize = 11.sp, color = tint)
    }
}
