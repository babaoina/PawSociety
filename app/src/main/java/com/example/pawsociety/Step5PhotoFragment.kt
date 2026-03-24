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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.pawsociety.R
import com.example.pawsociety.RegisterWizardActivity
import com.example.pawsociety.viewmodels.RegistrationViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class Step5PhotoFragment : Fragment() {

    private lateinit var ivProfilePhoto: ImageView
    private lateinit var ivCircleBackground: ImageView
    private lateinit var tvAddPhoto: TextView
    private lateinit var btnFinish: Button
    private lateinit var btnSkip: TextView
    private lateinit var viewModel: RegistrationViewModel

    private var selectedImageUri: Uri? = null
    private var isProcessing = false

    companion object {
        fun newInstance(viewModel: RegistrationViewModel): Step5PhotoFragment {
            val fragment = Step5PhotoFragment()
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
        val view = inflater.inflate(R.layout.fragment_step5_photo, container, false)

        ivProfilePhoto = view.findViewById(R.id.iv_profile_photo)
        ivCircleBackground = view.findViewById(R.id.iv_circle_background)
        tvAddPhoto = view.findViewById(R.id.tv_add_photo)
        btnFinish = view.findViewById(R.id.btn_finish)
        btnSkip = view.findViewById(R.id.btn_skip)

        btnFinish.isEnabled = true
        btnFinish.alpha = 1.0f
        btnFinish.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        btnFinish.setBackgroundResource(R.drawable.button_rounded_brown)

        btnSkip.isEnabled = true
        btnSkip.alpha = 1.0f
        btnSkip.setTextColor(android.graphics.Color.parseColor("#7A4F2B"))
        btnSkip.setBackgroundResource(R.drawable.button_rounded_white_border)

        // If Google Sign In with photo, load it
        val activity = activity as? RegisterWizardActivity
        if (activity?.isGoogleSignIn == true && !activity.googlePhotoUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(activity.googlePhotoUrl)
                .centerCrop()
                .into(ivProfilePhoto)

            ivProfilePhoto.visibility = View.VISIBLE
            tvAddPhoto.visibility = View.GONE
            viewModel.setProfileImageUri(activity.googlePhotoUrl)
            selectedImageUri = Uri.parse(activity.googlePhotoUrl)
        }

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

        btnFinish.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            isProcessing = true

            if (selectedImageUri != null) {
                lifecycleScope.launch {
                    btnFinish.isEnabled = false
                    btnFinish.text = "Processing..."

                    viewModel.completeRegistration()

                    viewModel.registrationComplete.observe(viewLifecycleOwner) { complete ->
                        if (complete) {
                            Toast.makeText(requireContext(), "Registration complete! 🎉", Toast.LENGTH_LONG).show()
                            isProcessing = false
                            (activity as? RegisterWizardActivity)?.finishRegistration()
                        }
                    }
                }
            } else {
                android.app.AlertDialog.Builder(requireContext(), R.style.Theme_PawSociety_Dialog)
                    .setTitle("No Photo")
                    .setMessage("You haven't selected a profile photo. Do you want to continue anyway?")
                    .setPositiveButton("Continue") { _, _ ->
                        lifecycleScope.launch {
                            btnFinish.isEnabled = false
                            btnFinish.text = "Processing..."

                            viewModel.completeRegistration()

                            viewModel.registrationComplete.observe(viewLifecycleOwner) { complete ->
                                if (complete) {
                                    Toast.makeText(requireContext(), "Registration complete! 🎉", Toast.LENGTH_LONG).show()
                                    isProcessing = false
                                    (activity as? RegisterWizardActivity)?.finishRegistration()
                                }
                            }
                        }
                    }
                    .setNegativeButton("Add Photo") { _, _ ->
                        isProcessing = false
                        showImagePickerDialog()
                    }
                    .show()
            }
        }

        btnSkip.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            isProcessing = true

            android.app.AlertDialog.Builder(requireContext(), R.style.Theme_PawSociety_Dialog)
                .setTitle("Skip Photo")
                .setMessage("Are you sure you want to skip adding a profile photo? You can add one later in settings.")
                .setPositiveButton("Skip") { _, _ ->
                    lifecycleScope.launch {
                        btnSkip.isEnabled = false

                        viewModel.setProfileImageUri(null)
                        viewModel.completeRegistration()

                        viewModel.registrationComplete.observe(viewLifecycleOwner) { complete ->
                            if (complete) {
                                Toast.makeText(requireContext(), "Registration complete! 🎉", Toast.LENGTH_LONG).show()
                                isProcessing = false
                                (activity as? RegisterWizardActivity)?.finishRegistration()
                            }
                        }
                    }
                }
                .setNegativeButton("Add Photo") { _, _ ->
                    isProcessing = false
                    showImagePickerDialog()
                }
                .show()
        }
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")

        androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.Theme_PawSociety_Dialog)
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
        // Check if device has camera hardware
        val hasCamera = requireContext().packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
        if (!hasCamera) {
            Toast.makeText(requireContext(), "This device doesn't have a camera", Toast.LENGTH_SHORT).show()
            isProcessing = false
            return
        }

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
            // Check if fragment is still attached
            if (!isAdded) {
                Toast.makeText(context ?: return, "Fragment not ready", Toast.LENGTH_SHORT).show()
                isProcessing = false
                return
            }

            val photoFile = createImageFile()
            val context = requireContext()
            val authority = "${context.packageName}.fileprovider"
            
            val uri = try {
                FileProvider.getUriForFile(context, authority, photoFile)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                Toast.makeText(context, "FileProvider authority mismatch: $authority", Toast.LENGTH_LONG).show()
                isProcessing = false
                return
            }
            
            selectedImageUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(context ?: return, "Camera permission denied: ${e.message}", Toast.LENGTH_LONG).show()
            isProcessing = false
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context ?: return, "Failed to open camera: ${e.message}", Toast.LENGTH_LONG).show()
            isProcessing = false
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
        isProcessing = false
    }
}
