package com.example.pawsociety.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.pawsociety.R
import com.example.pawsociety.RegisterWizardActivity
import com.example.pawsociety.viewmodels.RegistrationViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class Step4PhotoFragment : Fragment() {

    private lateinit var ivProfilePhoto: ImageView
    private lateinit var tvAddPhoto: TextView
    private lateinit var btnContinue: Button
    private lateinit var btnSkip: TextView
    private lateinit var viewModel: RegistrationViewModel

    private var selectedImageUri: Uri? = null

    companion object {
        fun newInstance(viewModel: RegistrationViewModel): Step4PhotoFragment {
            val fragment = Step4PhotoFragment()
            fragment.viewModel = viewModel
            return fragment
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            selectedImageUri?.let { uri ->
                Glide.with(this)
                    .load(uri)
                    .centerCrop()
                    .into(ivProfilePhoto)

                ivProfilePhoto.visibility = View.VISIBLE
                tvAddPhoto.visibility = View.GONE
                viewModel.setProfileImageUri(uri.toString())

                Toast.makeText(requireContext(), "Photo selected!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), "Camera cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            Glide.with(this)
                .load(it)
                .centerCrop()
                .into(ivProfilePhoto)

            ivProfilePhoto.visibility = View.VISIBLE
            tvAddPhoto.visibility = View.GONE
            viewModel.setProfileImageUri(it.toString())

            Toast.makeText(requireContext(), "Photo selected!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_step4_photo, container, false)

        ivProfilePhoto = view.findViewById(R.id.iv_profile_photo)
        tvAddPhoto = view.findViewById(R.id.tv_add_photo)
        btnContinue = view.findViewById(R.id.btn_continue)
        btnSkip = view.findViewById(R.id.btn_skip)

        setupListeners()
        return view
    }

    private fun setupListeners() {
        tvAddPhoto.setOnClickListener {
            showImagePickerDialog()
        }

        ivProfilePhoto.setOnClickListener {
            showImagePickerDialog()
        }

        // ✅ FIXED - use completeRegistration()
        btnContinue.setOnClickListener {
            viewModel.completeRegistration()

            viewModel.registrationComplete.observe(viewLifecycleOwner) { complete ->
                if (complete) {
                    Toast.makeText(requireContext(), "Registration complete!", Toast.LENGTH_SHORT).show()
                    (activity as? RegisterWizardActivity)?.finishRegistration()
                }
            }
        }

        // ✅ FIXED - use completeRegistration()
        btnSkip.setOnClickListener {
            viewModel.setProfileImageUri(null)
            viewModel.completeRegistration()

            viewModel.registrationComplete.observe(viewLifecycleOwner) { complete ->
                if (complete) {
                    Toast.makeText(requireContext(), "Registration complete!", Toast.LENGTH_SHORT).show()
                    (activity as? RegisterWizardActivity)?.finishRegistration()
                }
            }
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Add Profile Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndOpen()
                    1 -> openGallery()
                    2 -> { }
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndOpen() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            openCamera()
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCamera() {
        try {
            val photoFile = createImageFile()
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )
            selectedImageUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Failed to open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = requireContext().externalCacheDir ?: requireContext().cacheDir
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.registrationComplete.removeObservers(viewLifecycleOwner)
    }
}