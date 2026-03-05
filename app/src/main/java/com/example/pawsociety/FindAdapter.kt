package com.example.pawsociety

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.RequestOptions
import com.example.pawsociety.api.ApiPost

class FindAdapter(
    private val posts: List<ApiPost>,
    private val onItemClick: (ApiPost) -> Unit
) : RecyclerView.Adapter<FindAdapter.FindViewHolder>() {

    class FindViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: FrameLayout = itemView.findViewById(R.id.post_container)
        val postImage: ImageView = itemView.findViewById(R.id.post_image)
        val petName: TextView = itemView.findViewById(R.id.pet_name)
        val statusBadge: TextView = itemView.findViewById(R.id.status_badge)

        init {
            // Force square container
            container.post {
                val width = container.width
                if (width > 0) {
                    val layoutParams = container.layoutParams
                    layoutParams.height = width
                    container.layoutParams = layoutParams
                    container.requestLayout()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FindViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_find_grid, parent, false)
        return FindViewHolder(view)
    }

    override fun onBindViewHolder(holder: FindViewHolder, position: Int) {
        try {
            val post = posts[position]

            // Set pet name
            holder.petName.text = post.petName
            holder.petName.visibility = View.VISIBLE

            // Set status badge - OVAL with colors
            holder.statusBadge.visibility = View.VISIBLE
            holder.statusBadge.setTextColor(Color.WHITE)

            when (post.status.lowercase()) {
                "lost" -> {
                    holder.statusBadge.text = "LOST"
                    holder.statusBadge.setBackgroundResource(R.drawable.status_badge_oval)
                    holder.statusBadge.background.setTint(Color.parseColor("#F44336"))
                }
                "found" -> {
                    holder.statusBadge.text = "FOUND"
                    holder.statusBadge.setBackgroundResource(R.drawable.status_badge_oval)
                    holder.statusBadge.background.setTint(Color.parseColor("#4CAF50"))
                }
                "adoption" -> {
                    holder.statusBadge.text = "ADOPTION"
                    holder.statusBadge.setBackgroundResource(R.drawable.status_badge_oval)
                    holder.statusBadge.background.setTint(Color.parseColor("#2196F3"))
                }
            }

            // Load image - HIGH QUALITY
            if (!post.imageUrls.isNullOrEmpty() && post.imageUrls.isNotEmpty()) {
                val imageUrl = post.imageUrls[0]
                val fullImageUrl = if (imageUrl.startsWith("http")) {
                    imageUrl
                } else {
                    "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}$imageUrl"
                }

                holder.postImage.visibility = View.VISIBLE

                // HD QUALITY image loading
                Glide.with(holder.itemView.context)
                    .load(fullImageUrl)
                    .apply(
                        RequestOptions()
                            .centerCrop()
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_report_image)
                            .override(800, 800)  // Higher resolution
                            .format(DecodeFormat.PREFER_ARGB_8888)  // Best quality
                            .skipMemoryCache(false)  // Use memory cache
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL) // Cache both
                    )
                    .into(holder.postImage)
            } else {
                // Show colored placeholder
                holder.postImage.visibility = View.VISIBLE
                val colors = listOf("#7A4F2B", "#B88B4A", "#4CAF50", "#2196F3", "#FF9800")
                val color = Color.parseColor(colors[position % colors.size])
                holder.postImage.setImageDrawable(ColorDrawable(color))
            }

            holder.itemView.setOnClickListener {
                onItemClick(post)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getItemCount() = posts.size
}