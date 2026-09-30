package dev.comon.fsp.domain

/**
 * Separation of the account area and the anonymous (no account) area for records stored on this
 * device (responses, attendance, send queue). Survey definitions are shared and not covered here.
 */
object DataScopePolicy {
    /** An account sees only its own records; the anonymous area sees only records with no owner. */
    fun canAccess(session: Session, ownerUserId: String?): Boolean = when (session) {
        Session.Anonymous -> ownerUserId == null
        is Session.Account -> ownerUserId == session.userId
    }
}
