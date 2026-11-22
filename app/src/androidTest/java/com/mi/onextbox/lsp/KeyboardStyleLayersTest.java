package com.mi.onextbox.lsp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import org.junit.Test;
import static org.junit.Assert.*;

public class KeyboardStyleLayersTest {
    @Test public void keyStyleIsAppliedOverAnAlreadyStyledThemeBackground() {
        KeyboardStyleLayers first = new KeyboardStyleLayers();
        TransitionDrawable theme = first.wrap(new ColorDrawable(Color.GRAY), "bgKeyNormalBlur", false);
        TransitionDrawable key = first.wrap(theme, "keyEnter", false);
        assertNotSame(theme, key);
        assertSame(key, first.wrap(key, "keyEnter", false));
        TransitionDrawable cached = (TransitionDrawable) key.getConstantState().newDrawable().mutate();
        KeyboardStyleLayers next = new KeyboardStyleLayers();
        next.restore(first.capture());
        assertEquals("bkeyEnter", next.style(cached));
        assertEquals("bbgKeyNormalBlur", next.style((TransitionDrawable) cached.getDrawable(0)));
    }

    @Test public void platformCloneKeepsItsAppearanceAndStyleAfterHandover() {
        KeyboardStyleLayers first = new KeyboardStyleLayers();
        TransitionDrawable initial = first.wrap(new ColorDrawable(Color.RED), "enter", false);
        first.wrap(new ColorDrawable(Color.GREEN), "enter", true);
        TransitionDrawable cached = (TransitionDrawable) initial.getConstantState().newDrawable().mutate();
        KeyboardStyleLayers next = new KeyboardStyleLayers();
        next.restore(first.capture());
        assertTrue(next.owns(cached));
        assertEquals("benter", next.style(cached));
        assertFalse(next.owns(new TransitionDrawable(new Drawable[] {
                new ColorDrawable(Color.RED), new ColorDrawable(Color.TRANSPARENT)
        })));
        assertEquals(initial.getId(0), next.wrap(new ColorDrawable(Color.BLUE), "enter", false).getId(0));
        assertNotEquals(initial.getId(0), next.wrap(new ColorDrawable(Color.RED), "enter", true).getId(0));
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        cached.setBounds(0, 0, 2, 2);
        cached.draw(new Canvas(bitmap));
        assertEquals(Color.RED, bitmap.getPixel(1, 1));
    }
}
