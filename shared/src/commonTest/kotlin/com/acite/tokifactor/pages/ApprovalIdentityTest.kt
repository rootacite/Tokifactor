package com.acite.tokifactor.pages

import com.acite.tokifactor.model.legacyMemberId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApprovalIdentityTest {
    @Test
    fun approvalPermissionUsesMemberIdNotDeviceId() {
        val memberId = legacyMemberId("Acite Lee")
        assertTrue(approvalRequesterMatches(memberId, memberId))
        assertFalse(approvalRequesterMatches(memberId, "4554f60b-5ab6-414e-a303-4111c6717d8e"))
    }

    @Test
    fun legacyApprovalsWithoutRequesterRemainActionable() {
        assertTrue(approvalRequesterMatches("", legacyMemberId("Acite Lee")))
    }
}
