package com.apolilla.calendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.apolilla.calendar.R
import com.apolilla.calendar.domain.model.Attachment
import com.apolilla.calendar.domain.model.AttachmentKind

/**
 * Lists attachments. Photos get a thumbnail. Availability is checked asynchronously; a
 * missing file is shown as unavailable instead of crashing.
 */
@Composable
fun AttachmentList(
    attachments: List<Attachment>,
    isAvailable: suspend (Attachment) -> Boolean,
    onOpen: (Attachment) -> Unit,
    onRemove: ((Attachment) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        attachments.forEach { attachment ->
            key(attachment.uri) {
                AttachmentRow(attachment, isAvailable, onOpen, onRemove)
            }
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: Attachment,
    isAvailable: suspend (Attachment) -> Boolean,
    onOpen: (Attachment) -> Unit,
    onRemove: ((Attachment) -> Unit)?,
) {
    val available by produceState<Boolean?>(null, attachment.uri) { value = isAvailable(attachment) }
    val unavailable = available == false
    Card(
        onClick = { onOpen(attachment) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    unavailable -> Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    attachment.kind == AttachmentKind.PHOTO || attachment.mimeType?.startsWith("image/") == true -> {
                        val fallback = rememberVectorPainter(Icons.Outlined.BrokenImage)
                        AsyncImage(
                            model = attachment.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            error = fallback,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                    else -> Icon(Icons.Outlined.Description, null)
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    attachment.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    when {
                        unavailable -> stringResource(R.string.attachment_unavailable)
                        attachment.kind == AttachmentKind.PHOTO -> stringResource(R.string.photo)
                        else -> attachment.mimeType ?: stringResource(R.string.file)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unavailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (onRemove != null) {
                IconButton(onClick = { onRemove(attachment) }) {
                    Icon(Icons.Filled.Close, stringResource(R.string.remove_attachment, attachment.displayName))
                }
            }
        }
    }
}
