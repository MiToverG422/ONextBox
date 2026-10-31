package com.mi.onextbox.lsp

import org.junit.Assert.*
import org.junit.Test
import java.util.WeakHashMap

class SettingsAppInfoRecyclingTest {
    private class Card {
        var resets = 0
        fun resetBackgroundAnimation() { resets++ }
    }

    private class DummyView
    private class Holder(val itemView: Any)

    private fun reset(holder: Holder) {
        holder.itemView.javaClass.getDeclaredMethod("resetBackgroundAnimation").apply {
            isAccessible = true
        }.invoke(holder.itemView)
    }

    @Test fun nativeAnimationDummyDoesNotReceiveCardMethods() {
        val card = Holder(Card())
        val bindings = WeakHashMap<Holder, Any>().apply { put(card, Any()) }
        recycleBoundAppInfoHolder(bindings, Holder(DummyView()), ::reset)
        assertTrue(bindings.containsKey(card))
        assertEquals(0, (card.itemView as Card).resets)
    }

    @Test fun boundCardResetsOnceAndCanBeReboundAfterRecycling() {
        val card = Card()
        val holder = Holder(card)
        val bindings = WeakHashMap<Holder, Any>().apply { put(holder, Any()) }
        recycleBoundAppInfoHolder(bindings, holder, ::reset)
        recycleBoundAppInfoHolder(bindings, holder, ::reset)
        assertEquals(1, card.resets)
        assertTrue(bindings.isEmpty())
        bindings[holder] = Any()
        recycleBoundAppInfoHolder(bindings, holder, ::reset)
        assertEquals(2, card.resets)
    }

    @Test fun expansionAndCollapseCanInterleaveDummyAndRealRecycling() {
        val cards = List(3) { Holder(Card()) }
        val bindings = WeakHashMap<Holder, Any>().apply { cards.forEach { put(it, Any()) } }
        for (holder in cards) {
            recycleBoundAppInfoHolder(bindings, Holder(DummyView()), ::reset)
            recycleBoundAppInfoHolder(bindings, holder, ::reset)
        }
        assertTrue(bindings.isEmpty())
        cards.forEach { assertEquals(1, (it.itemView as Card).resets) }
    }
}
