package com.nativeplayer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nativeplayer.data.VideoModel
import java.io.File

@Composable
fun DuplicateFileDialog(
    video: VideoModel,
    targetFolderPath: String,
    actionType: VideoActionType,
    onDismissRequest: () -> Unit,
    onReplace: () -> Unit,
    onKeepBoth: () -> Unit
) {
    val targetFolderName = remember(targetFolderPath) {
        File(targetFolderPath).name.ifEmpty { targetFolderPath }
    }

    val actionName = remember(actionType) {
        if (actionType == VideoActionType.COPY) "copying" else "moving"
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("duplicate_file_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "File Already Exists",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "A file named '${video.title}' already exists in '$targetFolderName'.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "What would you like to do before $actionName?",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onKeepBoth,
                    modifier = Modifier.testTag("duplicate_keep_both_button")
                ) {
                    Text("Keep Both")
                }
                Button(
                    onClick = onReplace,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("duplicate_replace_button")
                ) {
                    Text("Replace")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag("duplicate_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
