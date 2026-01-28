package foo.pilz.freaklog.ui.tabs.settings.reminders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import foo.pilz.freaklog.data.room.reminders.entities.Reminder
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditReminderDialog(
    reminderToEdit: Reminder? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, String) -> Unit,
    onTest: (String) -> Unit
) {
    var title by remember { mutableStateOf(reminderToEdit?.title ?: "") }
    
    var intervalValue by remember { mutableStateOf(reminderToEdit?.intervalValue?.toString() ?: "1") }
    var intervalUnit by remember { mutableStateOf(reminderToEdit?.intervalUnit ?: "Days") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (reminderToEdit == null) "Add Reminder" else "Edit Reminder") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Substance / Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Repeat every:", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = intervalValue,
                        onValueChange = { if (it.all { char -> char.isDigit() }) intervalValue = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                         TextButton(onClick = { expanded = true }) {
                             Text(intervalUnit)
                         }
                         DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                             listOf("Minutes", "Hours", "Days").forEach { unit ->
                                 DropdownMenuItem(
                                     text = { Text(unit) },
                                     onClick = {
                                         intervalUnit = unit
                                         expanded = false
                                     }
                                 )
                             }
                         }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalIntervalValue = intervalValue.toIntOrNull()?.takeIf { it > 0 } ?: 1
                    onConfirm(title, finalIntervalValue, intervalUnit)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                 TextButton(onClick = { onTest(title) }) {
                    Text("Test")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
