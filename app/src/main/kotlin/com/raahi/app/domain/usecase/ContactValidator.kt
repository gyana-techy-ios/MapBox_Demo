package com.raahi.app.domain.usecase

sealed interface ContactFieldError {
    data object Empty : ContactFieldError
    data object Invalid : ContactFieldError
    data object TooLong : ContactFieldError
}

data class ValidatedContact(
    val name: String,
    val phone: String,
    val nameError: ContactFieldError?,
    val phoneError: ContactFieldError?,
) {
    val isValid: Boolean get() = nameError == null && phoneError == null
}

object ContactValidator {

    const val MAX_NAME_LENGTH = 60

    private val mobilePattern = Regex("^(\\+91|91|0)?[6-9]\\d{9}$")
    private val servicePattern = Regex("^\\d{3,4}$")
    private val separators = Regex("[\\s\\-().]")

    fun normalizePhone(raw: String): String = raw.replace(separators, "")

    fun validatePhone(raw: String): ContactFieldError? {
        val normalized = normalizePhone(raw)
        return when {
            normalized.isEmpty() -> ContactFieldError.Empty
            mobilePattern.matches(normalized) || servicePattern.matches(normalized) -> null
            else -> ContactFieldError.Invalid
        }
    }

    fun validateName(raw: String): ContactFieldError? {
        val trimmed = raw.trim()
        return when {
            trimmed.isEmpty() -> ContactFieldError.Empty
            trimmed.length > MAX_NAME_LENGTH -> ContactFieldError.TooLong
            else -> null
        }
    }

    fun validate(name: String, phone: String): ValidatedContact = ValidatedContact(
        name = name.trim(),
        phone = normalizePhone(phone),
        nameError = validateName(name),
        phoneError = validatePhone(phone),
    )
}
