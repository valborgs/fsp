package dev.comon.fsp.domain

import org.junit.Assert.*
import org.junit.Test

class SurveyPolicyTest {
    private val a = SurveyKey("A", 1)
    private val b = SurveyKey("B", 2)
    private val account = Session.Account("worker", Role.INTERVIEWER)
    private val assignment = Assignment("assignment-a", 1, a)
    private fun start(
        session: Session = account, on: Boolean = true, online: Boolean = true,
        verified: Boolean = true, current: Assignment? = assignment,
        cache: List<CachedSurvey> = listOf(CachedSurvey(a, true)), selected: SurveyKey? = null,
    ) = SurveyPolicy.start(session, on, online, verified, current, cache, selected)
    @Test fun roleContract() {
        assertEquals(listOf(1, 2, 3), Role.entries.map { it.grade })
        assertNull(Role.fromGrade(0))
        assertFalse(SurveyPolicy.canDownload(Session.Anonymous))
        assertFalse(SurveyPolicy.canManageAssignments(account))
    }
    @Test fun clockedOutCannotStart() {
        assertEquals(StartDecision.Blocked(StartBlock.CLOCKED_OUT), start(on = false))
    }
    @Test fun managersCannotCollect() {
        for (role in listOf(Role.ADMIN, Role.SUPERVISOR))
            assertEquals(StartDecision.Blocked(StartBlock.FORBIDDEN), start(Session.Account("manager", role)))
    }
    @Test fun onlineFailureCannotFallback() {
        assertEquals(StartDecision.Blocked(StartBlock.REFRESH_REQUIRED), start(verified = false))
    }
    @Test fun offlineUsesLastAssignment() {
        assertTrue(start(online = false, verified = false) is StartDecision.Allowed)
    }
    @Test fun accountCannotUseOtherCache() {
        assertEquals(StartDecision.Blocked(StartBlock.NO_SURVEY), start(cache = listOf(CachedSurvey(b, true))))
        assertEquals(StartDecision.Blocked(StartBlock.NO_SURVEY), start(current = null))
    }
    @Test fun anonymousSelection() {
        val cache = listOf(CachedSurvey(a, true), CachedSurvey(b, true))
        assertEquals(StartDecision.Blocked(StartBlock.SELECTION_REQUIRED), start(Session.Anonymous, cache = cache))
        assertEquals(StartDecision.Allowed(StartSnapshot(b, null)), start(Session.Anonymous, cache = cache, selected = b))
    }
    @Test fun missingOrExpiredCache() {
        assertEquals(StartDecision.Blocked(StartBlock.NO_SURVEY), start(Session.Anonymous, cache = emptyList()))
        assertEquals(StartDecision.Blocked(StartBlock.NO_SURVEY), start(cache = listOf(CachedSurvey(a, false))))
    }
    @Test fun assignmentChangePreservesSnapshot() {
        val original = (start() as StartDecision.Allowed).snapshot
        val next = start(current = Assignment("b", 2, b), cache = listOf(CachedSurvey(b, true))) as StartDecision.Allowed
        assertEquals(a, original.survey)
        assertEquals(assignment, original.assignment)
        assertEquals(b, next.snapshot.survey)
    }
    @Test fun importRequiresCurrentAssignmentAndRole() {
        assertTrue(SurveyPolicy.canImport(account, a, assignment))
        assertFalse(SurveyPolicy.canImport(account, b, assignment))
        assertFalse(SurveyPolicy.canImport(Session.Anonymous, a, assignment))
        assertFalse(SurveyPolicy.canImport(Session.Account("admin", Role.ADMIN), a, assignment))
    }
}
