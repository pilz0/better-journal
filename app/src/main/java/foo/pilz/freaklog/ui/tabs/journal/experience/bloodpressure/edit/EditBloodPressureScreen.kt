package foo.pilz.freaklog.ui.tabs.journal.experience.bloodpressure.edit

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import foo.pilz.freaklog.ui.tabs.journal.experience.bloodpressure.BloodPressureScreenContent
import foo.pilz.freaklog.ui.tabs.journal.experience.timeline.BloodPressureHealthConnect
import foo.pilz.freaklog.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBloodPressureScreen(
    viewModel: EditBloodPressureViewModel = hiltViewModel(),
    navigateBack: () -> Unit
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val savePermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(BloodPressureHealthConnect.writePermissions)) {
            viewModel.onDoneTap()
            navigateBack()
        } else {
            Toast.makeText(context, "Health Connect permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    val deletePermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(BloodPressureHealthConnect.writePermissions)) {
            viewModel.delete()
            navigateBack()
        } else {
            Toast.makeText(context, "Health Connect permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit blood pressure measurement") },
                actions = {
                    IconButton(onClick = {
                        deletePermissionLauncher.launch(BloodPressureHealthConnect.writePermissions)
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete blood pressure measurement",
                        )
                    }
                    IconButton(onClick = {
                        if (viewModel.isInputValid) {
                            savePermissionLauncher.launch(BloodPressureHealthConnect.writePermissions)
                        } else {
                            Toast.makeText(
                                context,
                                "Enter valid systolic and diastolic values",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }) {
                        Icon(
                            Icons.Filled.Done,
                            contentDescription = "Done icon"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            BloodPressureScreenContent(
                selectedTime = viewModel.localDateTimeFlow.collectAsStateWithLifecycle().value,
                onTimeChange = viewModel::onChangeTime,
                diastolic = viewModel.diastolic,
                systolic = viewModel.systolic,
                onDiastolicChange = viewModel::onChangeDiastolic,
                onSystolicChange = viewModel::onChangeSystolic,
                systolicError = viewModel.fieldErrors.systolic,
                diastolicError = viewModel.fieldErrors.diastolic,
            )
            Spacer(modifier = Modifier.height(spacing.fabBottomPad))
        }
    }
}
