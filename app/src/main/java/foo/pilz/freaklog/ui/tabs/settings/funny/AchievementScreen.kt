package foo.pilz.freaklog.ui.tabs.settings.funny

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import foo.pilz.freaklog.ui.theme.horizontalPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    viewModel: AchievementViewModel = hiltViewModel()
) {
    val achievements by viewModel.achievementsFlow.collectAsState()
    val unlocked = achievements.filter { it.unlocked }
    val locked = achievements.filter { !it.unlocked }

    // Mark all currently-unlocked achievements as "seen" once the user opens
    // the list — this clears the unseen badge elsewhere.
    LaunchedEffect(unlocked.size) {
        if (unlocked.isNotEmpty()) viewModel.markAllUnlockedAsSeen()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Achievements") },
                actions = {
                    Text(
                        text = "${unlocked.size}/${achievements.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(unlocked) { achievement ->
                AchievementCard(
                    title = achievement.def.title,
                    description = achievement.def.description,
                    tier = achievement.def.tier,
                    isLocked = false
                )
            }

            if (locked.isNotEmpty()) {
                item {
                    Text(
                        text = "Locked",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = horizontalPadding,
                            vertical = 4.dp
                        )
                    )
                }

                items(locked) { achievement ->
                    AchievementCard(
                        title = achievement.def.title,
                        description = achievement.def.description,
                        tier = achievement.def.tier,
                        isLocked = true
                    )
                }
            }
        }
    }
}

@Composable
private fun AchievementCard(
    title: String,
    description: String,
    tier: AchievementTier?,
    isLocked: Boolean
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLocked)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (tier != null) TierBadge(tier, isLocked)
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isLocked)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TierBadge(tier: AchievementTier, isLocked: Boolean) {
    val (bg, label) = when (tier) {
        AchievementTier.BRONZE -> Color(0xFFCD7F32) to "Bronze"
        AchievementTier.SILVER -> Color(0xFFB0B0B0) to "Silver"
        AchievementTier.GOLD -> Color(0xFFD4AF37) to "Gold"
    }
    val alpha = if (isLocked) 0.35f else 1f
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg.copy(alpha = alpha))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black.copy(alpha = alpha),
            fontWeight = FontWeight.Bold
        )
    }
}

