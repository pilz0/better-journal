package foo.pilz.freaklog.ui.tabs.journal.experience.teamsskin

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Videocam
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import foo.pilz.freaklog.data.room.experiences.relations.IngestionWithCompanionAndCustomUnit
import foo.pilz.freaklog.ui.tabs.journal.experience.components.TimeDisplayOption
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.AllTimelines
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.AllTimelinesModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HeaderHairline = Color(0xFFE1E1E1)
private val PresenceGreen = Color(0xFF92C353)
private val IncomingBubbleColor = Color(0xFFF5F5F5)
private val OutgoingBubbleColor = Color(0xFFE8EBFA)
private val TextPrimary = Color(0xFF242424)
private val TextSecondary = Color(0xFF616161)
private val TimeColor = Color(0xFF8A8886)
private val IconColor = Color(0xFF5F5F5F)
private val PillBackground = Color(0xFFF3F2F1)

val AvatarPalette = listOf(
    Color(0xFFA4262C),
    Color(0xFFCA5010),
    Color(0xFF986F0B),
    Color(0xFF498205),
    Color(0xFF038387),
    Color(0xFF0078D4),
    Color(0xFF5C2E91),
    Color(0xFFB4009E),
    Color(0xFF8E562E),
    Color(0xFF69797E),
)

private val BubbleShape = RoundedCornerShape(12.dp)

private val shortTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private data class FakeMessage(val fromContact: Boolean, val text: String, val time: String)

private sealed interface ChatItem {
    val fromContact: Boolean
    val time: String
}

private data class ChatTextItem(
    override val fromContact: Boolean,
    val text: String,
    override val time: String
) : ChatItem

private data class ChatIngestionItem(
    val title: String,
    val subtitle: String,
    override val time: String
) : ChatItem {
    override val fromContact: Boolean = false
}

private data class ChatGraphItem(
    val model: AllTimelinesModel,
    val timeDisplayOption: TimeDisplayOption,
    override val time: String
) : ChatItem {
    override val fromContact: Boolean = true
}

private val LINKEDIN_OPENERS = listOf(
    FakeMessage(true, "Hi! I’m thrilled to connect and share some exciting insights!", "09:14"),
    FakeMessage(false, "Be off with ye!", "09:31"),
)

private fun shortTime(instant: Instant): String = shortTimeFormatter.format(instant)

private fun doseLine(iwc: IngestionWithCompanionAndCustomUnit): String {
    val ingestion = iwc.ingestion
    val dosePart = ingestion.dose?.let { "$it ${ingestion.units ?: ""}".trim() } ?: "unknown dose"
    val route = ingestion.administrationRoute.name.lowercase().replaceFirstChar { it.uppercase() }
    return "$dosePart, $route"
}

@Composable
fun TeamsChatSkin(
    ingestions: List<IngestionWithCompanionAndCustomUnit>,
    timelineModel: AllTimelinesModel?,
    timeDisplayOption: TimeDisplayOption,
    contactName: String,
    onDismiss: () -> Unit,
) {
    BackHandler { onDismiss() }
    val activity = LocalActivity.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (controller != null) {
                if (previousStatus != null) controller.isAppearanceLightStatusBars = previousStatus
                if (previousNav != null) controller.isAppearanceLightNavigationBars = previousNav
            }
        }
    }
    TeamsChatContent(ingestions, timelineModel, timeDisplayOption, contactName, onDismiss)
}

@Composable
private fun TeamsChatContent(
    ingestions: List<IngestionWithCompanionAndCustomUnit>,
    timelineModel: AllTimelinesModel?,
    timeDisplayOption: TimeDisplayOption,
    contactName: String,
    onDismiss: () -> Unit,
) {
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
        TeamsTopBar(contactName, onDismiss)
        HorizontalDivider(color = HeaderHairline, thickness = 1.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            val items = buildList<ChatItem> {
                LINKEDIN_OPENERS.forEach { add(ChatTextItem(it.fromContact, it.text, it.time)) }
                ingestions.sortedBy { it.ingestion.time }.forEach { iwc ->
                    add(
                        ChatIngestionItem(
                            iwc.ingestion.substanceName,
                            doseLine(iwc),
                            shortTime(iwc.ingestion.time)
                        )
                    )
                    iwc.ingestion.notes?.takeIf { it.isNotBlank() }?.let { note ->
                        add(ChatTextItem(false, note, shortTime(iwc.ingestion.time)))
                    }
                }
                if (timelineModel != null) {
                    add(ChatGraphItem(timelineModel, timeDisplayOption, shortTime(Instant.now())))
                }
            }
            DatePill("Today")
            items.forEachIndexed { index, item ->
                val isFirst = index == 0 || items[index - 1].fromContact != item.fromContact
                val isLast =
                    index == items.lastIndex || items[index + 1].fromContact != item.fromContact
                Spacer(modifier = Modifier.height(if (isFirst) 10.dp else 2.dp))
                if (isFirst) {
                    GroupHeader(item.fromContact, contactName, item.time)
                    Spacer(modifier = Modifier.height(3.dp))
                }
                when (item) {
                    is ChatTextItem -> if (item.fromContact) {
                        IncomingTextBubble(item.text, contactName, isLast)
                    } else {
                        OutgoingTextBubble(item.text)
                    }

                    is ChatIngestionItem -> OutgoingIngestionBubble(
                        item.title,
                        item.subtitle
                    )

                    is ChatGraphItem -> IncomingGraphBubble(
                        item.model,
                        item.timeDisplayOption,
                        contactName,
                        isLast
                    )
                }
            }
        }
        TeamsComposeBar()
    }
}

