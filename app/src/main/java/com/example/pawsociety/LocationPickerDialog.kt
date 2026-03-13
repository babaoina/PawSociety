package com.example.pawsociety

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import java.lang.ref.WeakReference

class LocationPickerDialog(
    context: Context,
    private val onLocationSelected: (String) -> Unit
) {

    private val contextRef = WeakReference(context)
    private var selectedRegion = ""
    private var selectedProvince = ""
    private var selectedCity = ""
    private var selectedBarangay = ""

    // Complete Philippine regions
    private val regions = listOf(
        "National Capital Region (NCR)",
        "Cordillera Administrative Region (CAR)",
        "Region I (Ilocos Region)",
        "Region II (Cagayan Valley)",
        "Region III (Central Luzon)",
        "Region IV-A (CALABARZON)",
        "Region IV-B (MIMAROPA)",
        "Region V (Bicol Region)",
        "Region VI (Western Visayas)",
        "Region VII (Central Visayas)",
        "Region VIII (Eastern Visayas)",
        "Region IX (Zamboanga Peninsula)",
        "Region X (Northern Mindanao)",
        "Region XI (Davao Region)",
        "Region XII (SOCCSKSARGEN)",
        "Region XIII (Caraga)",
        "Bangsamoro Autonomous Region in Muslim Mindanao (BARMM)"
    )

    // Complete provinces per region
    private val provinceData = mapOf(
        "National Capital Region (NCR)" to listOf(
            "Metro Manila"
        ),
        "Cordillera Administrative Region (CAR)" to listOf(
            "Abra", "Apayao", "Benguet", "Ifugao", "Kalinga", "Mountain Province"
        ),
        "Region I (Ilocos Region)" to listOf(
            "Ilocos Norte", "Ilocos Sur", "La Union", "Pangasinan"
        ),
        "Region II (Cagayan Valley)" to listOf(
            "Batanes", "Cagayan", "Isabela", "Nueva Vizcaya", "Quirino"
        ),
        "Region III (Central Luzon)" to listOf(
            "Aurora", "Bataan", "Bulacan", "Nueva Ecija", "Pampanga", "Tarlac", "Zambales"
        ),
        "Region IV-A (CALABARZON)" to listOf(
            "Batangas", "Cavite", "Laguna", "Quezon", "Rizal"
        ),
        "Region IV-B (MIMAROPA)" to listOf(
            "Marinduque", "Occidental Mindoro", "Oriental Mindoro", "Palawan", "Romblon"
        ),
        "Region V (Bicol Region)" to listOf(
            "Albay", "Camarines Norte", "Camarines Sur", "Catanduanes", "Masbate", "Sorsogon"
        ),
        "Region VI (Western Visayas)" to listOf(
            "Aklan", "Antique", "Capiz", "Guimaras", "Iloilo", "Negros Occidental"
        ),
        "Region VII (Central Visayas)" to listOf(
            "Bohol", "Cebu", "Negros Oriental", "Siquijor"
        ),
        "Region VIII (Eastern Visayas)" to listOf(
            "Biliran", "Eastern Samar", "Leyte", "Northern Samar", "Samar", "Southern Leyte"
        ),
        "Region IX (Zamboanga Peninsula)" to listOf(
            "Zamboanga del Norte", "Zamboanga del Sur", "Zamboanga Sibugay"
        ),
        "Region X (Northern Mindanao)" to listOf(
            "Bukidnon", "Camiguin", "Lanao del Norte", "Misamis Occidental", "Misamis Oriental"
        ),
        "Region XI (Davao Region)" to listOf(
            "Davao de Oro", "Davao del Norte", "Davao del Sur", "Davao Occidental", "Davao Oriental"
        ),
        "Region XII (SOCCSKSARGEN)" to listOf(
            "Cotabato", "Sarangani", "South Cotabato", "Sultan Kudarat"
        ),
        "Region XIII (Caraga)" to listOf(
            "Agusan del Norte", "Agusan del Sur", "Dinagat Islands", "Surigao del Norte", "Surigao del Sur"
        ),
        "Bangsamoro Autonomous Region in Muslim Mindanao (BARMM)" to listOf(
            "Basilan", "Lanao del Sur", "Maguindanao", "Sulu", "Tawi-Tawi"
        )
    )

    // Complete cities/municipalities per province
    private val cityData = mapOf(
        // NCR
        "Metro Manila" to listOf(
            "Caloocan", "Las Piñas", "Makati", "Malabon", "Mandaluyong", "Manila",
            "Marikina", "Muntinlupa", "Navotas", "Parañaque", "Pasay", "Pasig",
            "Pateros", "Quezon City", "San Juan", "Taguig", "Valenzuela"
        ),

        // CAR
        "Abra" to listOf("Bangued", "Boliney", "Bucay", "Bucloc", "Daguioman", "Danglas", "Dolores", "La Paz", "Lacub", "Lagangilang", "Lagayan", "Langiden", "Licuan-Baay", "Luba", "Malibcong", "Manabo", "Peñarrubia", "Pidigan", "Pilar", "Sallapadan", "San Isidro", "San Juan", "San Quintin", "Tayum", "Tineg", "Tubo", "Villaviciosa"),
        "Apayao" to listOf("Calanasan", "Conner", "Flora", "Kabugao", "Luna", "Pudtol", "Santa Marcela"),
        "Benguet" to listOf("Atok", "Baguio", "Bakun", "Bokod", "Buguias", "Itogon", "Kabayan", "Kapangan", "Kibungan", "La Trinidad", "Mankayan", "Sablan", "Tuba", "Tublay"),
        "Ifugao" to listOf("Aguinaldo", "Alfonso Lista", "Asipulo", "Banaue", "Hingyon", "Hungduan", "Kiangan", "Lagawe", "Lamut", "Mayoyao", "Tinoc"),
        "Kalinga" to listOf("Balbalan", "Lubuagan", "Pasil", "Pinukpuk", "Rizal", "Tabuk", "Tanudan", "Tinglayan"),
        "Mountain Province" to listOf("Barlig", "Bauko", "Besao", "Bontoc", "Natonin", "Paracelis", "Sabangan", "Sadanga", "Sagada", "Tadian"),

        // Region I
        "Ilocos Norte" to listOf("Adams", "Bacarra", "Badoc", "Bangui", "Banna", "Batac", "Burgos", "Carasi", "Currimao", "Dingras", "Dumalneg", "Laoag", "Marcos", "Nueva Era", "Pagudpud", "Paoay", "Pasuquin", "Piddig", "Pinili", "San Nicolas", "Sarrat", "Solsona", "Vintar"),
        "Ilocos Sur" to listOf("Alilem", "Banayoyo", "Bantay", "Burgos", "Cabugao", "Candon", "Caoayan", "Cervantes", "Galimuyod", "Gregorio del Pilar", "Lidlidda", "Magsingal", "Nagbukel", "Narvacan", "Quirino", "Salcedo", "San Emilio", "San Esteban", "San Ildefonso", "San Juan", "San Vicente", "Santa", "Santa Catalina", "Santa Cruz", "Santa Lucia", "Santa Maria", "Santiago", "Santo Domingo", "Sigay", "Sinait", "Sugpon", "Suyo", "Tagudin", "Vigan"),
        "La Union" to listOf("Agoo", "Aringay", "Bacnotan", "Bagulin", "Balaoan", "Bangar", "Bauang", "Burgos", "Caba", "Luna", "Naguilian", "Pugo", "Rosario", "San Fernando", "San Gabriel", "San Juan", "Santo Tomas", "Santol", "Sudipen", "Tubao"),
        "Pangasinan" to listOf("Agno", "Aguilar", "Alaminos", "Alcala", "Anda", "Asingan", "Balungao", "Bani", "Basista", "Bautista", "Bayambang", "Binalonan", "Binmaley", "Bolinao", "Bugallon", "Burgos", "Calasiao", "Dagupan", "Dasol", "Infanta", "Labrador", "Laoac", "Lingayen", "Mabini", "Malasiqui", "Manaoag", "Mangaldan", "Mangatarem", "Mapandan", "Natividad", "Pozorrubio", "Rosales", "San Carlos", "San Fabian", "San Jacinto", "San Manuel", "San Nicolas", "San Quintin", "Santa Barbara", "Santa Maria", "Santo Tomas", "Sison", "Sual", "Tayug", "Umingan", "Urbiztondo", "Urdaneta", "Villasis"),

        // Add more provinces as needed...
        // I'll continue with major cities for now

        "Cebu" to listOf(
            "Alcantara", "Alcoy", "Alegria", "Aloguinsan", "Argao", "Asturias", "Badian", "Balamban", "Bantayan", "Barili", "Bogo", "Boljoon", "Borbon", "Carcar", "Carmen", "Catmon", "Cebu City", "Compostela", "Consolacion", "Cordova", "Daanbantayan", "Dalaguete", "Danao", "Dumanjug", "Ginatilan", "Lapu-Lapu", "Liloan", "Madridejos", "Malabuyoc", "Mandaue", "Medellin", "Minglanilla", "Moalboal", "Naga", "Oslob", "Pilar", "Pinamungajan", "Poro", "Ronda", "Samboan", "San Fernando", "San Francisco", "San Remigio", "Santa Fe", "Santander", "Sibonga", "Sogod", "Tabogon", "Tabuelan", "Talisay", "Toledo", "Tuburan", "Tudela"
        ),
        "Davao del Sur" to listOf(
            "Bansalan", "Davao City", "Digos", "Hagonoy", "Kiblawan", "Magsaysay", "Malalag", "Matanao", "Padada", "Santa Cruz", "Sulop"
        ),
        "Bukidnon" to listOf(
            "Baungon", "Cabanglasan", "Damulog", "Dangcagan", "Don Carlos", "Impasugong", "Kadingilan", "Kalilangan", "Kibawe", "Kitaotao", "Lantapan", "Libona", "Malaybalay", "Malitbog", "Manolo Fortich", "Maramag", "Pangantucan", "Quezon", "San Fernando", "Sumilao", "Talakag", "Valencia"
        )
        // Add more provinces as needed...
    )

    // Sample barangay data per city (you can expand this)
    private val barangayData = mapOf(
        "Manila" to listOf(
            "Barangay 1", "Barangay 2", "Barangay 3", "Barangay 4", "Barangay 5",
            "Barangay 6", "Barangay 7", "Barangay 8", "Barangay 9", "Barangay 10"
        ),
        "Quezon City" to listOf(
            "Barangay Alicia", "Barangay Amihan", "Barangay Apolonio Samson", "Barangay Aurora", "Barangay Baesa",
            "Barangay Bagbag", "Barangay Bagong Lipunan", "Barangay Bagong Pag-asa", "Barangay Bagong Silangan",
            "Barangay Bahay Toro", "Barangay Balingasa", "Barangay Balintawak", "Barangay Batasan Hills"
        ),
        "Cebu City" to listOf(
            "Adlaon", "Agsungot", "Apas", "Bacayan", "Banilad", "Basak Pardo", "Basak San Nicolas",
            "Binaliw", "Bonbon", "Buhisan", "Bulacao", "Buot", "Calamba", "Cambinocot", "Capitol Site",
            "Carreta", "Cogon Ramos", "Day-as", "Duljo", "Ermita", "Guadalupe", "Guba", "Hipodromo",
            "Inayawan", "Kalunasan", "Kamagayan", "Kamputhaw", "Kasambagan", "Labangon", "Lahug",
            "Lorega", "Lusaran", "Luz", "Mabolo", "Malubog", "Mambaling", "Pahina Central", "Pit-os",
            "Poblacion Pardo", "Pung-ol-Sibugay", "Punta Princesa", "Quiot", "Sambag I", "Sambag II",
            "San Antonio", "San Jose", "San Nicolas", "Santa Cruz", "Sawang Calero", "Sinsin",
            "Sirao", "Suba", "Sudlon I", "Sudlon II", "Tabunan", "Tagbao", "Taptap", "Tejero",
            "Tinago", "Tisa", "To-ong", "Zapatera"
        ),
        "Davao City" to listOf(
            "Agdao", "Baguio", "Buhangin", "Bunawan", "Calinan", "Marilog", "Paquibato", "Poblacion",
            "Talomo", "Tugbok", "Toril"
        ),
        "Makati" to listOf(
            "Bangkal", "Bel-Air", "Carmona", "Dasmarinas", "Forbes Park", "Guadalupe Nuevo",
            "Guadalupe Viejo", "Kasilawan", "La Paz", "Magallanes", "Olympia", "Palanan",
            "Pembo", "Pinagkaisahan", "Pio del Pilar", "Pitogo", "Rizal", "San Antonio",
            "San Isidro", "San Lorenzo", "Santa Cruz", "Singkamas", "Tejeros", "Urdaneta",
            "Valenzuela"
        )
        // Add more barangay data as needed...
    )

    fun show() {
        val context = contextRef.get() ?: return

        try {
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_location_selector, null)

            val spinnerRegion = dialogView.findViewById<Spinner>(R.id.spinner_region)
            val spinnerProvince = dialogView.findViewById<Spinner>(R.id.spinner_province)
            val spinnerCity = dialogView.findViewById<Spinner>(R.id.spinner_city)
            val spinnerBarangay = dialogView.findViewById<Spinner>(R.id.spinner_barangay)
            val btnConfirm = dialogView.findViewById<Button>(R.id.btn_confirm)
            val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)

            setupRegionSpinner(spinnerRegion, spinnerProvince, spinnerCity, spinnerBarangay)
            setupProvinceSpinner(spinnerProvince, spinnerCity, spinnerBarangay)
            setupCitySpinner(spinnerCity, spinnerBarangay)
            setupBarangaySpinner(spinnerBarangay)

            val dialog = AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(false)
                .create()

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            btnConfirm.setOnClickListener {
                if (validateSelection()) {
                    val fullLocation = "$selectedBarangay, $selectedCity, $selectedProvince, $selectedRegion"
                    onLocationSelected(fullLocation)
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, "Please select complete location", Toast.LENGTH_SHORT).show()
                }
            }

            btnCancel.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error opening location picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRegionSpinner(
        spinnerRegion: Spinner,
        spinnerProvince: Spinner,
        spinnerCity: Spinner,
        spinnerBarangay: Spinner
    ) {
        val context = contextRef.get() ?: return

        val regionAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, regions)
        regionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRegion.adapter = regionAdapter

        spinnerRegion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (parent != null) {
                    selectedRegion = parent.getItemAtPosition(position).toString()
                    val provinces = provinceData[selectedRegion] ?: emptyList()

                    val provinceAdapter = ArrayAdapter(context,
                        android.R.layout.simple_spinner_item, provinces.ifEmpty { listOf("No provinces available") })
                    provinceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerProvince.adapter = provinceAdapter
                    spinnerProvince.isEnabled = provinces.isNotEmpty()

                    resetCitySpinner(spinnerCity, context)
                    resetBarangaySpinner(spinnerBarangay, context)

                    selectedProvince = ""
                    selectedCity = ""
                    selectedBarangay = ""
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupProvinceSpinner(
        spinnerProvince: Spinner,
        spinnerCity: Spinner,
        spinnerBarangay: Spinner
    ) {
        val context = contextRef.get() ?: return

        spinnerProvince.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (parent != null && position >= 0) {
                    selectedProvince = parent.getItemAtPosition(position).toString()
                    val cities = cityData[selectedProvince] ?: listOf("No cities available")

                    val cityAdapter = ArrayAdapter(context,
                        android.R.layout.simple_spinner_item, cities)
                    cityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerCity.adapter = cityAdapter
                    spinnerCity.isEnabled = cities.isNotEmpty() && cities[0] != "No cities available"

                    resetBarangaySpinner(spinnerBarangay, context)
                    selectedCity = ""
                    selectedBarangay = ""
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupCitySpinner(
        spinnerCity: Spinner,
        spinnerBarangay: Spinner
    ) {
        val context = contextRef.get() ?: return

        spinnerCity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (parent != null && position >= 0) {
                    selectedCity = parent.getItemAtPosition(position).toString()
                    val barangays = if (selectedCity == "No cities available") {
                        listOf("No barangays available")
                    } else {
                        barangayData[selectedCity] ?: listOf(
                            "Barangay 1", "Barangay 2", "Barangay 3", "Barangay 4", "Barangay 5"
                        )
                    }

                    val barangayAdapter = ArrayAdapter(context,
                        android.R.layout.simple_spinner_item, barangays)
                    barangayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerBarangay.adapter = barangayAdapter
                    spinnerBarangay.isEnabled = barangays.isNotEmpty() && barangays[0] != "No barangays available"

                    selectedBarangay = ""
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupBarangaySpinner(spinnerBarangay: Spinner) {
        spinnerBarangay.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (parent != null && position >= 0) {
                    selectedBarangay = parent.getItemAtPosition(position).toString()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun resetCitySpinner(spinnerCity: Spinner, context: Context) {
        val adapter = ArrayAdapter(context,
            android.R.layout.simple_spinner_item, listOf("Select Province First"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCity.adapter = adapter
        spinnerCity.isEnabled = false
    }

    private fun resetBarangaySpinner(spinnerBarangay: Spinner, context: Context) {
        val adapter = ArrayAdapter(context,
            android.R.layout.simple_spinner_item, listOf("Select City First"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBarangay.adapter = adapter
        spinnerBarangay.isEnabled = false
    }

    private fun validateSelection(): Boolean {
        return selectedRegion.isNotEmpty() &&
                selectedProvince.isNotEmpty() &&
                selectedCity.isNotEmpty() &&
                selectedBarangay.isNotEmpty() &&
                selectedProvince != "Select Region First" &&
                selectedCity != "Select Province First" &&
                selectedBarangay != "Select City First" &&
                selectedCity != "No cities available" &&
                selectedBarangay != "No barangays available" &&
                selectedProvince != "No provinces available"
    }
}