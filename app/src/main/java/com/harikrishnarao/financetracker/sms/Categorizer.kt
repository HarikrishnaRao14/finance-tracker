package com.harikrishnarao.financetracker.sms

import com.harikrishnarao.financetracker.domain.Category
import com.harikrishnarao.financetracker.domain.TxnType

/**
 * Picks a category for a transaction.
 * Order: the user's own learned rules first, then built-in keywords, then a safe default.
 */
object Categorizer {

    private val keywords: List<Pair<Category, List<String>>> = listOf(
        Category.FOOD to listOf(
            "swiggy", "zomato", "eatsure", "domino", "pizza", "mcdonald", "kfc", "burger", "subway",
            "starbucks", "cafe", "coffee", "chai", "restaurant", "hotel", "dhaba", "bakery", "biryani",
            "food", "blinkit", "zepto", "instamart", "bigbasket", "dmart", "grocer", "kirana", "dairy", "milk",
        ),
        Category.TRAVEL to listOf(
            "uber", "ola", "rapido", "irctc", "railway", "metro", "redbus", "makemytrip", "goibibo", "ixigo",
            "indigo", "air india", "akasa", "spicejet", "petrol", "fuel", "hpcl", "bpcl", "iocl", "indian oil",
            "fastag", "parking", "toll", "cab",
        ),
        Category.SHOPPING to listOf(
            "amazon", "amzn", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tatacliq", "croma",
            "reliance digital", "decathlon", "ikea", "lifestyle", "westside", "trends", "mall", "store", "mart",
        ),
        Category.BILLS to listOf(
            "electricity", "mseb", "mahadiscom", "bescom", "tneb", "tata power", "adani", "bill", "recharge",
            "airtel", "jio", "vodafone", "vi ", "bsnl", "broadband", "act fibernet", "hathway", "dth", "tata play",
            "gas", "indane", "hp gas", "bharat gas", "water", "maintenance", "society", "rent", "emi", "loan",
            "insurance", "lic", "premium",
        ),
        Category.HEALTH to listOf(
            "pharma", "pharmacy", "medical", "medic", "apollo", "1mg", "netmeds", "pharmeasy", "hospital",
            "clinic", "diagnostic", "lab", "dr ", "doctor", "health", "gym", "cult",
        ),
        Category.ENTERTAINMENT to listOf(
            "netflix", "hotstar", "jiocinema", "prime video", "spotify", "youtube", "zee5", "sonyliv",
            "bookmyshow", "pvr", "inox", "cinema", "movie", "steam", "playstation", "gaming",
        ),
    )

    /** Normalises a merchant into a stable key for learned rules: "SWIGGY8@ybl" -> "swiggy". */
    fun merchantKey(merchant: String?): String? {
        if (merchant.isNullOrBlank()) return null
        val base = merchant.lowercase().substringBefore('@')
        val cleaned = base.replace(Regex("""[^a-z ]"""), " ").replace(Regex("""\s+"""), " ").trim()
        return cleaned.ifEmpty { null }
    }

    fun categorise(
        merchant: String?,
        type: TxnType,
        isRefund: Boolean,
        learnedRules: Map<String, Category>,
    ): Category {
        val key = merchantKey(merchant)
        if (key != null) learnedRules[key]?.let { return it }

        // Only the merchant is searched (never the whole SMS: bank footers mention "loan", "card"...),
        // and a keyword must start a word, so "current" never matches "rent".
        val haystack = " " + merchant.orEmpty().lowercase().substringBefore('@')
            .replace(Regex("""[^a-z0-9 ]"""), " ").replace(Regex("""\s+"""), " ").trim() + " "
        val byKeyword = keywords.firstOrNull { (_, words) ->
            words.any { haystack.contains(" " + it) }
        }?.first

        return when {
            type == TxnType.CREDIT && isRefund -> byKeyword ?: Category.OTHER
            type == TxnType.CREDIT -> Category.INCOME
            byKeyword != null -> byKeyword
            else -> Category.UNCATEGORISED
        }
    }
}
