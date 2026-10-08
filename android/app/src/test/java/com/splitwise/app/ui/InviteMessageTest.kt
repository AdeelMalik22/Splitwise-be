package com.splitwise.app.ui

import com.splitwise.app.data.Invite
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteMessageTest {
    private fun invite(sent: Boolean?) = Invite(id = 1, group = 1, inviter = 1, status = "pending", emailSent = sent)

    @Test fun saysEmailedOnlyWhenTheServerSentIt() {
        assertTrue(emailInviteMessage(invite(true), " a@b.com ").startsWith("Invitation emailed to a@b.com"))
        assertTrue(emailInviteMessage(invite(null), "a@b.com").startsWith("Invitation emailed"))
    }

    @Test fun admitsFailureWhenTheServerCouldNotSend() {
        val message = emailInviteMessage(invite(false), "a@b.com")
        assertTrue(message.contains("could NOT be sent"))
        assertTrue(message.contains("a@b.com"))
    }
}
