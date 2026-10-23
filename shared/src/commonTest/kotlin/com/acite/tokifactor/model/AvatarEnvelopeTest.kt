package com.acite.tokifactor.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AvatarEnvelopeTest {
    @Test
    fun sourceSurvivesRoundTrip() {
        val envelope = AvatarEnvelope.have(
            id = "master",
            hash = "abc",
            data = "cipher",
            source = "device-a",
        )
        assertEquals(envelope, AvatarEnvelope.decode(envelope.encode()))
    }
}
