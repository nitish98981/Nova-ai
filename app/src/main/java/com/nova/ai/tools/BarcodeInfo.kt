package com.nova.ai.tools

import android.net.Uri
import com.google.mlkit.vision.barcode.common.Barcode
import com.nova.ai.brain.Http
import java.util.Locale
import org.json.JSONObject

/** What NOVA found in a barcode or QR code, ready to show and speak. */
class ScanResult(
    val title: String,
    val lines: List<String>,
    val spoken: String,
    val raw: String,
    val upi: String? = null,
    val url: String? = null,
    val phone: String? = null,
    val geo: String? = null,
    val needsPhoto: Boolean = false
)

object BarcodeInfo {

    suspend fun describe(b: Barcode): ScanResult {
        val raw = b.rawValue.orEmpty()
        val low = raw.lowercase(Locale.ROOT)
        return when {
            low.startsWith("upi://") -> upi(raw)
            b.valueType == Barcode.TYPE_URL || low.startsWith("http://") || low.startsWith("https://") -> {
                val link = b.url?.url ?: raw
                ScanResult(
                    "Website ka link",
                    listOf(link, "Kholne se pehle dekh lo ki link bharose ka hai."),
                    "Ye ek website ka QR hai.",
                    raw, url = link
                )
            }
            b.valueType == Barcode.TYPE_WIFI -> {
                val w = b.wifi
                val type = when (w?.encryptionType) {
                    Barcode.WiFi.TYPE_OPEN -> "Open"
                    Barcode.WiFi.TYPE_WPA -> "WPA"
                    Barcode.WiFi.TYPE_WEP -> "WEP"
                    else -> "Pata nahi"
                }
                ScanResult(
                    "WiFi QR",
                    listOfNotNull("Network: ${w?.ssid.orEmpty()}", w?.password?.takeIf { it.isNotBlank() }?.let { "Password: $it" }, "Security: $type"),
                    "Ye WiFi ka QR hai. Network ka naam ${w?.ssid.orEmpty()} hai.",
                    raw
                )
            }
            b.valueType == Barcode.TYPE_PHONE -> {
                val n = b.phone?.number.orEmpty()
                ScanResult("Phone number", listOf(n), "Isme ek phone number hai: $n.", raw, phone = n)
            }
            b.valueType == Barcode.TYPE_SMS -> ScanResult(
                "SMS",
                listOfNotNull("Number: ${b.sms?.phoneNumber.orEmpty()}", b.sms?.message?.takeIf { it.isNotBlank() }?.let { "Message: $it" }),
                "Isme ek SMS ka sandesh hai.",
                raw, phone = b.sms?.phoneNumber
            )
            b.valueType == Barcode.TYPE_EMAIL -> ScanResult(
                "Email",
                listOfNotNull("Address: ${b.email?.address.orEmpty()}", b.email?.subject?.takeIf { it.isNotBlank() }?.let { "Subject: $it" }),
                "Isme ek email address hai: ${b.email?.address.orEmpty()}.",
                raw
            )
            b.valueType == Barcode.TYPE_GEO -> {
                val lat = b.geoPoint?.lat
                val lng = b.geoPoint?.lng
                ScanResult("Jagah (map)", listOf("Latitude $lat, Longitude $lng"), "Isme ek jagah ka location hai.", raw, geo = "geo:$lat,$lng")
            }
            b.valueType == Barcode.TYPE_CONTACT_INFO -> {
                val c = b.contactInfo
                val name = c?.name?.formattedName.orEmpty()
                ScanResult(
                    "Contact card",
                    listOfNotNull(
                        name.takeIf { it.isNotBlank() }?.let { "Naam: $it" },
                        c?.organization?.takeIf { it.isNotBlank() }?.let { "Company: $it" },
                        c?.phones?.firstOrNull()?.number?.let { "Phone: $it" },
                        c?.emails?.firstOrNull()?.address?.let { "Email: $it" }
                    ),
                    "Ye ek contact card hai${if (name.isNotBlank()) ", $name ka" else ""}.",
                    raw, phone = c?.phones?.firstOrNull()?.number
                )
            }
            b.valueType == Barcode.TYPE_ISBN -> isbn(raw)
            b.valueType == Barcode.TYPE_PRODUCT || b.format in PRODUCT_FORMATS -> product(raw)
            else -> ScanResult(
                "Text",
                listOf(raw),
                "Isme ye likha hai: ${raw.take(160)}",
                raw
            )
        }
    }

