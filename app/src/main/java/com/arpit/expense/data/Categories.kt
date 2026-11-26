package com.arpit.expense.data

/** Spending categories with an icon name and a color, plus keyword auto-detection. */
object Categories {

    data class Cat(val name: String, val icon: String, val color: Long)

    val ALL = listOf(
        Cat("Food & Dining", "restaurant", 0xFFE2683A),
        Cat("Groceries", "shopping_cart", 0xFF2E9E6B),
        Cat("Shopping", "shopping_bag", 0xFF7A6FF0),
        Cat("Fuel", "local_gas_station", 0xFFC2410C),
        Cat("Travel", "directions_car", 0xFF2563EB),
        Cat("Bills & Recharge", "receipt_long", 0xFF0EA5A5),
        Cat("Entertainment", "movie", 0xFFD4537E),
        Cat("Health", "local_hospital", 0xFFDC2626),
        Cat("Cash / ATM", "payments", 0xFF6B7280),
        Cat("Transfer", "swap_horiz", 0xFF8B8B8B),
        Cat("Income", "trending_up", 0xFF16A34A),
        Cat("Other", "category", 0xFF9CA3AF),
    )

    fun of(name: String): Cat = ALL.firstOrNull { it.name == name } ?: ALL.last()

    val names = ALL.map { it.name }

    private val KEYWORDS: List<Pair<String, List<String>>> = listOf(
        "Food & Dining" to listOf("swiggy", "zomato", "domino", "pizza", "restaurant", "cafe", "coffee", "mcdonald", "kfc", "burger", "biryani", "eatfit", "faasos", "food"),
        "Groceries" to listOf("bigbasket", "blinkit", "zepto", "grofers", "instamart", "dmart", "d-mart", "grocery", "kirana", "supermarket", "reliance fresh", "more retail"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tatacliq", "mall", "lifestyle", "pantaloons", "decathlon", "store"),
        "Fuel" to listOf("petrol", "fuel", "hpcl", "iocl", "bpcl", "indian oil", "bharat petroleum", "hindustan petroleum", "shell", "petrol pump", "filling station"),
        "Travel" to listOf("uber", "ola", "rapido", "irctc", "redbus", "makemytrip", "goibibo", "ixigo", "flight", "indigo", "vistara", "metro", "railway", "toll", "fastag"),
        "Bills & Recharge" to listOf("electricity", "recharge", "airtel", "jio", "vodafone", " vi ", "broadband", "dth", "tatasky", "bescom", "water bill", "gas bill", "bill pay", "billdesk", "postpaid", "prepaid"),
        "Entertainment" to listOf("netflix", "spotify", "hotstar", "bookmyshow", "prime video", "youtube", "jiocinema", "pvr", "inox", "game"),
        "Health" to listOf("pharmacy", "apollo", "medplus", "hospital", "clinic", "medicine", "1mg", "pharmeasy", "netmeds", "diagnostic", "lab", "doctor"),
        "Cash / ATM" to listOf("atm", "cash withdrawal", "cash wdl", "atw"),
    )

    /** Guess a category from the merchant text and full message body. */
    fun guess(text: String, isCredit: Boolean): String {
        val t = text.lowercase()
        if (isCredit) {
            if (listOf("salary", "sal cr", "neft cr").any { t.contains(it) }) return "Income"
        }
        for ((cat, words) in KEYWORDS) {
            if (words.any { t.contains(it) }) return cat
        }
        if (isCredit) return "Income"
        if (t.contains("upi") || t.contains("imps") || t.contains("neft") || t.contains("@")) return "Transfer"
        return "Other"
    }
}
