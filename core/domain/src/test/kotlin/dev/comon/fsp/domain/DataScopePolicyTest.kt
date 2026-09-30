package dev.comon.fsp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataScopePolicyTest {
    private val worker = Session.Account("user-1", Role.INTERVIEWER)

    @Test fun accountSeesOnlyItsOwnRecords() {
        assertTrue(DataScopePolicy.canAccess(worker, "user-1"))
        assertFalse(DataScopePolicy.canAccess(worker, "user-2"))
        assertFalse("anonymous records are not merged into an account", DataScopePolicy.canAccess(worker, null))
    }

    @Test fun anonymousAreaSeesOnlyOwnerlessRecords() {
        assertTrue(DataScopePolicy.canAccess(Session.Anonymous, null))
        assertFalse(DataScopePolicy.canAccess(Session.Anonymous, "user-1"))
    }

    @Test fun supervisorsDoNotSeeInterviewerRecordsOnDevice() {
        assertFalse(DataScopePolicy.canAccess(Session.Account("sup-1", Role.SUPERVISOR), "user-1"))
    }
}
