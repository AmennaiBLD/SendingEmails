package com.example.checkinoutapp.feature

import androidx.lifecycle.SavedStateHandle
import com.example.checkinoutapp.core.AppClock
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.AttendanceSession
import com.example.checkinoutapp.domain.model.ClubEvent
import com.example.checkinoutapp.domain.model.EventDraft
import com.example.checkinoutapp.domain.model.EventStatus
import com.example.checkinoutapp.domain.model.SessionStatus
import com.example.checkinoutapp.domain.model.Registration
import com.example.checkinoutapp.domain.model.RegistrationField
import com.example.checkinoutapp.domain.model.RegistrationForm
import com.example.checkinoutapp.domain.model.ScanMode
import com.example.checkinoutapp.domain.model.ScanOutcome
import com.example.checkinoutapp.domain.model.ScanResult
import com.example.checkinoutapp.domain.model.Ticket
import com.example.checkinoutapp.domain.repository.AttendanceRepository
import com.example.checkinoutapp.domain.repository.CancelOutcome
import com.example.checkinoutapp.domain.repository.CorrectionOutcome
import com.example.checkinoutapp.domain.repository.EventRepository
import com.example.checkinoutapp.domain.repository.EventSaveOutcome
import com.example.checkinoutapp.domain.repository.EventStatusOutcome
import com.example.checkinoutapp.domain.repository.RegistrationOutcome
import com.example.checkinoutapp.domain.repository.RegistrationRejection
import com.example.checkinoutapp.domain.repository.RegistrationRepository
import com.example.checkinoutapp.domain.repository.ResendOutcome
import com.example.checkinoutapp.domain.repository.ReviewOutcome
import com.example.checkinoutapp.domain.repository.TicketAccessOutcome
import com.example.checkinoutapp.feature.attendance.AttendanceViewModel
import com.example.checkinoutapp.feature.attendance.CorrectionError
import com.example.checkinoutapp.feature.qrScanner.QrScannerViewModel
import com.example.checkinoutapp.feature.qrScanner.ScanFeedbackState
import com.example.checkinoutapp.feature.registration.RegistrationFailure
import com.example.checkinoutapp.feature.registration.RegistrationViewModel
import com.example.checkinoutapp.ui.platform.ScanFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A fake backend: records every scan it receives and answers from a script. */
private class FakeAttendance : AttendanceRepository {
    val scans = mutableListOf<Triple<ScanMode, String?, String?>>()
    var failNext = 0
    var answer: (ScanMode) -> ScanResult = { if (it == ScanMode.CHECK_IN) ScanResult.VALID_CHECK_IN else ScanResult.VALID_CHECK_OUT }
    var corrections = 0
    var sessions: List<AttendanceSession> = emptyList()

    override suspend fun scan(mode: ScanMode, eventId: String, qr: String?, registrationId: String?): RequestState<ScanOutcome> {
        scans += Triple(mode, qr, registrationId)
        if (failNext > 0) {
            failNext--
            return RequestState.Error("No connection.")
        }
        return RequestState.Success(ScanOutcome(answer(mode), mode, participantName = "Ahmed"))
    }

    override fun readSessionsByEventFlow(eventId: String, limit: Int): Flow<RequestState<List<AttendanceSession>>> = flowOf(RequestState.Success(sessions))
    override fun readSessionsSinceFlow(since: Long, limit: Int): Flow<RequestState<List<AttendanceSession>>> = flowOf(RequestState.Success(emptyList()))
    override suspend fun correctAttendance(sessionId: String, checkInAt: Long?, checkOutAt: Long?, reason: String): RequestState<CorrectionOutcome> {
        corrections++
        return RequestState.Success(CorrectionOutcome.CORRECTED)
    }
}

private class FakeRegistrations : RegistrationRepository {
    val submitted = mutableListOf<RegistrationForm>()
    var outcome: RequestState<RegistrationOutcome> = RequestState.Success(RegistrationOutcome.Rejected(RegistrationRejection.EVENT_FULL))

