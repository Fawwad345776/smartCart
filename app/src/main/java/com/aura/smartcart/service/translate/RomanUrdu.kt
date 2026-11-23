package com.aura.smartcart.service.translate

import java.util.Locale

/**
 * Roman Urdu grocery words to English ("2 kilo chawal" -> "2 kilo Rice"). Only whole known words
 * are replaced; English words, brand names and numbers stay as they are.
 */
object RomanUrdu {

    /** Two-word names first, so "band gobhi" is cabbage, not "band cauliflower". */
    private val PHRASES = mapOf(
        "band gobhi" to "cabbage", "phool gobhi" to "cauliflower", "double roti" to "bread",
        "hari mirch" to "green chilli", "lal mirch" to "red chilli", "laal mirch" to "red chilli",
        "kali mirch" to "black pepper", "shimla mirch" to "capsicum", "kala chana" to "black chickpeas",
        "safed chana" to "white chickpeas", "chai patti" to "tea leaves",
        "hara dhania" to "fresh coriander", "moong daal" to "mung lentils", "masoor daal" to "red lentils",
        "chana daal" to "split chickpeas", "mash daal" to "black lentils", "khana pakane ka tel" to "cooking oil",
        "aaloo bukhara" to "plum", "aloo bukhara" to "plum"
    )

    private val WORDS = mapOf(
        // staples
        "chawal" to "rice", "chaawal" to "rice", "chaval" to "rice",
        "cheeni" to "sugar", "chini" to "sugar", "shakar" to "sugar",
        "aata" to "flour", "atta" to "flour", "maida" to "all-purpose flour", "besan" to "gram flour",
        "sooji" to "semolina", "suji" to "semolina", "sewaiyan" to "vermicelli", "sevaiyan" to "vermicelli",
        "daal" to "lentils", "dal" to "lentils", "chana" to "chickpeas", "chane" to "chickpeas",
        "masoor" to "red lentils", "moong" to "mung beans", "lobia" to "black-eyed beans", "rajma" to "kidney beans",
        "tel" to "oil", "namak" to "salt", "sirka" to "vinegar", "shehad" to "honey",
        "roti" to "flatbread",
        // dairy, meat, eggs
        "doodh" to "milk", "dudh" to "milk", "dahi" to "yogurt", "makhan" to "butter", "malai" to "cream",
        "paneer" to "cottage cheese", "panir" to "cottage cheese",
        "anda" to "egg", "anday" to "eggs", "ande" to "eggs",
        "gosht" to "meat", "qeema" to "mince", "keema" to "mince", "murghi" to "chicken", "murgi" to "chicken",
        "machli" to "fish", "machhli" to "fish", "bakra" to "mutton",
        // vegetables
        "sabzi" to "vegetables", "aloo" to "potato", "aaloo" to "potato", "pyaz" to "onion", "piyaz" to "onion",
        "payaz" to "onion", "tamatar" to "tomato", "lehsan" to "garlic", "lahsan" to "garlic", "adrak" to "ginger",
        "mirch" to "chilli", "mirchi" to "chilli", "gajar" to "carrot", "gobhi" to "cauliflower", "bhindi" to "okra",
        "baingan" to "eggplant", "bengan" to "eggplant", "kaddu" to "pumpkin", "lauki" to "bottle gourd",
        "ghiya" to "bottle gourd", "tori" to "ridge gourd", "karela" to "bitter gourd", "palak" to "spinach",
        "saag" to "mustard greens", "matar" to "peas", "mooli" to "radish", "shaljam" to "turnip",
        "kheera" to "cucumber", "khira" to "cucumber", "dhania" to "coriander", "dhaniya" to "coriander",
        "podina" to "mint", "pudina" to "mint", "leemu" to "lemon", "limu" to "lemon", "nimbu" to "lemon",
        "arvi" to "taro", "shakarkandi" to "sweet potato",
        // fruit
        "phal" to "fruit", "kela" to "banana", "kelay" to "bananas", "kele" to "bananas", "seb" to "apple",
        "aam" to "mango", "santra" to "orange", "malta" to "orange", "kinnu" to "kinnow", "angoor" to "grapes",
        "anaar" to "pomegranate", "anar" to "pomegranate", "amrood" to "guava", "tarbooz" to "watermelon",
        "kharbooza" to "melon", "khajoor" to "dates", "khajur" to "dates", "alubukhara" to "plum",
        "nashpati" to "pear", "cheeku" to "sapodilla", "papeeta" to "papaya",
        // spices, tea, dry fruit
        "masala" to "spices", "haldi" to "turmeric", "zeera" to "cumin", "jeera" to "cumin",
        "elaichi" to "cardamom", "darchini" to "cinnamon", "laung" to "cloves", "rai" to "mustard seeds",
        "methi" to "fenugreek", "ajwain" to "carom seeds", "saunf" to "fennel", "imli" to "tamarind",
        "chai" to "tea", "patti" to "tea leaves", "badam" to "almonds", "kaju" to "cashews",
        "pista" to "pistachios", "akhrot" to "walnuts", "kishmish" to "raisins", "chilgoza" to "pine nuts",
        // household
        "sabun" to "soap", "sarf" to "detergent", "manjan" to "toothpowder", "machis" to "matches",
        "mombatti" to "candles", "jharu" to "broom", "bartan" to "dishes", "pani" to "water",
        // describing words
        "kala" to "black", "kali" to "black", "safed" to "white", "lal" to "red", "laal" to "red",
        "hara" to "green", "hari" to "green", "peela" to "yellow", "desi" to "local", "taza" to "fresh",
        "taaza" to "fresh", "sukha" to "dried", "bara" to "large", "chhota" to "small", "chota" to "small"
    )

    fun toEnglish(text: String): String {
        var out = text
        for ((ur, en) in PHRASES) {
            out = out.replace(Regex("(?i)(?<![\\p{L}])${Regex.escape(ur)}(?![\\p{L}])"), en)
        }
        return Regex("[\\p{L}]+").replace(out) { m ->
            val en = WORDS[m.value.lowercase(Locale.ROOT)] ?: return@replace m.value
            // Keep the user's capital letter: "Chawal" -> "Rice".
            if (m.value.first().isUpperCase()) en.replaceFirstChar { it.uppercase() } else en
        }
    }
}
