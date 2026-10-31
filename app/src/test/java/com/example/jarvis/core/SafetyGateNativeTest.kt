package com.example.jarvis.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyGateNativeTest {
    private fun d(t: ActionType) = AgentDecision(
        reasoning = "t", actionType = t, target = "Maria",
        requiresConfirmation = false, userFacingSummary = "ok"
    )

    @Test fun callAndSmsAlwaysNeedConfirmation() {
        assertEquals(RiskLevel.SENSITIVE, SafetyGate.evaluate(d(ActionType.CALL), "launcher"))
        assertEquals(RiskLevel.SENSITIVE, SafetyGate.evaluate(d(ActionType.SEND_SMS), "launcher"))
    }

    @Test fun mobileMoneyAlwaysNeedsConfirmation() {
        assertEquals(RiskLevel.SENSITIVE, SafetyGate.evaluate(d(ActionType.MOBILE_MONEY), "launcher"))
    }

    @Test fun studyModeIsSafe() {
        assertEquals(RiskLevel.SAFE, SafetyGate.evaluate(d(ActionType.STUDY_MODE), "launcher"))
    }

    @Test fun alarmIsSafe() {
        assertEquals(RiskLevel.SAFE, SafetyGate.evaluate(d(ActionType.SET_ALARM), "launcher"))
    }
}
