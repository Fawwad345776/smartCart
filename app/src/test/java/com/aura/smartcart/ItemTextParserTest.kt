package com.aura.smartcart

import com.aura.smartcart.util.ItemTextParser
import com.aura.smartcart.util.ParsedItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemTextParserTest {

    private fun parse(text: String) = ItemTextParser.parse(text)
    private fun item(name: String, qty: String = "") = ParsedItem(name, qty)

    @Test fun pao_with_digit() =
        assertEquals(listOf(item("Dahi", "1 pao")), parse("1 pao dahi"))

    @Test fun aadha_pao() =
        assertEquals(listOf(item("Dahi", "½ pao")), parse("aadha pao dahi"))

    @Test fun pao_after_name() =
        assertEquals(listOf(item("Dahi", "½ pao")), parse("dahi aadha pao"))

    @Test fun ek_pao_words() =
        assertEquals(listOf(item("Qeema", "1 pao")), parse("ek pao qeema"))

    @Test fun dedh_and_dhai() =
        assertEquals(
            listOf(item("Cheeni", "1½ kilo"), item("Chawal", "2½ kilo")),
            parse("dedh kilo cheeni aur dhai kilo chawal")
        )

    @Test fun fast_speech_without_and_leading_quantities() =
        assertEquals(
            listOf(item("Dahi", "½ pao"), item("Cheeni", "1 kilo"), item("Anday", "2 darjan")),
            parse("aadha pao dahi ek kilo cheeni do darjan anday")
        )

    @Test fun fast_speech_without_and_trailing_quantities() =
        assertEquals(
            listOf(item("Dahi", "½ pao"), item("Cheeni", "1 kilo")),
            parse("dahi aadha pao cheeni ek kilo")
        )

    @Test fun english_mix() =
        assertEquals(
            listOf(item("Milk"), item("Rice", "2 kg"), item("Eggs", "1 dozen")),
            parse("milk, 2kg rice and a dozen eggs")
        )

    @Test fun fillers_removed() =
        assertEquals(listOf(item("Doodh", "1 packet")), parse("mujhe ek packet doodh chahiye"))

    @Test fun urdu_script() =
        assertEquals(
            listOf(item("دہی", "½ پاؤ"), item("چاول", "2 کلو")),
            parse("آدھا پاؤ دہی اور دو کلو چاول")
        )

    @Test fun hindi_script() =
        assertEquals(
            listOf(item("दही", "1 पाव"), item("चीनी", "½ किलो")),
            parse("एक पाव दही और आधा किलो चीनी")
        )

    @Test fun arabic_script() =
        assertEquals(
            listOf(item("سكر", "½ كيلو"), item("أرز", "2 كيلو")),
            parse("نصف كيلو سكر و اثنين كيلو أرز")
        )

    @Test fun plain_names() =
        assertEquals(listOf(item("Doodh"), item("Bread"), item("Anday")), parse("doodh, bread aur anday"))

    @Test fun scanned_lines_with_bullets() =
        assertEquals(
            listOf(item("Rice", "5 kg"), item("Sugar")),
            ItemTextParser.parseLines(listOf("1. Rice 5 kg", "- sugar"))
        )
}
