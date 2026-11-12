package com.example.checkinoutapp.domain.registration

import com.example.checkinoutapp.domain.model.RegistrationField
import com.example.checkinoutapp.domain.model.RegistrationForm

// No "/" either: the normalised email is part of a document id (see registrationKeyId).
private val EMAIL = Regex("^[^\\s@/]+@[^\\s@/]+\\.[^\\s@/]{2,}$")
private val E164 = Regex("^\\+[1-9]\\d{7,14}$")

fun normalizeEmail(email: String): String = email.trim().lowercase()

/** One `registrationKeys` document per (event, email): it makes a second registration impossible. */
fun registrationKeyId(eventId: String, normalizedEmail: String): String = "${eventId}_$normalizedEmail"

fun isValidEmail(email: String): Boolean = normalizeEmail(email).let { it.length <= 254 && EMAIL.matches(it) }

fun normalizePhone(phone: String): String {
    val compact = phone.trim().replace(Regex("[\\s\\-().]"), "")
    return if (compact.startsWith("00")) "+" + compact.drop(2) else compact
}

fun isValidInternationalPhone(phone: String): Boolean = E164.matches(normalizePhone(phone))

fun validateRegistration(form: RegistrationForm): Set<RegistrationField> = buildSet {
    if (form.fullName.trim().length < 2) add(RegistrationField.FULL_NAME)
    if (!isValidEmail(form.email)) add(RegistrationField.EMAIL)
    if (!isValidInternationalPhone(form.phone)) add(RegistrationField.PHONE)
    if (form.university.isBlank()) add(RegistrationField.UNIVERSITY)
    if (form.faculty.isBlank()) add(RegistrationField.FACULTY)
    if (form.department.isBlank()) add(RegistrationField.DEPARTMENT)
    // WhatsApp delivery needs the participant's explicit opt-in.
    if (form.deliveryPreference.usesWhatsApp && !form.whatsappConsent) add(RegistrationField.WHATSAPP_CONSENT)
    if (!form.privacyAccepted) add(RegistrationField.PRIVACY)
}

/** The form as it is sent: trimmed, with the phone number normalised. */
fun cleanRegistrationForm(form: RegistrationForm): RegistrationForm = form.copy(
    fullName = form.fullName.trim(),
    email = form.email.trim(),
    phone = normalizePhone(form.phone),
    university = form.university.trim(),
    faculty = form.faculty.trim(),
    department = form.department.trim(),
    studentId = form.studentId.trim(),
)
