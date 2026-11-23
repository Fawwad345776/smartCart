package com.aura.smartcart

import com.aura.smartcart.util.PriceMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceMatchTest {
    @Test fun same_item_ignores_order_case_and_quantity() {
        assertTrue(PriceMatch.sameItem("Rice Basmati 5kg", "basmati rice"))
        assertTrue(PriceMatch.sameItem("Chawal", "Chawal Basmati"))
        assertFalse(PriceMatch.sameItem("Milk Pack", "Sugar"))
    }

    @Test fun quantities_are_normalised() {
        assertEquals("5kg", PriceMatch.quantity("5 kg"))
        assertEquals("5kg", PriceMatch.quantity("5 kilo"))
        assertEquals("2", PriceMatch.quantity("2"))
        assertTrue(PriceMatch.sameQuantity("1 L", "1ltr"))
    }

    @Test fun geohash_known_value() {
        // Karachi city centre.
        assertEquals("tkrtk", PriceMatch.geohash(24.8607, 67.0011, 5))
    }

    @Test fun cells_around_has_nine_cells_including_own() {
        val cells = PriceMatch.cellsAround(24.8607, 67.0011, 5)
        assertEquals(9, cells.size)
        assertTrue(PriceMatch.geohash(24.8607, 67.0011, 5) in cells)
    }

    @Test fun distance_about_right() {
        // Saddar to Clifton, Karachi: about 5-6 km.
        val d = PriceMatch.distanceKm(24.8556, 67.0270, 24.8138, 67.0300)
        assertTrue(d in 4.0..6.0)
    }
}