@Composable
private fun TeamsTopBar(contactName: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
            .padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onDismiss() }
                .padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                contentDescription = "Back",
                tint = Color(0xFF3B3A39),
                modifier = Modifier.size(20.dp)
            )
        }
        ContactAvatar(contactName, 38.dp, showPresence = true)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contactName,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(text = "Available", color = TextSecondary, fontSize = 12.sp)
            }
        }
        Icon(
            imageVector = Icons.Outlined.Videocam,
            contentDescription = null,
            tint = IconColor,
            modifier = Modifier.size(24.dp)
        )
        Icon(
            imageVector = Icons.Outlined.Call,
            contentDescription = null,
            tint = IconColor,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
fun ContactAvatar(name: String, size: Dp, showPresence: Boolean = false) {
    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(AvatarPalette[teamsAvatarColorIndex(name, AvatarPalette.size)]),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = teamsInitials(name),
                color = Color.White,
                fontSize = (size.value * 0.34f).sp,
                fontWeight = FontWeight.Medium
            )
        }
        if (showPresence) {
            val dot = size * 0.42f
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(dot)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(dot * 0.68f)
                        .clip(CircleShape)
                        .background(PresenceGreen)
                )
            }
        }
    }
}

@Composable
private fun DatePill(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = Color(0xFF797775),
            fontSize = 11.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(PillBackground)
                .padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun GroupHeader(fromContact: Boolean, contactName: String, time: String) {
    if (fromContact) {
        Row(
            modifier = Modifier.padding(start = 33.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = contactName, fontSize = 12.sp, color = TextSecondary)
            Text(text = time, fontSize = 11.sp, color = TimeColor)
        }
    } else {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(text = time, fontSize = 11.sp, color = TimeColor)
        }
    }
}

@Composable
private fun IncomingTextBubble(
    text: String,
    contactName: String,
    showAvatar: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        if (showAvatar) {
            ContactAvatar(contactName, 26.dp, showPresence = true)
        } else {
            Spacer(modifier = Modifier.size(26.dp))
        }
        Column(
            modifier = Modifier
                .widthIn(max = 268.dp)
                .clip(BubbleShape)
                .background(IncomingBubbleColor)
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            Text(text = text, fontSize = 14.sp, color = TextPrimary)
        }
    }
}

@Composable
private fun OutgoingTextBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            modifier = Modifier
                .widthIn(max = 268.dp)
                .clip(BubbleShape)
                .background(OutgoingBubbleColor)
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            Text(text = text, fontSize = 14.sp, color = TextPrimary)
        }
    }
}

@Composable
private fun OutgoingIngestionBubble(
    title: String,
    subtitle: String
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            modifier = Modifier
                .widthIn(max = 268.dp)
                .clip(BubbleShape)
                .background(OutgoingBubbleColor)
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = TextPrimary
            )
            Text(text = subtitle, fontSize = 13.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun IncomingGraphBubble(
    timelineModel: AllTimelinesModel,
    timeDisplayOption: TimeDisplayOption,
    contactName: String,
    showAvatar: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        if (showAvatar) {
            ContactAvatar(contactName, 26.dp, showPresence = true)
        } else {
            Spacer(modifier = Modifier.size(26.dp))
        }
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(BubbleShape)
                .background(IncomingBubbleColor)
                .padding(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(4.dp)
            ) {
                AllTimelines(
                    model = timelineModel,
                    timeDisplayOption = timeDisplayOption,
                    isShowingCurrentTime = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }
        }
    }
}

@Composable
private fun TeamsComposeBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = IconColor,
            modifier = Modifier.size(22.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(PillBackground)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(text = "Type a message", color = TimeColor, fontSize = 14.sp)
        }
        Icon(
            imageVector = Icons.Outlined.PhotoCamera,
            contentDescription = null,
            tint = IconColor,
            modifier = Modifier.size(21.dp)
        )
        Icon(
            imageVector = Icons.Outlined.Mic,
            contentDescription = null,
            tint = IconColor,
            modifier = Modifier.size(21.dp)
        )
    }
}
