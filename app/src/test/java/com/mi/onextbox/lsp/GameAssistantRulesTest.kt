package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class GameAssistantRulesTest {
    @Test fun rootBypassDoesNotRemoveOtherSafetyStates() {
        assertEquals(0, GameAssistantRules.safetyStatus(true, 1))
        listOf(-1, 0, 2, 3, 4, 5, 100).forEach { assertEquals(it, GameAssistantRules.safetyStatus(true, it)) }
        (-1..5).forEach { assertEquals(it, GameAssistantRules.safetyStatus(false, it)) }
    }
    @Test fun limitsRestoreWithoutTrimmingExistingItems() {
        listOf(8, 20).forEach { stock ->
            assertEquals(Int.MAX_VALUE, GameAssistantRules.maxItems(true, stock))
            assertEquals(stock, GameAssistantRules.maxItems(false, stock))
        }
    }
    @Test fun unlockedMusicMembershipMatchesAnyPackageButNeverAnEmptyOne() {
        listOf("com.example.player", "com.tencent.tmgp.cod").forEach {
            assertTrue(it, GameAssistantRules.musicMembership(true, it, false))
            assertTrue(it, GameAssistantRules.musicMembership(true, it, true))
        }
        listOf(null, "").forEach {
            assertFalse("$it", GameAssistantRules.musicMembership(true, it, true))
        }
        listOf(null, "", "com.example.player").forEach { candidate ->
            assertTrue("$candidate", GameAssistantRules.musicMembership(false, candidate, true))
            assertFalse("$candidate", GameAssistantRules.musicMembership(false, candidate, false))
        }
    }
    @Test fun onlyTheSevenAuditedBuiltInSkinPacksCanBeSelected() {
        assertEquals(8, GameAssistantRules.skins.distinct().size)
        assertEquals("", GameAssistantRules.skinName("default"))
        GameAssistantRules.skins.drop(1).forEach { assertEquals(it, GameAssistantRules.skinName(it)) }
        listOf(null, "", "../external.skin", "unverified", "genshin-hutao.skin").forEach {
            assertEquals("default", GameAssistantRules.skin(it))
        }
    }
    @Test fun developerPreferencesKeepTheNativeIntegerType() {
        assertTrue(GameAssistantRules.developerIntKey("feel_adjust_v2_idle_fade_delay_sec_key"))
        assertFalse(GameAssistantRules.developerIntKey("king_of_glory_fourd_vibration"))
        assertEquals(9, GameAssistantRules.developerCategories.distinct().size)
    }
}