    private val PRODUCT_FORMATS = setOf(
        Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E
    )

    private fun upi(raw: String): ScanResult {
        val uri = Uri.parse(raw)
        val payee = uri.getQueryParameter("pn").orEmpty()
        val id = uri.getQueryParameter("pa").orEmpty()
        val amount = uri.getQueryParameter("am").orEmpty()
        val note = uri.getQueryParameter("tn").orEmpty()
        val lines = ArrayList<String>()
        lines += "Payee: " + payee.ifBlank { "naam nahi diya gaya" }
        lines += "UPI ID: " + id.ifBlank { "nahi mili" }
        lines += "Amount: " + if (amount.isNotBlank()) "₹$amount" else "QR mein nahi hai, tum payment app mein daaloge"
        if (note.isNotBlank()) lines += "Note: $note"
        lines += "Payment se pehle naam aur UPI ID khud check karo. NOVA payment confirm nahi karti, wo tum apne payment app mein hi karoge."
        val spoken = "Ye UPI payment QR hai. Payee ${payee.ifBlank { "naam nahi diya gaya" }}, UPI ID $id." +
            (if (amount.isNotBlank()) " Amount $amount rupaye." else "")
        return ScanResult("UPI payment QR", lines, spoken, raw, upi = raw)
    }

    private suspend fun product(code: String): ScanResult {
        val body = Http.getJson(
            "https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name,generic_name,brands,quantity",
            mapOf("User-Agent" to "NOVA-AI/0.1 (Android)")
        )
        val p = body?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.takeIf { it.optInt("status") == 1 }?.optJSONObject("product")
        val name = p?.optString("product_name").orEmpty().ifBlank { p?.optString("generic_name").orEmpty() }
        val brand = p?.optString("brands").orEmpty()
        val qty = p?.optString("quantity").orEmpty()
        return if (name.isNotBlank()) {
            val title = listOf(brand, name).filter { it.isNotBlank() }.joinToString(" ")
            ScanResult(
                title,
                listOfNotNull("Barcode: $code", qty.takeIf { it.isNotBlank() }?.let { "Matra: $it" }, "Source: Open Food Facts (free database)"),
                "Ye $title hai.",
                code
            )
        } else {
            ScanResult(
                "Product barcode: $code",
                listOf("Is barcode ka naam free database (Open Food Facts) mein nahi mila ya internet nahi hai. Ye khane-peene se alag cheez ho sakti hai."),
                "Barcode mil gaya par naam nahi mila. Photo se pehchanne ke liye Pehchano dabao.",
                code, needsPhoto = true
            )
        }
    }

    private suspend fun isbn(code: String): ScanResult {
        val body = Http.getJson("https://openlibrary.org/api/books?bibkeys=ISBN:$code&format=json&jscmd=data")
        val book = body?.let { runCatching { JSONObject(it) }.getOrNull() }?.optJSONObject("ISBN:$code")
        val title = book?.optString("title").orEmpty()
        val authors = book?.optJSONArray("authors")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("name") }.joinToString(", ")
        }.orEmpty()
        return if (title.isNotBlank()) {
            ScanResult(
                title,
                listOfNotNull("ISBN: $code", authors.takeIf { it.isNotBlank() }?.let { "Lekhak: $it" }, "Source: Open Library"),
                "Ye kitab hai: $title.",
                code
            )
        } else {
            ScanResult("Kitab ka barcode", listOf("ISBN: $code", "Naam nahi mila."), "Ye kitab ka barcode hai par naam nahi mila.", code)
        }
    }
}
