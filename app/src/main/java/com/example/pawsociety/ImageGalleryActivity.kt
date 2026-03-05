package com.example.pawsociety

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.pawsociety.api.ApiClient
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class ImageGalleryActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var btnClose: ImageView
    private lateinit var tvCounter: TextView
    private lateinit var btnDownload: ImageView
    private lateinit var btnShare: ImageView

    private var imageUrls: List<String> = listOf()
    private var currentPosition: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_gallery)

        // Make activity fullscreen
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // Get data from intent
        imageUrls = intent.getStringArrayListExtra("image_urls") ?: listOf()
        currentPosition = intent.getIntExtra("current_position", 0)

        initializeViews()
        setupViewPager()
        setupClickListeners()
    }

    private fun initializeViews() {
        viewPager = findViewById(R.id.gallery_view_pager)
        tabLayout = findViewById(R.id.gallery_tab_layout)
        btnClose = findViewById(R.id.btn_gallery_close)
        tvCounter = findViewById(R.id.tv_gallery_counter)
        btnDownload = findViewById(R.id.btn_gallery_download)
        btnShare = findViewById(R.id.btn_gallery_share)

        // Hide download and share for now (can implement later)
        btnDownload.visibility = View.GONE
        btnShare.visibility = View.GONE
    }

    private fun setupViewPager() {
        val adapter = GalleryImageAdapter(imageUrls)
        viewPager.adapter = adapter
        viewPager.currentItem = currentPosition

        // Update counter
        updateCounter(currentPosition)

        // Setup page change callback
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateCounter(position)
            }
        })

        // Setup tab indicators if more than 1 image
        if (imageUrls.size > 1) {
            tabLayout.visibility = View.VISIBLE
            TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()
        } else {
            tabLayout.visibility = View.GONE
        }
    }

    private fun updateCounter(position: Int) {
        tvCounter.text = "${position + 1}/${imageUrls.size}"
    }

    private fun setupClickListeners() {
        btnClose.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    // ViewPager Adapter
    inner class GalleryImageAdapter(private val images: List<String>) :
        RecyclerView.Adapter<GalleryImageAdapter.ImageViewHolder>() {

        inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val photoView: PhotoView = itemView.findViewById(R.id.photo_view)
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ImageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_gallery_image, parent, false)
            return ImageViewHolder(view)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            val imageUrl = images[position]
            val fullImageUrl = if (imageUrl.startsWith("http")) {
                imageUrl
            } else {
                "${ApiClient.FULL_BASE_URL}$imageUrl"
            }

            Glide.with(this@ImageGalleryActivity)
                .load(fullImageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(holder.photoView)
        }

        override fun getItemCount() = images.size
    }
}