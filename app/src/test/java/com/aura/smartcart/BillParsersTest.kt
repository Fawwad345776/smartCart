package com.aura.smartcart

import com.aura.smartcart.util.BillVoiceItem
import com.aura.smartcart.util.BillVoiceParser
import com.aura.smartcart.util.ReceiptItem
import com.aura.smartcart.util.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Test

class BillVoiceParserTest {
    private fun v(name: String, qty: String, price: String) = BillVoiceItem(name, qty, price)
    private fun parse(t: String) = BillVoiceParser.parse(t)

    @Test fun name_qty_price() =
        assertEquals(listOf(v("Chawal", "5 kilo", "1450")), parse("chawal 5 kilo 1450"))

    @Test fun with_currency_word() =
        assertEquals(listOf(v("Doodh", "2 packet", "360")), parse("doodh 2 packet 360 rupay"))

    @Test fun two_items_with_and() =
        assertEquals(
            listOf(v("Chawal", "5 kilo", "1450"), v("Doodh", "2 packet", "360")),
            parse("chawal 5 kilo 1450 aur doodh 2 packet 360")
        )

    @Test fun fast_without_and() =
        assertEquals(
            listOf(v("Chawal", "", "1450"), v("Doodh", "", "360")),
            parse("chawal 1450 doodh 360")
        )

    @Test fun fast_with_quantities() =
        assertEquals(
            listOf(v("Dahi", "½ pao", "80"), v("Anday", "1 darjan", "300")),
            parse("aadha pao dahi 80 ek darjan anday 300 ke")
        )

    @Test fun count_before_name() =
        assertEquals(listOf(v("Bread", "2", "300")), parse("2 bread 300"))

    @Test fun spoken_hundreds() =
        assertEquals(listOf(v("Cheeni", "1½ kilo", "300")), parse("cheeni dedh kilo teen sau"))

    @Test fun price_word() =
        assertEquals(listOf(v("Tamatar", "1 kilo", "120")), parse("tamatar 1 kilo daam 120"))

    @Test fun rs_before_number() =
        assertEquals(listOf(v("Oil", "1 liter", "650")), parse("oil 1 liter rs 650"))

    @Test fun urdu_script() =
        assertEquals(listOf(v("چاول", "5 کلو", "1450")), parse("چاول 5 کلو 1450 روپے"))

    @Test fun hindi_script() =
        assertEquals(listOf(v("दूध", "2 पैकेट", "120")), parse("दूध 2 पैकेट 120 रुपये"))

    @Test fun arabic_script() =
        assertEquals(listOf(v("أرز", "2 كيلو", "25")), parse("أرز 2 كيلو 25 ريال"))
}

class ReceiptParserTest {
    private fun r(name: String, qty: String, price: Double) = ReceiptItem(name, qty, price)

    @Test fun typical_receipt() {
        val res = ReceiptParser.parse(
            listOf(
                "AL MADINA SUPER STORE",
                "Main Market Gulberg",
                "Date: 12/11/2026  Time 18:22",
                "Item  Qty  Rate  Amount",
                "RICE BASMATI 5KG   1,450.00",
                "Milk Pack 1L  2  180.00  360.00",
                "SUGAR",
                "2 x 160.00   320.00",
                "Eggs 12  300",
                "Bread 150.00 150.00",
                "Sub Total  2,580.00",
                "GST 0.00",
                "TOTAL  2,580.00",
                "Cash 3000.00",
                "Change 420.00"
            )
        )
        assertEquals("Al Madina Super Store", res.store)
        assertEquals("2026-11-12", res.date)
        assertEquals(
            listOf(
                r("Rice Basmati", "5 kg", 1450.0),
                r("Milk Pack 1L", "2", 360.0),
                r("Sugar", "2", 320.0),
                r("Eggs", "12", 300.0),
                r("Bread", "1", 150.0)
            ),
            res.items
        )
        assertEquals(2580.0, res.total, 0.001)
    }

    @Test fun rate_and_amount() {
        val res = ReceiptParser.parse(listOf("Shop", "Tomato 1.250 200.00 250.00", "Oil 650.00 1,300.00"))
        assertEquals(listOf(r("Tomato", "1.25", 250.0), r("Oil", "2", 1300.0)), res.items)
    }

    @Test fun times_without_line_total() {
        val res = ReceiptParser.parse(listOf("Store", "Juice 3 x 120.00"))
        assertEquals(listOf(r("Juice", "3", 360.0)), res.items)
    }
}
