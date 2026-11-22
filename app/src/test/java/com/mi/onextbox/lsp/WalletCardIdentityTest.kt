package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test

class WalletCardIdentityTest {
    @Test fun bagAndSwipeAgreeEvenWhenTheBagFormatsTheSuffix() {
        val bag = WalletCardCatalog.identity("1", "issuer", "aid", "(1234)", "")
        assertNotNull(bag)
        assertEquals(bag, WalletCardCatalog.identity("1", "issuer", "aid", "1234", "local"))
        assertNotEquals(bag, WalletCardCatalog.identity("1", "issuer", "aid", "5678", "local"))
    }

    @Test fun distinctCardInstancesCanShareIssuerSlotAndSuffix() {
        val first = WalletCardCatalog.identity("1", "issuer", "aid", "1234", "", "one")
        assertEquals(first, WalletCardCatalog.identity("1", "issuer", "aid", "(1234)", "", "one"))
        assertNotEquals(first, WalletCardCatalog.identity("1", "issuer", "aid", "1234", "", "two"))
    }

    @Test fun nonBankModelsAndMissingIds() {
        val bag = WalletCardCatalog.identity("12", "door", "slot1", "", "")
        assertEquals(bag, WalletCardCatalog.identity("12", "door", "slot1", "abc", "device"))
        assertNotEquals(bag, WalletCardCatalog.identity("12", "door", "slot2", "", ""))
        assertNotEquals(WalletCardCatalog.identity("12", "", "", "", "one"),
            WalletCardCatalog.identity("12", "", "", "", "two"))
        assertNull(WalletCardCatalog.identity("12", "", "", "", ""))
    }
}
