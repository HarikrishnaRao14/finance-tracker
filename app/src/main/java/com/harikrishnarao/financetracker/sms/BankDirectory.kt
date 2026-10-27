package com.harikrishnarao.financetracker.sms

/**
 * Indian bank SMS come from registered (DLT) sender headers like "AX-HDFCBK" or "JD-HDFCBK-S".
 * The 2-letter prefix is the telecom route and the optional suffix (-S/-T/-P/-G) is the
 * message type, so only the middle "core code" identifies the bank.
 *
 * This list is best-effort. Banks not in it are still found: any alphanumeric sender whose
 * messages parse as account transactions is offered to the user as an "Other bank".
 */
object BankDirectory {

    private val banks: Map<String, String> = buildMap {
        fun add(name: String, vararg codes: String) = codes.forEach { put(it, name) }
        add("HDFC Bank", "HDFCBK", "HDFCBN", "HDFCCC")
        add("State Bank of India", "SBIINB", "SBIPSG", "SBIUPI", "ATMSBI", "CBSSBI")
        add("SBI Card", "SBICRD")
        add("ICICI Bank", "ICICIB", "ICICIT", "ICICIO")
        add("Axis Bank", "AXISBK", "AXISMR")
        add("Kotak Mahindra Bank", "KOTAKB")
        add("Punjab National Bank", "PNBSMS", "PNBBNK")
        add("Bank of Baroda", "BOBTXN", "BOBSMS", "BARODA")
        add("Canara Bank", "CANBNK", "CANARA")
        add("Union Bank of India", "UNIONB")
        add("Bank of India", "BOIIND", "BOISMS")
        add("Bank of Maharashtra", "MAHABK")
        add("Central Bank of India", "CENTBK")
        add("Indian Bank", "INDBNK")
        add("Indian Overseas Bank", "IOBCHN", "IOBBNK")
        add("UCO Bank", "UCOBNK")
        add("Punjab & Sind Bank", "PSBANK")
        add("IDBI Bank", "IDBIBK")
        add("IDFC FIRST Bank", "IDFCFB", "IDFCBK")
        add("IndusInd Bank", "INDUSB")
        add("Yes Bank", "YESBNK", "YESBKL")
        add("Federal Bank", "FEDBNK", "FEDFIB")
        add("RBL Bank", "RBLBNK", "RBLCRD")
        add("AU Small Finance Bank", "AUBANK")
        add("Bandhan Bank", "BANDHN")
        add("Ujjivan SFB", "UJJIVN")
        add("Standard Chartered", "SCBANK", "STANCB")
        add("HSBC", "HSBCIN")
        add("Citibank", "CITIBK")
        add("DBS Bank", "DBSBNK")
        add("South Indian Bank", "SIBSMS")
        add("Karnataka Bank", "KBLBNK")
        add("Karur Vysya Bank", "KVBANK")
        add("Tamilnad Mercantile Bank", "TMBANK")
        add("J&K Bank", "JKBANK")
        add("Dhanlaxmi Bank", "DHANBK")
        add("India Post Payments Bank", "IPBMSG", "IPPBNK")
        add("Airtel Payments Bank", "AIRBNK")
        add("Paytm Payments Bank", "PAYTMB")
        add("Fino Payments Bank", "FINOBK")
        add("OneCard", "ONECRD")
        add("American Express", "AMEXIN")
    }

    /**
     * Returns the core sender code, or null for senders that can never be a bank
     * (plain phone numbers: anyone could text "Rs 5000 debited" from a personal number).
     */
    fun coreCode(address: String?): String? {
        val a = address?.trim()?.uppercase() ?: return null
        if (a.none { it.isLetter() }) return null
        val parts = a.split('-').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val core = if (parts.size >= 2 && parts[0].length == 2) parts[1] else parts[0]
        return core.takeIf { it.length in 3..11 && it.all { c -> c.isLetterOrDigit() } }
    }

    fun bankName(code: String): String? = banks[code]

    fun isKnownBank(code: String): Boolean = code in banks
}
