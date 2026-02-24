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

    // Simple location data to prevent crashes
    private val regions = listOf("Luzon", "Visayas", "Mindanao")

    private val provinceData = mapOf(
        "Luzon" to listOf("Metro Manila", "Cavite", "Laguna", "Batangas", "Bulacan", "Pampanga", "Pangasinan"),
        "Visayas" to listOf("Cebu", "Bohol", "Iloilo", "Negros Occidental", "Leyte"),
        "Mindanao" to listOf("Davao del Sur", "Bukidnon", "Misamis Oriental", "Zamboanga del Sur", "Cotabato")
    )

    private val cityData = mapOf(
        "Metro Manila" to listOf("Manila", "Quezon City", "Makati", "Taguig", "Pasig"),
        "Cavite" to listOf("Bacoor", "Dasmariñas", "Imus", "Tagaytay", "Trece Martires"),
        "Laguna" to listOf("Calamba", "San Pablo", "Santa Rosa", "Biñan", "Cabuyao"),
        "Batangas" to listOf("Batangas City", "Lipa City", "Tanauan", "Nasugbu"),
        "Bulacan" to listOf("Malolos", "Meycauayan", "San Jose del Monte", "Baliuag"),
        "Pampanga" to listOf("Angeles City", "San Fernando", "Mabalacat"),
        "Pangasinan" to listOf("Dagupan", "Alaminos", "San Carlos", "Urdaneta"),
        "Cebu" to listOf("Cebu City", "Lapu-Lapu", "Mandaue", "Talisay"),
        "Bohol" to listOf("Tagbilaran", "Panglao", "Dauis"),
        "Iloilo" to listOf("Iloilo City", "Passi"),
        "Negros Occidental" to listOf("Bacolod", "Bago", "Kabankalan"),
        "Leyte" to listOf("Tacloban", "Ormoc"),
        "Davao del Sur" to listOf("Davao City", "Digos"),
        "Bukidnon" to listOf("Malaybalay", "Valencia"),
        "Misamis Oriental" to listOf("Cagayan de Oro", "Gingoog"),
        "Zamboanga del Sur" to listOf("Zamboanga City", "Pagadian"),
        "Cotabato" to listOf("Kidapawan", "Midsayap")
    )

    private val barangayData = mapOf(
        "Manila" to listOf("Barangay 1", "Barangay 2", "Barangay 3"),
        "Quezon City" to listOf("Barangay A", "Barangay B", "Barangay C"),
        "Makati" to listOf("Barangay 1", "Barangay 2", "Barangay 3"),
        "Cebu City" to listOf("Barangay 1", "Barangay 2", "Barangay 3"),
        "Davao City" to listOf("Poblacion", "Buhangin", "Toril"),
        "Cagayan de Oro" to listOf("Barangay 1", "Barangay 2", "Barangay 3")
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

            // Setup spinners
            setupRegionSpinner(spinnerRegion, spinnerProvince, spinnerCity, spinnerBarangay)
            setupProvinceSpinner(spinnerProvince, spinnerCity, spinnerBarangay)
            setupCitySpinner(spinnerCity, spinnerBarangay)
            setupBarangaySpinner(spinnerBarangay)

            val dialog = AlertDialog.Builder(context)
                .setTitle("Select Location")
                .setView(dialogView)
                .setCancelable(false)
                .create()

            btnConfirm.setOnClickListener {
                if (validateSelection()) {
                    val fullLocation = "$selectedBarangay, $selectedCity, $selectedProvince"
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
                        android.R.layout.simple_spinner_item, provinces.ifEmpty { listOf("No provinces") })
                    provinceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerProvince.adapter = provinceAdapter
                    spinnerProvince.isEnabled = provinces.isNotEmpty()

                    // Reset lower spinners
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
                    val cities = cityData[selectedProvince] ?: listOf("No cities")

                    val cityAdapter = ArrayAdapter(context,
                        android.R.layout.simple_spinner_item, cities)
                    cityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerCity.adapter = cityAdapter
                    spinnerCity.isEnabled = cities.isNotEmpty() && cities[0] != "No cities"

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
                    val barangays = if (selectedCity == "No cities") {
                        listOf("No barangays")
                    } else {
                        barangayData[selectedCity] ?: listOf("Barangay 1", "Barangay 2", "Barangay 3")
                    }

                    val barangayAdapter = ArrayAdapter(context,
                        android.R.layout.simple_spinner_item, barangays)
                    barangayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerBarangay.adapter = barangayAdapter
                    spinnerBarangay.isEnabled = barangays.isNotEmpty() && barangays[0] != "No barangays"

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
                selectedCity != "No cities" &&
                selectedBarangay != "No barangays"
    }
}