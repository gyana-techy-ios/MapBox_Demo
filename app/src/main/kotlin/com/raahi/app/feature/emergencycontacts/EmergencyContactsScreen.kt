package com.raahi.app.feature.emergencycontacts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raahi.app.R
import com.raahi.app.core.di.appViewModel
import com.raahi.app.domain.model.EmergencyContact
import com.raahi.app.domain.usecase.ContactFieldError
import com.raahi.app.ui.components.ConfirmDialog
import com.raahi.app.ui.components.EmptyState
import com.raahi.app.ui.components.RaahiTopBar
import com.raahi.app.ui.components.SecondaryButton
import com.raahi.app.ui.components.SkeletonList
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.theme.Sizes
import com.raahi.app.ui.util.dialNumber

@Composable
fun EmergencyContactsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = appViewModel { EmergencyContactsViewModel(repository = it.emergencyContactRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.emergencycontacts_undo)

    val pickContact = rememberLauncherForActivityResult(PickPhoneContact()) { uri ->
        uri?.let { context.contentResolver.readPickedContact(it) }
            ?.let { viewModel.onContactPicked(it.name, it.phone) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EmergencyContactsEvent.ShowMessage -> snackbarHost.showSnackbar(event.message.resolve(context))
                is EmergencyContactsEvent.ShowUndo -> {
                    val result = snackbarHost.showSnackbar(
                        message = event.message.resolve(context),
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                }
            }
        }
    }

    EmergencyContactsContent(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onAdd = viewModel::startAdd,
        onEdit = viewModel::startEdit,
        onDelete = viewModel::requestDelete,
        onCall = { context.dialNumber(it.phone) },
        modifier = modifier,
    )

    state.editor?.let { editor ->
        ContactEditorDialog(
            editor = editor,
            onNameChange = viewModel::onNameChange,
            onPhoneChange = viewModel::onPhoneChange,
            onImport = { runCatching { pickContact.launch(Unit) } },
            onSave = viewModel::saveEditor,
            onDismiss = viewModel::dismissEditor,
        )
    }
    state.pendingDelete?.let { contact ->
        ConfirmDialog(
            title = stringResource(R.string.emergencycontacts_delete_title),
            message = stringResource(R.string.emergencycontacts_delete_message, contact.name),
            confirmLabel = stringResource(R.string.emergencycontacts_delete),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun EmergencyContactsContent(
    state: EmergencyContactsUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (EmergencyContact) -> Unit,
    onDelete: (EmergencyContact) -> Unit,
    onCall: (EmergencyContact) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Scaffold(
        modifier = modifier.testTag("screen_emergencycontacts"),
        topBar = { RaahiTopBar(title = stringResource(R.string.emergencycontacts_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.testTag("contacts_add"),
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.emergencycontacts_add)) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when {
                !state.isLoaded -> SkeletonList(modifier = Modifier.padding(top = spacing.md), itemCount = 4)
                state.contacts.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Contacts,
                    title = stringResource(R.string.emergencycontacts_empty_title),
                    body = stringResource(R.string.emergencycontacts_empty_body),
                    modifier = Modifier.testTag("contacts_empty"),
                    actionLabel = stringResource(R.string.emergencycontacts_add),
                    onAction = onAdd,
                )
                else -> LazyColumn(
                    modifier = Modifier.testTag("contacts_list"),
                    contentPadding = PaddingValues(bottom = Sizes.fab + spacing.xxl),
                ) {
                    items(state.contacts, key = { it.id }) { contact ->
                        ContactRow(contact = contact, onEdit = onEdit, onDelete = onDelete, onCall = onCall)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: EmergencyContact,
    onEdit: (EmergencyContact) -> Unit,
    onDelete: (EmergencyContact) -> Unit,
    onCall: (EmergencyContact) -> Unit,
) {
    val spacing = LocalSpacing.current
    val callLabel = stringResource(R.string.emergencycontacts_call, contact.name)
    val deleteLabel = stringResource(R.string.emergencycontacts_delete_named, contact.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget + spacing.md)
            .padding(start = spacing.lg)
            .testTag("contact_${contact.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {}
                .clickable(role = Role.Button, onClick = { onEdit(contact) })
                .padding(vertical = spacing.sm),
        ) {
            Text(contact.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(contact.phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onCall(contact) }, modifier = Modifier.heightIn(min = Sizes.minTouchTarget)) {
            Icon(Icons.Outlined.Call, contentDescription = callLabel)
        }
        IconButton(
            onClick = { onDelete(contact) },
            modifier = Modifier.heightIn(min = Sizes.minTouchTarget).testTag("contact_delete_${contact.id}"),
        ) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = deleteLabel)
        }
    }
}

@Composable
private fun ContactEditorDialog(
    editor: ContactEditorState,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onImport: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("contact_editor"),
        title = {
            Text(stringResource(if (editor.isEditing) R.string.emergencycontacts_edit_title else R.string.emergencycontacts_add_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth().testTag("contact_name_field"),
                    label = { Text(stringResource(R.string.emergencycontacts_name)) },
                    singleLine = true,
                    isError = editor.nameError != null,
                    supportingText = editor.nameError?.let { error -> { Text(stringResource(error.nameMessageRes()), modifier = Modifier.testTag("contact_name_error")) } },
                )
                OutlinedTextField(
                    value = editor.phone,
                    onValueChange = onPhoneChange,
                    modifier = Modifier.fillMaxWidth().testTag("contact_phone_field"),
                    label = { Text(stringResource(R.string.emergencycontacts_phone)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = editor.phoneError != null,
                    supportingText = editor.phoneError?.let { error -> { Text(stringResource(error.phoneMessageRes()), modifier = Modifier.testTag("contact_phone_error")) } },
                )
                SecondaryButton(
                    text = stringResource(R.string.emergencycontacts_import),
                    onClick = onImport,
                    icon = Icons.Outlined.Contacts,
                    modifier = Modifier.fillMaxWidth().testTag("contact_import"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, modifier = Modifier.testTag("contact_save")) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun ContactFieldError.nameMessageRes(): Int = when (this) {
    ContactFieldError.TooLong -> R.string.emergencycontacts_error_name_long
    else -> R.string.emergencycontacts_error_name_required
}

private fun ContactFieldError.phoneMessageRes(): Int = when (this) {
    ContactFieldError.Empty -> R.string.emergencycontacts_error_phone_required
    else -> R.string.emergencycontacts_error_phone_invalid
}
