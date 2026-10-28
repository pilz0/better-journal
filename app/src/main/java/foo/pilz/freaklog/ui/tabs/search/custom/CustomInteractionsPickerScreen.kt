package foo.pilz.freaklog.ui.tabs.search.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionTargetType
import foo.pilz.freaklog.ui.components.SectionHeader
import foo.pilz.freaklog.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomInteractionsPickerScreen(
    navigateBack: () -> Unit,
    viewModel: CustomInteractionsPickerViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val sections = viewModel.sectionsFlow.collectAsStateWithLifecycle().value
    val query = viewModel.queryFlow.collectAsStateWithLifecycle().value

    val title = "Add ${viewModel.severity.displayLabel().lowercase()} interaction"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = spacing.sm),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.queryFlow.value = it },
                    label = { Text("Search") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.None,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal)
                        .padding(bottom = spacing.sm),
                )
            }

            val onPick = { name: String, targetType: CustomInteractionTargetType ->
                viewModel.add(name, targetType, navigateBack)
            }
            pickerSection("Substances", sections.substances, CustomInteractionTargetType.SUBSTANCE, onPick)
            pickerSection("Categories", sections.categories, CustomInteractionTargetType.CATEGORY, onPick)
            pickerSection("Common", sections.common, CustomInteractionTargetType.COMMON, onPick)

            if (sections.substances.isEmpty() && sections.categories.isEmpty() && sections.common.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No matches.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                    )
                }
            }
        }
    }
}

/**
 * A header plus one lazy item per name, drawn together as a card. Each row is its own item so
 * that only the visible rows of long lists (hundreds of substances) are composed.
 */
private fun LazyListScope.pickerSection(
    title: String,
    names: List<String>,
    targetType: CustomInteractionTargetType,
    onPick: (name: String, targetType: CustomInteractionTargetType) -> Unit,
) {
    if (names.isEmpty()) return
    item(key = "header:${targetType.name}") {
        Column {
            SectionHeader(title)
            Spacer(modifier = Modifier.height(LocalSpacing.current.sm))
        }
    }
    itemsIndexed(names, key = { _, name -> "${targetType.name}:$name" }) { index, name ->
        val spacing = LocalSpacing.current
        val isFirst = index == 0
        val isLast = index == names.lastIndex
        val cardShape = MaterialTheme.shapes.medium
        val zero = CornerSize(0)
        val shape = when {
            isFirst && isLast -> cardShape
            isFirst -> cardShape.copy(bottomStart = zero, bottomEnd = zero)
            isLast -> cardShape.copy(topStart = zero, topEnd = zero)
            else -> RectangleShape
        }
        ListItem(
            headlineContent = { Text(name) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenHorizontal)
                .padding(bottom = if (isLast) spacing.sm else 0.dp)
                .clip(shape)
                .background(CardDefaults.elevatedCardColors().containerColor)
                .clickable { onPick(name, targetType) },
        )
    }
}

private fun CustomInteractionSeverity.displayLabel(): String = when (this) {
    CustomInteractionSeverity.DANGEROUS -> "Dangerous"
    CustomInteractionSeverity.UNSAFE -> "Unsafe"
    CustomInteractionSeverity.UNCERTAIN -> "Uncertain"
}
