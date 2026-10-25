package com.example.alarmclock

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import java.text.Collator
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

class WorldClockActivity : BaseActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("world_clocks", MODE_PRIVATE) }

    private lateinit var list: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var countrySpinner: Spinner
    private lateinit var citySpinner: Spinner
    private lateinit var searchInput: EditText
    private lateinit var countryAdapter: StyledAdapter
    private lateinit var cityAdapter: StyledAdapter
    private lateinit var heroClock: AnalogClockView
    private lateinit var heroTime: TextView
    private lateinit var heroDate: TextView
    private lateinit var heroZone: TextView
    private lateinit var scroll: ScrollView

    private var filteredCountries: List<CountryChoice> = emptyList()
    private var citiesForCountry: List<ZoneChoice> = emptyList()
    private var shownCountryCode: String? = null

    private val selected = linkedSetOf<String>()
    private val rows = linkedMapOf<String, CityRow>()
    private val zoneCache = HashMap<String, TimeZone?>()
    private val scratch: Calendar = GregorianCalendar()

    private data class ZoneChoice(val id: String, val city: String, val countryCode: String)
    private data class CountryChoice(val code: String, val faName: String, val enName: String, val flag: String)
    private class CityRow(
        val zone: TimeZone,
        val root: View,
        val clock: AnalogClockView,
        val time: TextView,
        val date: TextView,
        val hint: TextView,
        var isDay: Boolean?
    )

    private val cBg by lazy { getColor(R.color.app_background) }
    private val cSurface by lazy { getColor(R.color.surface_primary) }
    private val cElevated by lazy { getColor(R.color.surface_elevated) }
    private val cBorder by lazy { getColor(R.color.surface_border) }
    private val cPrimary by lazy { getColor(R.color.text_primary) }
    private val cSecondary by lazy { getColor(R.color.text_secondary) }
    private val cCyan by lazy { getColor(R.color.accent_cyan) }
    private val cViolet by lazy { getColor(R.color.accent_violet) }
    private val cDanger by lazy { getColor(R.color.danger) }

    // Only IANA ids that really exist. (The old list contained America/Atlanta and
    // America/Miami, which are not valid zones; Android silently showed GMT for them.)
    private val allZones: List<ZoneChoice> = listOf(
            ZoneChoice("Asia/Tehran", "تهران", "IR"),
            ZoneChoice("Europe/London", "لندن", "GB"),
            ZoneChoice("Europe/Paris", "پاریس", "FR"),
            ZoneChoice("Europe/Berlin", "برلین", "DE"),
            ZoneChoice("Europe/Rome", "رم", "IT"),
            ZoneChoice("Europe/Madrid", "مادرید", "ES"),
            ZoneChoice("Europe/Amsterdam", "آمستردام", "NL"),
            ZoneChoice("Europe/Athens", "آتن", "GR"),
            ZoneChoice("Europe/Istanbul", "استانبول", "TR"),
            ZoneChoice("Europe/Moscow", "مسکو", "RU"),
            ZoneChoice("Europe/Kyiv", "کی‌یف", "UA"),
            ZoneChoice("Africa/Cairo", "قاهره", "EG"),
            ZoneChoice("Africa/Johannesburg", "ژوهانسبورگ", "ZA"),
            ZoneChoice("Asia/Dubai", "دبی", "AE"),
            ZoneChoice("Asia/Riyadh", "ریاض", "SA"),
            ZoneChoice("Asia/Baghdad", "بغداد", "IQ"),
            ZoneChoice("Asia/Kuwait", "کویت", "KW"),
            ZoneChoice("Asia/Qatar", "دوحه", "QA"),
            ZoneChoice("Asia/Jerusalem", "اورشلیم", "IL"),
            ZoneChoice("Asia/Kabul", "کابل", "AF"),
            ZoneChoice("Asia/Karachi", "کراچی", "PK"),
            ZoneChoice("Asia/Kolkata", "دهلی / کلکته", "IN"),
            ZoneChoice("Asia/Dhaka", "داکا", "BD"),
            ZoneChoice("Asia/Kathmandu", "کاتماندو", "NP"),
            ZoneChoice("Asia/Bangkok", "بانکوک", "TH"),
            ZoneChoice("Asia/Jakarta", "جاکارتا", "ID"),
            ZoneChoice("Asia/Singapore", "سنگاپور", "SG"),
            ZoneChoice("Asia/Hong_Kong", "هنگ‌کنگ", "HK"),
            ZoneChoice("Asia/Shanghai", "شانگهای", "CN"),
            ZoneChoice("Asia/Tokyo", "توکیو", "JP"),
            ZoneChoice("Asia/Seoul", "سئول", "KR"),
            ZoneChoice("Australia/Sydney", "سیدنی", "AU"),
            ZoneChoice("Australia/Melbourne", "ملبورن", "AU"),
            ZoneChoice("Pacific/Auckland", "اوکلند", "NZ"),
            ZoneChoice("America/New_York", "نیویورک", "US"),
            ZoneChoice("America/Chicago", "شیکاگو", "US"),
            ZoneChoice("America/Denver", "دنور", "US"),
            ZoneChoice("America/Los_Angeles", "لس‌آنجلس", "US"),
            ZoneChoice("America/Toronto", "تورنتو", "CA"),
            ZoneChoice("America/Vancouver", "ونکوور", "CA"),
            ZoneChoice("America/Mexico_City", "مکزیکوسیتی", "MX"),
            ZoneChoice("America/Sao_Paulo", "سائوپائولو", "BR"),
            ZoneChoice("America/Argentina/Buenos_Aires", "بوئنوس آیرس", "AR"),
            ZoneChoice("Atlantic/Reykjavik", "ریکیاویک", "IS"),
            ZoneChoice("Europe/Lisbon", "لیسبون", "PT"),
            ZoneChoice("Europe/Vienna", "وین", "AT"),
            ZoneChoice("Europe/Prague", "پراگ", "CZ"),
            ZoneChoice("Europe/Warsaw", "ورشو", "PL"),
            ZoneChoice("Europe/Stockholm", "استکهلم", "SE"),
            ZoneChoice("Europe/Oslo", "اسلو", "NO"),
            ZoneChoice("Europe/Copenhagen", "کپنهاگ", "DK"),
            ZoneChoice("Europe/Helsinki", "هلسینکی", "FI"),
            ZoneChoice("Europe/Zurich", "زوریخ", "CH"),
            ZoneChoice("Europe/Brussels", "بروکسل", "BE"),
            ZoneChoice("Europe/Bucharest", "بخارست", "RO"),
            ZoneChoice("Europe/Sofia", "صوفیه", "BG"),
            ZoneChoice("Europe/Belgrade", "بلگراد", "RS"),
            ZoneChoice("Europe/Budapest", "بوداپست", "HU"),
            ZoneChoice("Europe/Dublin", "دوبلین", "IE"),
            ZoneChoice("Europe/Minsk", "مینسک", "BY"),
            ZoneChoice("Africa/Casablanca", "کازابلانکا", "MA"),
            ZoneChoice("Africa/Tunis", "تونس", "TN"),
            ZoneChoice("Africa/Algiers", "الجزیره", "DZ"),
            ZoneChoice("Africa/Nairobi", "نایروبی", "KE"),
            ZoneChoice("Africa/Lagos", "لاگوس", "NG"),
            ZoneChoice("Africa/Accra", "آکرا", "GH"),
            ZoneChoice("Africa/Addis_Ababa", "آدیس‌آبابا", "ET"),
            ZoneChoice("Asia/Yerevan", "ایروان", "AM"),
            ZoneChoice("Asia/Baku", "باکو", "AZ"),
            ZoneChoice("Asia/Tbilisi", "تفلیس", "GE"),
            ZoneChoice("Asia/Almaty", "آلماتی", "KZ"),
            ZoneChoice("Asia/Tashkent", "تاشکند", "UZ"),
            ZoneChoice("Asia/Dushanbe", "دوشنبه", "TJ"),
            ZoneChoice("Asia/Bishkek", "بیشکک", "KG"),
            ZoneChoice("Asia/Ulaanbaatar", "اولان‌باتور", "MN"),
            ZoneChoice("Asia/Manila", "مانیل", "PH"),
            ZoneChoice("Asia/Ho_Chi_Minh", "هوشی‌مین", "VN"),
            ZoneChoice("Asia/Kuala_Lumpur", "کوالالامپور", "MY"),
            ZoneChoice("Asia/Phnom_Penh", "پنوم‌پن", "KH"),
            ZoneChoice("Asia/Yangon", "یانگون", "MM"),
            ZoneChoice("Asia/Colombo", "کلمبو", "LK"),
            ZoneChoice("Asia/Muscat", "مسقط", "OM"),
            ZoneChoice("Asia/Amman", "عمان", "JO"),
            ZoneChoice("Asia/Beirut", "بیروت", "LB"),
            ZoneChoice("Asia/Damascus", "دمشق", "SY"),
            ZoneChoice("Asia/Novosibirsk", "نووسیبیرسک", "RU"),
            ZoneChoice("Asia/Vladivostok", "ولادی‌وستوک", "RU"),
            ZoneChoice("Australia/Brisbane", "بریزبن", "AU"),
            ZoneChoice("Australia/Perth", "پرت", "AU"),
            ZoneChoice("Australia/Adelaide", "آدلاید", "AU"),
            ZoneChoice("Pacific/Fiji", "سووا", "FJ"),
            ZoneChoice("Pacific/Honolulu", "هونولولو", "US"),
            ZoneChoice("America/Anchorage", "انکوریج", "US"),
            ZoneChoice("America/Phoenix", "فینیکس", "US"),
            ZoneChoice("America/Detroit", "دیترویت", "US"),
            ZoneChoice("America/Montreal", "مونترآل", "CA"),
            ZoneChoice("America/Edmonton", "ادمونتون", "CA"),
            ZoneChoice("America/Winnipeg", "وینیپگ", "CA"),
            ZoneChoice("America/Halifax", "هلیفکس", "CA"),
            ZoneChoice("America/Bogota", "بوگوتا", "CO"),
            ZoneChoice("America/Lima", "لیما", "PE"),
            ZoneChoice("America/Santiago", "سانتیاگو", "CL"),
            ZoneChoice("America/Caracas", "کاراکاس", "VE"),
            ZoneChoice("America/Montevideo", "مونته‌ویدئو", "UY"),
            ZoneChoice("America/Guatemala", "گواتمالاسیتی", "GT"),
            ZoneChoice("America/Costa_Rica", "سن‌خوزه", "CR"),
            ZoneChoice("America/Panama", "پاناماسیتی", "PA"),
            ZoneChoice("America/Havana", "هاوانا", "CU"),
            ZoneChoice("America/Jamaica", "کینگستون", "JM"),
            ZoneChoice("America/Port_of_Spain", "پورت آو اسپین", "TT"),
            ZoneChoice("Asia/Taipei", "تایپه", "TW"),
            ZoneChoice("Asia/Bahrain", "منامه", "BH"),
            ZoneChoice("Africa/Tripoli", "طرابلس", "LY"),
            ZoneChoice("Asia/Ashgabat", "عشق‌آباد", "TM"),
            ZoneChoice("Europe/Vilnius", "ویلنیوس", "LT"),
            ZoneChoice("Europe/Riga", "ریگا", "LV"),
            ZoneChoice("Europe/Tallinn", "تالین", "EE"),
            ZoneChoice("Europe/Zagreb", "زاگرب", "HR"),
            ZoneChoice("Europe/Ljubljana", "لیوبلیانا", "SI"),
            ZoneChoice("Europe/Bratislava", "براتیسلاوا", "SK"),
            ZoneChoice("Indian/Maldives", "مالی", "MV"),
            ZoneChoice("America/La_Paz", "لاپاز", "BO"),
            ZoneChoice("America/Guayaquil", "گوایاکیل", "EC"),
            ZoneChoice("America/Santo_Domingo", "سانتو دومینگو", "DO"),
            ZoneChoice("Africa/Dar_es_Salaam", "دارالسلام", "TZ"),
            ZoneChoice("Africa/Kampala", "کامپالا", "UG"),
            ZoneChoice("Africa/Luanda", "لواندا", "AO"),
            ZoneChoice("Africa/Dakar", "داکار", "SN"),
            ZoneChoice("Africa/Abidjan", "آبیجان", "CI")
    )

    // Old saved ids that were never valid zones -> the zone they should have used.
    private val zoneAliases = mapOf(
        "America/Atlanta" to "America/New_York",
        "America/Miami" to "America/New_York",
        "Europe/Kiev" to "Europe/Kyiv"
    )

    private val availableIds: Set<String> by lazy { TimeZone.getAvailableIDs().toHashSet() }

    private val allCountries: List<CountryChoice> by lazy {
        val collator = Collator.getInstance(Locale("fa"))
        allZones.map { it.countryCode }.distinct().mapNotNull { code ->
            val locale = Locale("", code)
            val en = locale.getDisplayCountry(Locale.ENGLISH).takeIf { it.isNotBlank() && it != code }
                ?: return@mapNotNull null
            val fa = locale.getDisplayCountry(Locale("fa")).takeIf { it.isNotBlank() && it != code } ?: en
            CountryChoice(code, fa, en, flagFor(code))
        }.sortedWith(Comparator { a, b -> collator.compare(a.faName, b.faName) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = cBg
        window.navigationBarColor = cBg
        loadSelection()

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(cBg)
            setPadding(dp(18), dp(16), dp(18), dp(8))
        }
        val header = buildHeader()
        outer.addView(header)

        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, dp(14), 0, dp(8))
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val hero = buildHeroCard()
        val addCard = buildAddCard()
        column.addView(hero, LinearLayout.LayoutParams(-1, -2))
        column.addView(addCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        column.addView(
            tv("شهرهای من", 18f, cPrimary, true),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(22); bottomMargin = dp(10) }
        )
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(list, LinearLayout.LayoutParams(-1, -2))
        emptyText = tv("هنوز شهری اضافه نکرده‌ای. از کارت بالا یک کشور و شهر انتخاب کن.", 14f, cSecondary).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(28), dp(16), dp(28))
            visibility = View.GONE
        }
        column.addView(emptyText, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(column)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(outer)

        filteredCountries = allCountries
        countryAdapter.setItems(filteredCountries.map { countryLabel(it) })
        filteredCountries.firstOrNull()?.let { showCountry(it) }

        render()

        UiFx.enter(header, 0L, 12f)
        UiFx.enter(hero, 90L)
        UiFx.enter(addCard, 180L)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    // ---------------------------------------------------------------- UI building

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            imageTintList = ColorStateList.valueOf(cPrimary)
            background = UiFx.rounded(this@WorldClockActivity, cElevated, 22, cBorder)
            contentDescription = "بازگشت"
            setOnClickListener { finish() }
        }
        UiFx.pressable(back)
        row.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) }
        }
        titles.addView(tv("ساعت جهانی", 26f, cPrimary, true))
        titles.addView(tv("وقت شهرهای دنیا، لحظه‌به‌لحظه", 13f, cSecondary).apply { setPadding(0, dp(2), 0, 0) })
        row.addView(titles)
        return row
    }

    private fun buildHeroCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            elevation = dp(4).toFloat()
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(getColor(R.color.hero_start), getColor(R.color.hero_end))
            ).apply {
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), cBorder)
            }
        }
        heroClock = AnalogClockView(this).apply { accentColor = cCyan }
        card.addView(heroClock, LinearLayout.LayoutParams(dp(92), dp(92)))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(16) }
        }
        col.addView(tv("ساعت شما", 12f, cSecondary))
        heroTime = tv("", 34f, cPrimary, true).apply {
            fontFeatureSettings = "tnum"
            includeFontPadding = false
            setPadding(0, dp(4), 0, dp(2))
        }
        col.addView(heroTime)
        heroDate = tv("", 13f, cCyan)
        col.addView(heroDate)
        heroZone = tv("", 11f, cSecondary).apply { setPadding(0, dp(3), 0, 0) }
        col.addView(heroZone)
        card.addView(col)
        return card
    }

    private fun buildAddCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = UiFx.rounded(this@WorldClockActivity, cSurface, 24, cBorder)
        }
        card.addView(tv("افزودن شهر", 16f, cPrimary, true))
        card.addView(tv("کشور را پیدا کن و سپس شهر دلخواهت را اضافه کن", 12f, cSecondary).apply {
            setPadding(0, dp(2), 0, dp(10))
        })

        searchInput = EditText(this).apply {
            hint = "جست‌وجوی کشور به فارسی یا انگلیسی…"
            textSize = 14f
            setSingleLine(true)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = UiFx.rounded(this@WorldClockActivity, cElevated, 14, cBorder)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setTextColor(cPrimary)
            setHintTextColor(cSecondary)
        }
        card.addView(searchInput, LinearLayout.LayoutParams(-1, dp(48)))

        countryAdapter = StyledAdapter()
        countrySpinner = Spinner(this).apply {
            background = UiFx.rounded(this@WorldClockActivity, cElevated, 14, cBorder)
            setPopupBackgroundDrawable(UiFx.rounded(this@WorldClockActivity, cElevated, 14, cBorder))
            adapter = countryAdapter
        }
        card.addView(countrySpinner, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

        cityAdapter = StyledAdapter()
        citySpinner = Spinner(this).apply {
            background = UiFx.rounded(this@WorldClockActivity, cElevated, 14, cBorder)
            setPopupBackgroundDrawable(UiFx.rounded(this@WorldClockActivity, cElevated, 14, cBorder))
            adapter = cityAdapter
        }
        card.addView(citySpinner, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

        countrySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filteredCountries.getOrNull(position)?.let { showCountry(it) }
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                applySearch(s?.toString().orEmpty())
            }
        })

        val add = MaterialButton(this).apply {
            text = "＋  افزودن شهر"
            isAllCaps = false
            setTextColor(cBg)
            backgroundTintList = ColorStateList.valueOf(cCyan)
            setOnClickListener { onAddClicked() }
        }
        UiFx.pressable(add)
        card.addView(add, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) })
        return card
    }

    // ---------------------------------------------------------------- Country / city pickers

    private fun applySearch(raw: String) {
        val query = normalize(raw)
        filteredCountries = if (query.isBlank()) allCountries else allCountries.filter {
            normalize(it.faName).contains(query) || normalize(it.enName).contains(query) ||
                normalize(it.code).contains(query)
        }
        val previous = shownCountryCode
        countryAdapter.setItems(filteredCountries.map { countryLabel(it) })
        if (filteredCountries.isEmpty()) {
            shownCountryCode = null
            citiesForCountry = emptyList()
            refreshCities()
            return
        }
        val keep = filteredCountries.indexOfFirst { it.code == previous }.takeIf { it >= 0 } ?: 0
        countrySpinner.setSelection(keep)
        showCountry(filteredCountries[keep])
    }

    private fun showCountry(country: CountryChoice) {
        if (country.code == shownCountryCode) return
        shownCountryCode = country.code
        citiesForCountry = allZones.filter { it.countryCode == country.code }
            .sortedBy { it.city.lowercase(Locale.ROOT) }
        refreshCities()
    }

    private fun refreshCities() {
        cityAdapter.setItems(citiesForCountry.map { it.city })
        if (citiesForCountry.isNotEmpty()) citySpinner.setSelection(0)
    }

    private fun countryLabel(c: CountryChoice) = "${c.flag}  ${c.faName}  ·  ${c.enName}"

    private fun onAddClicked() {
        val choice = citiesForCountry.getOrNull(citySpinner.selectedItemPosition)
        if (choice == null) {
            Toast.makeText(this, "ابتدا یک کشور و شهر انتخاب کن.", Toast.LENGTH_SHORT).show()
            return
        }
        if (resolveZone(choice.id) == null) {
            Toast.makeText(this, "این منطقهٔ زمانی روی دستگاه تو پشتیبانی نمی‌شود.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!selected.add(choice.id)) {
            Toast.makeText(this, "این شهر قبلاً اضافه شده است.", Toast.LENGTH_SHORT).show()
            return
        }
        persist()
        val row = addRow(choice.id, animate = true)
        updateEmpty()
        updateTimes()
        if (row != null) scroll.post { scroll.smoothScrollTo(0, list.top + row.root.top) }
    }

    /** Spinner adapter styled for the dark theme and Persian text. */
    private inner class StyledAdapter :
        ArrayAdapter<String>(this@WorldClockActivity, android.R.layout.simple_spinner_item, ArrayList<String>()) {
        init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        fun setItems(items: List<String>) {
            clear()
            addAll(items)
            notifyDataSetChanged()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            style(super.getView(position, convertView, parent), false)

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
            style(super.getDropDownView(position, convertView, parent), true)

        private fun style(view: View, dropDown: Boolean): View {
            (view as? TextView)?.apply {
                setTextColor(cPrimary)
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                textDirection = View.TEXT_DIRECTION_LOCALE
                setPadding(dp(14), if (dropDown) dp(12) else 0, dp(14), if (dropDown) dp(12) else 0)
                if (dropDown) setBackgroundColor(cElevated)
            }
            return view
        }
    }

    // ---------------------------------------------------------------- Selected cities

    private fun loadSelection() {
        val saved = prefs.getString("zones_v2", null)
        val raw: List<String> = if (saved != null) {
            saved.split("|").filter { it.isNotBlank() }
        } else {
            // First run after the update: migrate from the old unordered set.
            prefs.getStringSet("zones", null)?.toList()?.sorted()
                ?: listOf("Asia/Tehran", "Europe/London", "America/New_York")
        }
        selected.clear()
        for (id in raw) {
            val fixed = zoneAliases[id] ?: id
            if (resolveZone(fixed) != null) selected.add(fixed)
        }
        if (saved == null) persist()
    }

    private fun persist() {
        prefs.edit().putString("zones_v2", selected.joinToString("|")).apply()
    }

    private fun resolveZone(id: String): TimeZone? = zoneCache.getOrPut(id) {
        when {
            id in availableIds -> TimeZone.getTimeZone(id)
            id == "Europe/Kyiv" && "Europe/Kiev" in availableIds -> TimeZone.getTimeZone("Europe/Kiev")
            else -> null
        }
    }

    private fun render() {
        list.removeAllViews()
        rows.clear()
        var index = 0
        for (id in selected.toList()) {
            val row = addRow(id, animate = false)
            if (row != null) UiFx.enter(row.root, 160L + index * 70L, 18f)
            index++
        }
        updateEmpty()
        updateTimes()
    }

    private fun updateEmpty() {
        emptyText.visibility = if (selected.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun addRow(id: String, animate: Boolean): CityRow? {
        val zone = resolveZone(id) ?: return null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(14), dp(14), dp(10), dp(14))
            elevation = dp(2).toFloat()
        }
        val clock = AnalogClockView(this).apply { this.zone = zone }
        root.addView(clock, LinearLayout.LayoutParams(dp(64), dp(64)))

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14); marginEnd = dp(6) }
        }
        column.addView(tv(displayName(id), 15f, cPrimary, true))
        val time = tv("", 28f, cCyan, true).apply {
            fontFeatureSettings = "tnum"
            includeFontPadding = false
            setPadding(0, dp(4), 0, dp(2))
        }
        column.addView(time)
        val date = tv("", 12f, cSecondary)
        column.addView(date)
        val hint = tv("", 12f, cSecondary).apply { setPadding(0, dp(2), 0, 0) }
        column.addView(hint)
        root.addView(column)

        val remove = ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(cDanger)
            background = UiFx.rounded(this@WorldClockActivity, cElevated, 20)
            contentDescription = "حذف"
            setOnClickListener { removeCity(id) }
        }
        UiFx.pressable(remove, 0.88f)
        root.addView(remove, LinearLayout.LayoutParams(dp(40), dp(40)))

        val row = CityRow(zone, root, clock, time, date, hint, null)
        rows[id] = row
        list.addView(root, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        if (animate) UiFx.enter(root, 0L, 18f)
        return row
    }

    private fun removeCity(id: String) {
        val row = rows.remove(id) ?: return
        selected.remove(id)
        persist()
        row.root.animate()
            .alpha(0f)
            .translationX(-dp(48).toFloat())
            .setDuration(240L)
            .withEndAction {
                list.removeView(row.root)
                updateEmpty()
            }
            .start()
    }

    // ---------------------------------------------------------------- Ticking

    private val tick = object : Runnable {
        override fun run() {
            updateTimes()
            val now = System.currentTimeMillis()
            handler.postDelayed(this, 1000L - (now % 1000L) + 5L)
        }
    }

    private fun updateTimes() {
        if (!::heroTime.isInitialized) return
        val now = System.currentTimeMillis()
        val local = TimeZone.getDefault()

        val lc = PersianUtils.newCalendar(local, now)
        heroTime.text = clockText(lc)
        heroDate.text = PersianUtils.dateText(lc)
        heroClock.zone = local
        heroClock.setTime(now)
        heroZone.text = "منطقهٔ زمانی: " + zoneTitle(local) + " • " + utcText(local.getOffset(now))

        for (row in rows.values) updateRow(row, now, local)
    }

    private fun updateRow(row: CityRow, now: Long, local: TimeZone) {
        scratch.timeZone = row.zone
        scratch.timeInMillis = now
        val hour = scratch.get(Calendar.HOUR_OF_DAY)
        row.time.text = clockText(scratch)
        row.clock.setTime(now)

        val day = hour in 6..17
        if (row.isDay != day) {
            row.isDay = day
            row.root.background = GradientDrawable(
                GradientDrawable.Orientation.TR_BL,
                intArrayOf(
                    getColor(if (day) R.color.card_day_start else R.color.card_night_start),
                    getColor(if (day) R.color.card_day_end else R.color.card_night_end)
                )
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), cBorder)
            }
            val accent = if (day) cCyan else cViolet
            row.time.setTextColor(accent)
            row.clock.accentColor = accent
        }
        row.date.text = (if (day) "☀️ روز" else "🌙 شب") + "  •  " + PersianUtils.dateText(scratch)

        val zoneOffset = row.zone.getOffset(now)
        val localOffset = local.getOffset(now)
        val diffMinutes = (zoneOffset - localOffset) / 60000L
        val dayDiff = Math.floorDiv(now + zoneOffset, DAY_MS) - Math.floorDiv(now + localOffset, DAY_MS)
        val dayWord = when {
            dayDiff == 0L -> "امروز"
            dayDiff == 1L -> "فردا"
            dayDiff == -1L -> "دیروز"
            else -> ""
        }
        val diffText = when {
            diffMinutes == 0L -> "هم‌ساعت با شما"
            diffMinutes > 0L -> PersianUtils.hoursAndMinutes(diffMinutes) + " جلوتر از شما"
            else -> PersianUtils.hoursAndMinutes(-diffMinutes) + " عقب‌تر از شما"
        }
        row.hint.text = if (dayWord.isEmpty()) diffText else "$dayWord  •  $diffText"
    }

    // ---------------------------------------------------------------- Small helpers

    private fun clockText(cal: Calendar): String =
        PersianUtils.two(cal.get(Calendar.HOUR_OF_DAY)) + ":" +
            PersianUtils.two(cal.get(Calendar.MINUTE)) + ":" +
            PersianUtils.two(cal.get(Calendar.SECOND))

    private fun utcText(offsetMs: Int): String {
        val minutes = Math.abs(offsetMs) / 60000L
        return when {
            offsetMs == 0 -> "هم‌زمان با گرینویچ"
            offsetMs > 0 -> PersianUtils.hoursAndMinutes(minutes) + " جلوتر از گرینویچ"
            else -> PersianUtils.hoursAndMinutes(minutes) + " عقب‌تر از گرینویچ"
        }
    }

    private fun zoneTitle(zone: TimeZone): String =
        allZones.firstOrNull { it.id == zone.id }?.city
            ?: zone.getDisplayName(false, TimeZone.LONG, Locale("fa"))

    private fun displayName(id: String): String {
        val zone = allZones.firstOrNull { it.id == id }
            ?: return id.substringAfterLast('/').replace('_', ' ')
        val country = allCountries.firstOrNull { it.code == zone.countryCode }
        return if (country == null) zone.city else "${zone.city} · ${country.faName} ${country.flag}"
    }

    private fun flagFor(code: String): String {
        if (code.length != 2) return ""
        return code.uppercase(Locale.ROOT)
            .map { ch -> String(Character.toChars(0x1F1E6 + ch.code - 'A'.code)) }
            .joinToString("")
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT).trim()
        .replace('ي', 'ی').replace('ك', 'ک').replace(Regex("\\s+"), " ")

    private fun tv(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun dp(value: Int): Int = UiFx.dp(this, value)

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
