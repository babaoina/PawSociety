package com.example.pawsociety

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.pawsociety.api.ApiPost

class FindAdapter(
    private val posts: List<ApiPost>,
    private val onItemClick: (ApiPost) -> Unit
) : RecyclerView.Adapter<FindAdapter.FindViewHolder>() {

    private val colors = listOf(
        "#FF6B35", "#4CAF50", "#2196F3", "#9C27B0",
        "#F44336", "#009688", "#FF9800", "#3F51B5",
        "#E91E63", "#7A4F2B", "#00BCD4", "#8BC34A"
    )

    class FindViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: FrameLayout = itemView.findViewById(R.id.post_container)
        val postImage: ImageView = itemView.findViewById(R.id.post_image)
        val petName: TextView = itemView.findViewById(R.id.pet_name)
        val statusBadge: TextView = itemView.findViewById(R.id.status_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FindViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_find_grid, parent, false)
        return FindViewHolder(view)
    }

    override fun onBindViewHolder(holder: FindViewHolder, position: Int) {
        try {
            val post = posts[position]

            // Load post image if available
            if (!post.imageUrls.isNullOrEmpty() && post.imageUrls.isNotEmpty()) {
                val imageUrl = post.imageUrls[0]
                val fullImageUrl = if (imageUrl.startsWith("http")) {
                    imageUrl
                } else {
                    "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}$imageUrl"
                }
                
                holder.postImage.visibility = View.VISIBLE
                Glide.with(holder.itemView.context)
                    .load(fullImageUrl)
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(holder.postImage)
            } else {
                // Show placeholder with emoji and first letter
                holder.postImage.visibility = View.GONE
                
                val firstLetter = if (post.petName.isNotEmpty()) {
                    post.petName.first().toString()
                } else {
                    "?"
                }

                val emoji = when {
                    post.petType.contains("dog", ignoreCase = true) -> "🐶"
                    post.petType.contains("cat", ignoreCase = true) -> "🐱"
                    post.petType.contains("bird", ignoreCase = true) -> "🐦"
                    post.petType.contains("rabbit", ignoreCase = true) -> "🐰"
                    post.petType.contains("fish", ignoreCase = true) -> "🐟"
                    else -> "🐾"
                }

                // Create placeholder drawable
                val placeholderDrawable = android.graphics.drawable.LayerDrawable(
                    arrayOf(
                        android.graphics.drawable.ColorDrawable(Color.parseColor(colors[Math.abs(post.postId.hashCode()) % colors.size])),
                        android.graphics.drawable.InsetDrawable(
                            android.graphics.drawable.ColorDrawable(Color.TRANSPARENT),
                            0, 0, 0, 0
                        )
                    )
                )
                holder.postImage.setImageDrawable(placeholderDrawable)
            }

            // Set pet name
            holder.petName.text = post.petName

            // Set status badge
            holder.statusBadge.text = post.status
            when (post.status.lowercase()) {
                "lost" -> holder.statusBadge.setBackgroundColor(Color.parseColor("#F44336"))
                "found" -> holder.statusBadge.setBackgroundColor(Color.parseColor("#4CAF50"))
                "adoption" -> holder.statusBadge.setBackgroundColor(Color.parseColor("#2196F3"))
                else -> holder.statusBadge.setBackgroundColor(Color.parseColor("#7A4F2B"))
            }

            // Make container square
            holder.container.post {
                val width = holder.container.width
                if (width > 0) {
                    holder.container.layoutParams.height = width
                    holder.container.requestLayout()
                }
            }

            // Set click listener
            holder.itemView.setOnClickListener {
                onItemClick(post)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getItemCount() = posts.size
}