    override suspend fun register(eventId: String, form: RegistrationForm, asAdmin: Boolean): RequestState<RegistrationOutcome> {
        submitted += form
        return outcome
    }

    override fun readRegistrationsByEventFlow(eventId: String, limit: Int): Flow<RequestState<List<Registration>>> = flowOf(RequestState.Success(emptyList()))
    override fun readRecentRegistrationsFlow(limit: Int): Flow<RequestState<List<Registration>>> = flowOf(RequestState.Success(emptyList()))
    override fun readRegistrationsByEmailFlow(normalizedEmail: String, limit: Int): Flow<RequestState<List<Registration>>> = flowOf(RequestState.Success(emptyList()))
    override suspend fun cancelRegistration(registrationId: String, reason: String) = RequestState.Success(CancelOutcome.CANCELLED)
    override suspend fun reviewRegistration(registrationId: String, approve: Boolean) =
        RequestState.Success(if (approve) ReviewOutcome.APPROVED else ReviewOutcome.REJECTED)
    override suspend fun resendTicket(registrationId: String, replace: Boolean) = RequestState.Success(ResendOutcome.QUEUED)
    override suspend fun requestTicketAccess(eventId: String, email: String) = RequestState.Success("request")
    override suspend fun confirmTicketAccess(requestId: String, code: String, replace: Boolean): RequestState<TicketAccessOutcome> = RequestState.Success(TicketAccessOutcome.InvalidCode)
}

