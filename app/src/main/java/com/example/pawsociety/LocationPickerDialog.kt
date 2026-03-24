package com.example.pawsociety

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
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

    private val regions = listOf("Region I (Ilocos Region)")

    private val provinceData = mapOf(
        "Region I (Ilocos Region)" to listOf("Pangasinan")
    )

    private val cityData = mapOf(
        "Pangasinan" to listOf(
            "Agno", "Aguilar", "Alaminos", "Alcala", "Anda", "Asingan", "Balungao", "Bani",
            "Basista", "Bautista", "Bayambang", "Binalonan", "Binmaley", "Bolinao", "Bugallon",
            "Burgos", "Calasiao", "Dagupan", "Dasol", "Infanta", "Labrador", "Laoac",
            "Lingayen", "Mabini", "Malasiqui", "Manaoag", "Mangaldan", "Mangatarem",
            "Mapandan", "Natividad", "Pozorrubio", "Rosales", "San Carlos", "San Fabian",
            "San Jacinto", "San Manuel", "San Nicolas", "San Quintin", "Santa Barbara",
            "Santa Maria", "Santo Tomas", "Sison", "Sual", "Tayug", "Umingan",
            "Urbiztondo", "Urdaneta", "Villasis"
        )
    )

    private val barangayData = mapOf(
        "Dagupan" to listOf("Bacayao Norte", "Bacayao Sur", "Bonuan Binloc", "Bonuan Boquig", "Bonuan Gueset", "Caranglaan", "Lasip Chico", "Lucao", "Mangin", "Pantal"),
        "Lingayen" to listOf("Aliwekwek", "Balococ", "Bantayan", "Baquioen", "Basing", "Capandanan", "Domalandan Center", "Estanza", "Libsong East", "Poblacion"),
        "San Carlos" to listOf("Bocboc East", "Bocboc West", "Bolo", "Cobol", "Mabalbalino", "Palaming", "Poblacion", "Taloy", "Tamayo", "Tandoc"),
        "Urdaneta" to listOf("Anonas", "Bactad East", "Bactad Proper", "Bayaoas", "Bolaoen", "Camanang", "Camantiles", "Nancamaliran East", "Poblacion", "San Vicente"),
        "Alaminos" to listOf("Baleyadaan", "Bisocol", "Bued", "Inerangan", "Lucap", "Pangapisan North", "Poblacion", "San Jose", "Tangcarang", "Telbang"),
        "Calasiao" to listOf("Ambonao", "Bued", "Cabilocaan", "Doyong", "Lasip", "Malabago", "Poblacion East", "Poblacion West", "Quesban", "Talibaew"),
        "Manaoag" to listOf("Babasit", "Baritao", "Binalay", "Lelemaan", "Poblacion", "Pugaro", "San Ramon", "Santa Ines", "Tebuel", "Town Center"),
        "Mangaldan" to listOf("Anolid", "Bari", "Bunlalacao", "Embarcadero", "Gueset", "Poblacion", "Salaan", "Talogtog", "Tebag", "Tococ"),
        "Santa Barbara" to listOf("Alibago", "Balinling", "Botao", "Leet", "Malanay", "Nilombot", "Poblacion", "Primicias", "Tayug", "Tuliao"),
        "default" to listOf("Barangay 1", "Barangay 2", "Barangay 3", "Barangay 4", "Barangay 5")
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

            val dialog = AlertDialog.Builder(context, R.style.Theme_PawSociety_Dialog)
                .setView(dialogView)
                .setCancelable(false)
                .create()

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

            btnConfirm.setOnClickListener {
                if (validateSelection()) {
                    val fullLocation = "$selectedBarangay, $selectedCity, Pangasinan"
                    onLocationSelected(fullLocation)
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, "Please select a complete Pangasinan location", Toast.LENGTH_SHORT).show()
                }
            }

            btnCancel.setOnClickListener { dialog.dismiss() }
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error opening Pangasinan location picker", Toast.LENGTH_SHORT).show()
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
                selectedRegion = regions[position]
                val provinces = provinceData[selectedRegion] ?: emptyList()
                val provinceAdapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_item,
                    provinces
                )
                provinceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerProvince.adapter = provinceAdapter
                spinnerProvince.isEnabled = provinces.isNotEmpty()
                resetCitySpinner(spinnerCity, context)
                resetBarangaySpinner(spinnerBarangay, context)
                selectedProvince = ""
                selectedCity = ""
                selectedBarangay = ""
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
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
                selectedProvince = parent?.getItemAtPosition(position)?.toString().orEmpty()
                val cities = cityData[selectedProvince] ?: emptyList()
                val cityAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, cities)
                cityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerCity.adapter = cityAdapter
                spinnerCity.isEnabled = cities.isNotEmpty()
                resetBarangaySpinner(spinnerBarangay, context)
                selectedCity = ""
                selectedBarangay = ""
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupCitySpinner(
        spinnerCity: Spinner,
        spinnerBarangay: Spinner
    ) {
        val context = contextRef.get() ?: return

        spinnerCity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCity = parent?.getItemAtPosition(position)?.toString().orEmpty()
                val barangays = barangayData[selectedCity] ?: barangayData.getValue("default")
                val barangayAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, barangays)
                barangayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerBarangay.adapter = barangayAdapter
                spinnerBarangay.isEnabled = barangays.isNotEmpty()
                selectedBarangay = ""
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setupBarangaySpinner(spinnerBarangay: Spinner) {
        spinnerBarangay.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedBarangay = parent?.getItemAtPosition(position)?.toString().orEmpty()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun resetCitySpinner(spinnerCity: Spinner, context: Context) {
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listOf("Select municipality/city"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCity.adapter = adapter
        spinnerCity.isEnabled = false
    }

    private fun resetBarangaySpinner(spinnerBarangay: Spinner, context: Context) {
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listOf("Select barangay"))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBarangay.adapter = adapter
        spinnerBarangay.isEnabled = false
    }

    private fun validateSelection(): Boolean {
        return selectedRegion.isNotEmpty() &&
            selectedProvince == "Pangasinan" &&
            selectedCity.isNotEmpty() &&
            !selectedCity.startsWith("Select") &&
            selectedBarangay.isNotEmpty() &&
            !selectedBarangay.startsWith("Select")
    }
}
