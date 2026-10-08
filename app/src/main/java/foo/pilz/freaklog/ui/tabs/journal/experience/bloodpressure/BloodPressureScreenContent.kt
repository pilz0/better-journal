package foo.pilz.freaklog.ui.tabs.journal.experience.bloodpressure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import foo.pilz.freaklog.ui.tabs.journal.experience.rating.TimePickerSection
import foo.pilz.freaklog.ui.theme.LocalSpacing
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun BloodPressureScreenContentPreview() {
    val spacing = LocalSpacing.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Blood Pressure Measurement") },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Filled.Done,
                            contentDescription = "Done Icon"
                        )
                    }
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            BloodPressureScreenContent(
                selectedTime = LocalDateTime.now(),
                onTimeChange = {},
                diastolic = "140",
                systolic = "90",
                onDiastolicChange = {},
                onSystolicChange = {}
            )
        }
    }
}

@Composable
fun BloodPressureScreenContent(
    selectedTime: LocalDateTime,
    onTimeChange: (LocalDateTime) -> Unit,
    diastolic: String,
    systolic: String,
    onDiastolicChange: (String) -> Unit,
    onSystolicChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    shouldFocusTextFieldOnAppear: Boolean = false,
    systolicError: String? = null,
    diastolicError: String? = null,
) {
    val spacing = LocalSpacing.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        if (shouldFocusTextFieldOnAppear) {
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        OutlinedTextField(
            value = systolic,
            onValueChange = onSystolicChange,
            label = { Text(text = "Systolic") },
            trailingIcon = { Text(text = "mmHg") },
            isError = systolicError != null,
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
            }),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
        if (systolicError != null) {
            Text(
                text = systolicError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        OutlinedTextField(
            value = diastolic,
            onValueChange = onDiastolicChange,
            label = { Text(text = "Diastolic") },
            trailingIcon = { Text(text = "mmHg") },
            isError = diastolicError != null,
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
            }),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
        )
        if (diastolicError != null) {
            Text(
                text = diastolicError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        TimePickerSection(selectedTime = selectedTime, onTimeChange = onTimeChange)
    }
}