private class CountingFeedback : ScanFeedback {
    var successes = 0
    var failures = 0
    override fun success() { successes++ }
    override fun failure() { failures++ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    private var now = 1_000_000L
    private val clock = AppClock { now }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun scanner(repository: FakeAttendance, feedback: CountingFeedback = CountingFeedback()) =
        QrScannerViewModel(SavedStateHandle(mapOf("id" to "e1")), repository, feedback, clock)

    @Test
    fun aCameraReportingTheSameCodeRepeatedlySubmitsItOnce() = runTest {
        val repository = FakeAttendance()
        val feedback = CountingFeedback()
        val viewModel = scanner(repository, feedback)
        repeat(20) { viewModel.onScanned("SC2.code") }
        runCurrent()
        repeat(20) { viewModel.onScanned("SC2.code") }
        runCurrent()
        assertEquals(1, repository.scans.size)
        assertEquals(1, feedback.successes)
        val shown = assertIs<ScanFeedbackState.Answered>(viewModel.state.value.feedback)
        assertEquals(ScanResult.VALID_CHECK_IN, shown.outcome.result)

        now += 5_000 // after the cooldown the same ticket may be scanned on purpose
        viewModel.onScanned("SC2.code")
        runCurrent()
        assertEquals(2, repository.scans.size)
    }

    @Test
    fun theModeOnlyChangesWhenTheOrganizerChangesItAndTheSameQrThenChecksOut() = runTest {
        val repository = FakeAttendance()
        val viewModel = scanner(repository)
        viewModel.onScanned("SC2.code")
        runCurrent()
        assertEquals(ScanMode.CHECK_IN, viewModel.state.value.mode, "a successful check-in does not flip the mode")

        viewModel.setMode(ScanMode.CHECK_OUT)
        assertNull(viewModel.state.value.feedback)
        viewModel.onScanned("SC2.code") // same ticket, immediately: allowed because the mode changed
        runCurrent()
        assertEquals(listOf(ScanMode.CHECK_IN, ScanMode.CHECK_OUT), repository.scans.map { it.first })
        assertEquals(ScanResult.VALID_CHECK_OUT, (viewModel.state.value.feedback as ScanFeedbackState.Answered).outcome.result)
    }

    @Test
    fun aFailedCallSaysNothingWasRecordedAndAllowsAnImmediateRescan() = runTest {
        val repository = FakeAttendance().apply { failNext = 1 }
        val feedback = CountingFeedback()
        val viewModel = scanner(repository, feedback)
        viewModel.onScanned("SC2.code")
        runCurrent()
        assertIs<ScanFeedbackState.NoAnswer>(viewModel.state.value.feedback)
        assertEquals(1, feedback.failures)
        assertFalse(viewModel.state.value.processing)

        viewModel.onScanned("SC2.code")
        runCurrent()
        assertEquals(2, repository.scans.size)
        assertIs<ScanFeedbackState.Answered>(viewModel.state.value.feedback)
    }

    @Test
    fun aRefusalIsShownAsARefusalNeverAsSuccess() = runTest {
        val repository = FakeAttendance().apply { answer = { ScanResult.TICKET_REVOKED } }
        val feedback = CountingFeedback()
        val viewModel = scanner(repository, feedback)
        viewModel.setManualCode("  SC2.typed  ")
        viewModel.submitManualCode()
        runCurrent()
        assertEquals("SC2.typed", repository.scans.single().second)
        assertEquals(ScanResult.TICKET_REVOKED, (viewModel.state.value.feedback as ScanFeedbackState.Answered).outcome.result)
        assertEquals(0, feedback.successes)
        assertEquals("  SC2.typed  ", viewModel.state.value.manualCode, "the typed code stays so it can be corrected")
    }

    @Test
    fun oversizedOrEmptyPayloadsNeverReachTheBackend() = runTest {
        val repository = FakeAttendance()
        val viewModel = scanner(repository)
        viewModel.onScanned("x".repeat(5_000))
        runCurrent()
        assertTrue(repository.scans.isEmpty())
        assertEquals(ScanResult.INVALID_QR, (viewModel.state.value.feedback as ScanFeedbackState.Answered).outcome.result)
    }

    private val validForm = RegistrationForm("Ahmed Benali", " ahmed@example.org ", "+213 555 12 34 56", "USTHB", "CS", "AI", privacyAccepted = true)

    private fun RegistrationViewModel.fill(form: RegistrationForm) {
        setFullName(form.fullName); setEmail(form.email); setPhone(form.phone); setUniversity(form.university)
        setFaculty(form.faculty); setDepartment(form.department); setPrivacy(form.privacyAccepted)
    }

    @Test
    fun anInvalidFormIsNotSentAndNamesItsFields() = runTest {
        val repository = FakeRegistrations()
        val viewModel = RegistrationViewModel(SavedStateHandle(mapOf("id" to "e1")), repository)
        viewModel.setFullName("Ahmed Benali")
        viewModel.submit()
        runCurrent()
        assertTrue(repository.submitted.isEmpty())
        assertEquals(RegistrationFailure.InvalidFields, viewModel.state.value.failure)
        assertTrue(RegistrationField.EMAIL in viewModel.state.value.invalid && RegistrationField.PRIVACY in viewModel.state.value.invalid)
        viewModel.setEmail("ahmed@example.org")
        assertFalse(RegistrationField.EMAIL in viewModel.state.value.invalid, "fixing a field clears its error")
    }

    @Test
    fun aRejectedOrFailedRegistrationKeepsWhatWasTyped() = runTest {
        val repository = FakeRegistrations()
        val viewModel = RegistrationViewModel(SavedStateHandle(mapOf("id" to "e1")), repository)
        viewModel.fill(validForm)
        viewModel.submit()
        viewModel.submit() // a double tap is one request
        runCurrent()
        assertEquals(1, repository.submitted.size)
        assertEquals("+213555123456", repository.submitted.single().phone, "the phone is normalised before sending")
        assertEquals("ahmed@example.org", repository.submitted.single().email)
        assertEquals(RegistrationFailure.Rejected(RegistrationRejection.EVENT_FULL), viewModel.state.value.failure)
        assertEquals("Ahmed Benali", viewModel.state.value.form.fullName)
        assertNull(viewModel.ticket.value)

        repository.outcome = RequestState.Error("No connection.")
        viewModel.submit()
        runCurrent()
        assertEquals(RegistrationFailure.Network, viewModel.state.value.failure)
        assertEquals("USTHB", viewModel.state.value.form.university)
    }

    @Test
    fun aSuccessfulRegistrationShowsTheTicket() = runTest {
        val ticket = Ticket("r1", "e1", "AI", "Ahmed Benali", "Hall", "UTC", 1, 2, 1, "SC2.r1.1.sig", emailQueued = true, whatsappQueued = false)
        val repository = FakeRegistrations().apply { outcome = RequestState.Success(RegistrationOutcome.Registered(ticket)) }
        val viewModel = RegistrationViewModel(SavedStateHandle(mapOf("id" to "e1")), repository)
        viewModel.fill(validForm)
        viewModel.submit()
        runCurrent()
        assertEquals("r1", assertNotNull(viewModel.ticket.value).registrationId)
        assertEquals(RegistrationForm(), viewModel.state.value.form)
    }

    @Test
    fun correctionsNeedAReasonAndSaneTimesBeforeAnyCall() = runTest {
        val checkIn = 1_794_646_800_000L // 2026-11-14 09:00 UTC
        val open = AttendanceSession(
            id = "s1", eventId = "e1", registrationId = "r1", participantName = "Ahmed", faculty = "", department = "",
            checkInAt = checkIn, checkOutAt = null, durationSeconds = null, status = SessionStatus.ACTIVE, corrected = false,
            checkedInByName = "Org", checkedOutByName = null, manual = false,
        )
        val repository = FakeAttendance()
        val viewModel = AttendanceViewModel(SavedStateHandle(mapOf("id" to "e1")), repository, FakeRegistrations(), FakeEvents())
        runCurrent()

        viewModel.openCorrection(open)
        viewModel.setOutDate("2026-11-14")
        viewModel.setOutTime("11:00")
        viewModel.saveCorrection()
        assertEquals(CorrectionError.REASON_REQUIRED, viewModel.correction.value?.error)

        viewModel.setReason("Forgot to scan out")
        viewModel.setOutTime("08:00") // before the check-in
        viewModel.saveCorrection()
        assertEquals(CorrectionError.INVALID_TIMES, viewModel.correction.value?.error)

        viewModel.setOutDate("")
        viewModel.setOutTime("")
        viewModel.saveCorrection()
        assertEquals(CorrectionError.NOTHING_CHANGED, viewModel.correction.value?.error)

        viewModel.setOutDate("14/11/2026")
        viewModel.setOutTime("11:00")
        viewModel.saveCorrection()
        assertEquals(CorrectionError.INVALID_TIMES, viewModel.correction.value?.error, "an unreadable date is refused")
        assertEquals(0, repository.corrections, "nothing reached the backend so far")

        viewModel.setOutDate("2026-11-14")
        viewModel.saveCorrection()
        runCurrent()
        assertEquals(1, repository.corrections)
        assertNull(viewModel.correction.value, "the dialog closes once the correction is saved")
    }
}

private class FakeEvents : EventRepository {
    override fun readPublicEventsFlow(limit: Int): Flow<RequestState<List<ClubEvent>>> = flowOf(RequestState.Success(emptyList()))
    override fun readAllEventsFlow(limit: Int): Flow<RequestState<List<ClubEvent>>> = flowOf(RequestState.Success(emptyList()))
    override fun readEventByIdFlow(id: String): Flow<RequestState<ClubEvent?>> = flowOf(RequestState.Success(null))
    override suspend fun saveEvent(draft: EventDraft): RequestState<EventSaveOutcome> = RequestState.Success(EventSaveOutcome.NotFound)
    override suspend fun updateEventStatus(eventId: String, status: EventStatus) = RequestState.Success(EventStatusOutcome.UPDATED)
}
