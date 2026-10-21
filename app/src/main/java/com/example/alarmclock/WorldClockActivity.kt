package com.example.alarmclock

import android.os.Bundle
import android.icu.util.TimeZone as IcuTimeZone
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WorldClockActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var list: LinearLayout
    private lateinit var countrySpinner: Spinner
    private lateinit var citySpinner: Spinner
    private lateinit var searchInput: EditText
    private lateinit var countryAdapter: ArrayAdapter<String>
    private lateinit var cityAdapter: ArrayAdapter<String>
    private var filteredCountries: List<CountryChoice> = emptyList()
    private var citiesForCountry: List<ZoneChoice> = emptyList()
    private val prefs by lazy { getSharedPreferences("world_clocks", MODE_PRIVATE) }

    private data class ZoneChoice(val id: String, val city: String, val countryCode: String)
    private data class CountryChoice(val code: String, val faName: String, val enName: String, val flag: String)
    private data class ClockRow(val time: TextView, val dayStatus: TextView)

    private val allZones: List<ZoneChoice> by lazy {
        // Use Android's country-specific time-zone database instead of inferring a
        // country from ICU region metadata, which can return the world region ("001")
        // for aliases or IDs not recognized by that metadata version.
        Locale.getISOCountries().asSequence()
            .flatMap { code ->
                IcuTimeZone.getAvailableIDs(code).asSequence()
                    .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
                    .distinct()
                    .map { id ->
                        val city = id.substringAfterLast('/')
                            .replace('_', ' ')
                            .replace('-', ' ')
                        ZoneChoice(id, city, code)
                    }
            }
            .distinctBy { it.id }
            .toList()
    }

    private val allCountries: List<CountryChoice> by lazy {
        allZones.map { it.countryCode }.distinct().mapNotNull { code ->
            val locale = Locale("", code)
            val en = locale.getDisplayCountry(Locale.ENGLISH).takeIf { it.isNotBlank() && it != code } ?: return@mapNotNull null
            val fa = locale.getDisplayCountry(Locale("fa")).takeIf { it.isNotBlank() && it != code } ?: en
            CountryChoice(code, fa, en, flagFor(code))
        }.sortedBy { it.faName.lowercase(Locale("fa")) }
    }
    private val selected = linkedSetOf<String>()
    private val rows = linkedMapOf<String, ClockRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected.addAll(prefs.getStringSet("zones", setOf("Asia/Tehran", "Europe/London", "America/New_York")) ?: emptySet())
        val validZones = allZones.map { it.id }.toSet()
        selected.retainAll(validZones)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 14)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(getColor(R.color.app_background))
        }
        root.addView(TextView(this).apply {
            text = "ساعت جهانی"; textSize = 27f; setTextColor(getColor(R.color.text_primary))
            gravity = Gravity.END; setPadding(0, 0, 0, 5)
        })
        root.addView(TextView(this).apply {
            text = "کشور را پیدا کن و سپس شهر دلخواهت را اضافه کن"
            textSize = 13f; setTextColor(getColor(R.color.text_secondary))
            gravity = Gravity.END; setPadding(0, 0, 0, 14)
        })

        searchInput = EditText(this).apply {
            hint = "جست‌وجوی کشور به فارسی یا انگلیسی…"
            textSize = 14f; setSingleLine(true); layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = getDrawable(R.drawable.bg_input_field); setPadding(14, 9, 14, 9)
            setTextColor(getColor(R.color.text_primary)); setHintTextColor(getColor(R.color.text_secondary))
        }
        root.addView(searchInput, ViewGroup.LayoutParams(-1, -2))

        countrySpinner = Spinner(this).apply { background = getDrawable(R.drawable.bg_input_field); setPadding(10, 4, 10, 4) }
        filteredCountries = allCountries
        countryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filteredCountries.map { "${it.flag}  ${it.faName}  ·  ${it.enName}" })
        countryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        countrySpinner.adapter = countryAdapter
        root.addView(countrySpinner, ViewGroup.LayoutParams(-1, -2))

        citySpinner = Spinner(this).apply { background = getDrawable(R.drawable.bg_input_field); setPadding(10, 4, 10, 4) }
        cityAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, emptyList<String>())
        cityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        citySpinner.adapter = cityAdapter
        root.addView(citySpinner, ViewGroup.LayoutParams(-1, -2))

        countrySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) { citiesForCountry = emptyList(); refreshCities() }
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val country = filteredCountries.getOrNull(position)
                citiesForCountry = country?.let { c -> allZones.filter { it.countryCode == c.code }.sortedBy { it.city.lowercase(Locale.ROOT) } }.orEmpty()
                refreshCities()
            }
        }
        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = normalize(s?.toString().orEmpty())
                val previousCode = filteredCountries.getOrNull(countrySpinner.selectedItemPosition)?.code
                filteredCountries = if (query.isBlank()) allCountries else allCountries.filter {
                    normalize(it.faName).contains(query) || normalize(it.enName).contains(query) || normalize(it.code).contains(query)
                }
                countryAdapter.clear()
                countryAdapter.addAll(filteredCountries.map { "${it.flag}  ${it.faName}  ·  ${it.enName}" })
                countryAdapter.notifyDataSetChanged()
                if (filteredCountries.isEmpty()) {
                    citiesForCountry = emptyList()
                    refreshCities()
                    // Leave the empty result visible while the user continues typing.
                } else {
                    val preserved = filteredCountries.indexOfFirst { it.code == previousCode }.takeIf { it >= 0 } ?: 0
                    countrySpinner.setSelection(preserved)
                    val country = filteredCountries[preserved]
                    citiesForCountry = allZones.filter { it.countryCode == country.code }.sortedBy { it.city.lowercase(Locale.ROOT) }
                    refreshCities()
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        root.addView(Button(this).apply {
            text = "＋ افزودن شهر"; isAllCaps = false
            backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.accent_cyan))
            setTextColor(getColor(R.color.app_background))
            setOnClickListener {
                val choice = citiesForCountry.getOrNull(citySpinner.selectedItemPosition)
                if (choice == null) {
                    Toast.makeText(this@WorldClockActivity, "ابتدا یک کشور و شهر انتخاب کن.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (!selected.add(choice.id)) Toast.makeText(this@WorldClockActivity, "این شهر قبلاً اضافه شده است.", Toast.LENGTH_SHORT).show()
                else { persist(); render() }
            }
        })

        val scroll = ScrollView(this).apply { clipToPadding = false; setPadding(0, 12, 0, 8) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "بازگشت"; isAllCaps = false
            backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.surface_elevated))
            setTextColor(getColor(R.color.text_primary)); setOnClickListener { finish() }
        })
        setContentView(root)
        if (filteredCountries.isNotEmpty()) {
            val country = filteredCountries.first()
            citiesForCountry = allZones.filter { it.countryCode == country.code }.sortedBy { it.city.lowercase(Locale.ROOT) }
            refreshCities()
        }
        render()
    }

    private fun refreshCities() {
        cityAdapter.clear()
        cityAdapter.addAll(citiesForCountry.map { it.cityLabel() })
        cityAdapter.notifyDataSetChanged()
    }

    private fun ZoneChoice.cityLabel(): String = city

    private fun flagFor(code: String): String {
        if (code.length != 2) return ""
        return code.uppercase(Locale.ROOT).map { char -> String(Character.toChars(0x1F1E6 + char.code - 'A'.code)) }.joinToString("")
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT).trim()
        .replace('ي', 'ی').replace('ك', 'ک').replace(Regex("\\s+"), " ")

    private fun countryForZone(id: String): CountryChoice? {
        val code = runCatching { allZones.firstOrNull { it.id == id }?.countryCode }.getOrNull() ?: return null
        return allCountries.firstOrNull { it.code == code }
    }

    private fun displayName(id: String): String {
        val zone = allZones.firstOrNull { it.id == id } ?: return id
        val country = countryForZone(id)
        return if (country == null) zone.city else "${zone.city} · ${country.faName} ${country.flag}"
    }

    private fun persist() { prefs.edit().putStringSet("zones", selected.toSet()).apply() }

    private fun render() {
        list.removeAllViews()
        rows.clear()
        if (selected.isEmpty()) list.addView(TextView(this).apply {
            text = "هنوز شهری اضافه نکرده‌ای. از فهرست بالا یک کشور و شهر انتخاب کن."
            textSize = 14f; setTextColor(getColor(R.color.text_secondary)); gravity = Gravity.CENTER
            setPadding(16, 32, 16, 32)
        })
        selected.forEach { id ->
            val zone = allZones.firstOrNull { it.id == id } ?: return@forEach
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(14, 14, 14, 14)
                background = getDrawable(R.drawable.bg_surface_card); elevation = 2f
            }
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            column.addView(TextView(this).apply {
                text = displayName(id); textSize = 15f; setTextColor(getColor(R.color.text_primary)); gravity = Gravity.END
            })
            val time = TextView(this).apply {
                textSize = 27f; setTextColor(getColor(R.color.accent_cyan)); gravity = Gravity.END
                typeface = android.graphics.Typeface.MONOSPACE; includeFontPadding = false; setPadding(0, 6, 0, 4)
            }
            column.addView(time)
            val status = TextView(this).apply { textSize = 12f; setTextColor(getColor(R.color.text_secondary)); gravity = Gravity.END }
            column.addView(status)
            row.addView(column)
            row.addView(Button(this).apply {
                text = "حذف"; isAllCaps = false; minWidth = 0
                backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.danger))
                setTextColor(getColor(R.color.app_background))
                setOnClickListener { selected.remove(id); persist(); render() }
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = 10 })
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
            rows[id] = ClockRow(time, status)
        }
        updateTimes()
    }

    private fun updateTimes() {
        val now = Date()
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale("fa", "IR"))
        val dateFormat = SimpleDateFormat("EEEE، dd MMMM yyyy", Locale("fa", "IR"))
        rows.forEach { (zone, row) ->
            val tz = java.util.TimeZone.getTimeZone(zone)
            timeFormat.timeZone = tz; dateFormat.timeZone = tz
            row.time.text = timeFormat.format(now)
            val hour = SimpleDateFormat("H", Locale.ROOT).apply { timeZone = tz }.format(now).toIntOrNull() ?: 12
            row.dayStatus.text = (if (hour in 6..17) "☀️ روز" else "🌙 شب") + "  •  " + dateFormat.format(now)
            row.dayStatus.setTextColor(getColor(if (hour in 6..17) R.color.accent_cyan else R.color.text_secondary))
        }
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1000)
    }

    private val tick = Runnable { updateTimes() }
    override fun onDestroy() { handler.removeCallbacks(tick); super.onDestroy() }
}
