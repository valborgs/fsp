package dev.comon.fsp.domain

enum class Role(val grade: Int) {
    ADMIN(1), SUPERVISOR(2), INTERVIEWER(3);
    companion object {
        fun fromGrade(grade: Int): Role? = entries.find { it.grade == grade }
    }
}
sealed interface Session {
    data object Anonymous : Session
    data class Account(val userId: String, val role: Role) : Session
}
data class SurveyKey(val id: String, val version: Int)
data class CachedSurvey(val key: SurveyKey, val available: Boolean)
data class Assignment(val id: String, val revision: Int, val survey: SurveyKey)
/** Captured once at draft creation; refresh must never replace a running draft. */
data class StartSnapshot(val survey: SurveyKey, val assignment: Assignment?)
enum class StartBlock { FORBIDDEN, CLOCKED_OUT, REFRESH_REQUIRED, NO_SURVEY, SELECTION_REQUIRED }
sealed interface StartDecision {
    data class Allowed(val snapshot: StartSnapshot) : StartDecision
    data class Blocked(val reason: StartBlock) : StartDecision
}
object SurveyPolicy {
    fun canDownload(session: Session): Boolean = session is Session.Account
    fun canManageAssignments(session: Session): Boolean =
        session is Session.Account && session.role != Role.INTERVIEWER
    fun canImport(session: Session, original: SurveyKey, current: Assignment?): Boolean =
        session is Session.Account && session.role == Role.INTERVIEWER && current?.survey == original

    /** Verified means final assignment recheck AFTER storing the downloaded definition. */
    fun start(
        session: Session, clockedIn: Boolean, online: Boolean, assignmentVerified: Boolean,
        assignment: Assignment?, cache: List<CachedSurvey>, selected: SurveyKey? = null,
    ): StartDecision {
        fun blocked(reason: StartBlock) = StartDecision.Blocked(reason)
        if (session is Session.Account && session.role != Role.INTERVIEWER) return blocked(StartBlock.FORBIDDEN)
        if (!clockedIn) return blocked(StartBlock.CLOCKED_OUT)
        val usable = cache.filter { it.available }.map { it.key }.distinct()
        if (session is Session.Account) {
            if (online && !assignmentVerified) return blocked(StartBlock.REFRESH_REQUIRED)
            val current = assignment ?: return blocked(StartBlock.NO_SURVEY)
            if (current.survey !in usable) return blocked(StartBlock.NO_SURVEY)
            return StartDecision.Allowed(StartSnapshot(current.survey, current.copy()))
        }
        if (usable.isEmpty()) return blocked(StartBlock.NO_SURVEY)
        val survey = selected ?: usable.singleOrNull() ?: return blocked(StartBlock.SELECTION_REQUIRED)
        if (survey !in usable) return blocked(StartBlock.NO_SURVEY)
        return StartDecision.Allowed(StartSnapshot(survey, null))
    }
}
