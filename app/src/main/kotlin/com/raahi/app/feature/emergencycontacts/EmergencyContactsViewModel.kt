package com.raahi.app.feature.emergencycontacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raahi.app.R
import com.raahi.app.domain.model.EmergencyContact
import com.raahi.app.domain.repository.EmergencyContactRepository
import com.raahi.app.domain.usecase.ContactFieldError
import com.raahi.app.domain.usecase.ContactValidator
import com.raahi.app.ui.util.UiMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContactEditorState(
    val id: Long = NEW_CONTACT_ID,
    val name: String = "",
    val phone: String = "",
    val nameError: ContactFieldError? = null,
    val phoneError: ContactFieldError? = null,
) {
    val isEditing: Boolean get() = id != NEW_CONTACT_ID

    companion object {
        const val NEW_CONTACT_ID = 0L
    }
}

data class EmergencyContactsUiState(
    val contacts: List<EmergencyContact> = emptyList(),
    val isLoaded: Boolean = false,
    val editor: ContactEditorState? = null,
    val pendingDelete: EmergencyContact? = null,
)

sealed interface EmergencyContactsEvent {
    data class ShowUndo(val message: UiMessage) : EmergencyContactsEvent
    data class ShowMessage(val message: UiMessage) : EmergencyContactsEvent
}

class EmergencyContactsViewModel(
    private val repository: EmergencyContactRepository,
) : ViewModel() {

    private val mutableState = MutableStateFlow(EmergencyContactsUiState())
    val uiState: StateFlow<EmergencyContactsUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<EmergencyContactsEvent>(Channel.BUFFERED)
    val events: Flow<EmergencyContactsEvent> = eventChannel.receiveAsFlow()

    private var lastDeleted: EmergencyContact? = null

    init {
        repository.observeAll()
            .onEach { list -> mutableState.update { it.copy(contacts = list, isLoaded = true) } }
            .launchIn(viewModelScope)
    }

    fun startAdd() {
        mutableState.update { it.copy(editor = ContactEditorState()) }
    }

    fun startEdit(contact: EmergencyContact) {
        mutableState.update { it.copy(editor = ContactEditorState(id = contact.id, name = contact.name, phone = contact.phone)) }
    }

    fun onNameChange(name: String) {
        mutableState.update { state -> state.copy(editor = state.editor?.copy(name = name, nameError = null)) }
    }

    fun onPhoneChange(phone: String) {
        mutableState.update { state -> state.copy(editor = state.editor?.copy(phone = phone, phoneError = null)) }
    }

    fun onContactPicked(name: String?, phone: String?) {
        mutableState.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(
                editor = editor.copy(
                    name = name?.takeIf { it.isNotBlank() } ?: editor.name,
                    phone = phone?.takeIf { it.isNotBlank() } ?: editor.phone,
                    nameError = null,
                    phoneError = null,
                ),
            )
        }
    }

    fun dismissEditor() {
        mutableState.update { it.copy(editor = null) }
    }

    fun saveEditor() {
        val editor = mutableState.value.editor ?: return
        val validated = ContactValidator.validate(editor.name, editor.phone)
        if (!validated.isValid) {
            mutableState.update {
                it.copy(editor = editor.copy(nameError = validated.nameError, phoneError = validated.phoneError))
            }
            return
        }
        viewModelScope.launch {
            repository.upsert(EmergencyContact(id = editor.id, name = validated.name, phone = validated.phone))
            mutableState.update { it.copy(editor = null) }
            eventChannel.send(EmergencyContactsEvent.ShowMessage(UiMessage(R.string.emergencycontacts_saved, listOf(validated.name))))
        }
    }

    fun requestDelete(contact: EmergencyContact) {
        mutableState.update { it.copy(pendingDelete = contact) }
    }

    fun cancelDelete() {
        mutableState.update { it.copy(pendingDelete = null) }
    }

    fun confirmDelete() {
        val contact = mutableState.value.pendingDelete ?: return
        mutableState.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            repository.delete(contact.id)
            lastDeleted = contact
            eventChannel.send(EmergencyContactsEvent.ShowUndo(UiMessage(R.string.emergencycontacts_deleted, listOf(contact.name))))
        }
    }

    fun undoDelete() {
        val contact = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch { repository.upsert(contact) }
    }
}
