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
        // 🔥 ADD THIS - Category badge view
        val categoryBadge: TextView = itemView.findViewById(R.id.category_badge)

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

            // ===== SET CATEGORY BADGE =====
            try {
                var category = when (post.category) {
                    "Dogs" -> "DOG"
                    "Cats" -> "CAT"
                    "Fish" -> "FISH"
                    "Birds" -> "BIRD"
                    else -> null
                }

                // If no category, try to detect from pet type
                if (category == null) {
                    val petTypeLower = post.petType.lowercase()
                    category = when {
                        // DOG detection
                        petTypeLower.contains("dog") ||
                                petTypeLower.contains("aspin") ||
                                petTypeLower.contains("shih") ||
                                petTypeLower.contains("labrador") ||
                                petTypeLower.contains("golden") ||
                                petTypeLower.contains("german") ||
                                petTypeLower.contains("poodle") ||
                                petTypeLower.contains("chow") ||
                                petTypeLower.contains("pug") ||
                                petTypeLower.contains("beagle") ||
                                petTypeLower.contains("dachshund") ||
                                petTypeLower.contains("rottweiler") ||
                                petTypeLower.contains("pomeranian") ||
                                petTypeLower.contains("husky") ||
                                petTypeLower.contains("corgi") ||
                                petTypeLower.contains("maltese") ||
                                petTypeLower.contains("chihuahua") ||
                                petTypeLower.contains("pitbull") ||
                                petTypeLower.contains("bulldog") ||
                                petTypeLower.contains("boxer") ||
                                petTypeLower.contains("shiba") ||
                                petTypeLower.contains("akita") ||
                                petTypeLower.contains("samoyed") ||
                                petTypeLower.contains("cocker") ||
                                petTypeLower.contains("doberman") ||
                                petTypeLower.contains("great dane") ||
                                petTypeLower.contains("saint bernard") ||
                                petTypeLower.contains("siberian") ||
                                petTypeLower.contains("jack russell") ||
                                petTypeLower.contains("border collie") ||
                                petTypeLower.contains("australian shepherd") ||
                                petTypeLower.contains("bichon") ||
                                petTypeLower.contains("unknown dog") ||
                                petTypeLower.contains("other dog") -> "DOG"

                        // CAT detection
                        petTypeLower.contains("cat") ||
                                petTypeLower.contains("puspin") ||
                                petTypeLower.contains("persian") ||
                                petTypeLower.contains("siamese") ||
                                petTypeLower.contains("maine coon") ||
                                petTypeLower.contains("bengal") ||
                                petTypeLower.contains("sphynx") ||
                                petTypeLower.contains("ragdoll") ||
                                petTypeLower.contains("british shorthair") ||
                                petTypeLower.contains("scottish fold") ||
                                petTypeLower.contains("abyssinian") ||
                                petTypeLower.contains("burmese") ||
                                petTypeLower.contains("russian blue") ||
                                petTypeLower.contains("norwegian forest") ||
                                petTypeLower.contains("birman") ||
                                petTypeLower.contains("oriental shorthair") ||
                                petTypeLower.contains("devon rex") ||
                                petTypeLower.contains("cornish rex") ||
                                petTypeLower.contains("himalayan") ||
                                petTypeLower.contains("american shorthair") ||
                                petTypeLower.contains("exotic shorthair") ||
                                petTypeLower.contains("unknown cat") ||
                                petTypeLower.contains("other cat") -> "CAT"

                        // FISH detection
                        petTypeLower.contains("fish") ||
                                petTypeLower.contains("goldfish") ||
                                petTypeLower.contains("betta") ||
                                petTypeLower.contains("guppy") ||
                                petTypeLower.contains("molly") ||
                                petTypeLower.contains("platy") ||
                                petTypeLower.contains("swordtail") ||
                                petTypeLower.contains("angelfish") ||
                                petTypeLower.contains("discus") ||
                                petTypeLower.contains("oscar") ||
                                petTypeLower.contains("cichlid") ||
                                petTypeLower.contains("koi") ||
                                petTypeLower.contains("tetra") ||
                                petTypeLower.contains("barb") ||
                                petTypeLower.contains("corydoras") ||
                                petTypeLower.contains("plecostomus") ||
                                petTypeLower.contains("danio") ||
                                petTypeLower.contains("rainbowfish") ||
                                petTypeLower.contains("killifish") ||
                                petTypeLower.contains("arowana") ||
                                petTypeLower.contains("flowerhorn") ||
                                petTypeLower.contains("parrot fish") ||
                                petTypeLower.contains("gourami") ||
                                petTypeLower.contains("unknown fish") ||
                                petTypeLower.contains("other fish") -> "FISH"

                        // BIRD detection
                        petTypeLower.contains("bird") ||
                                petTypeLower.contains("parrot") ||
                                petTypeLower.contains("macaw") ||
                                petTypeLower.contains("lovebird") ||
                                petTypeLower.contains("parakeet") ||
                                petTypeLower.contains("budgie") ||
                                petTypeLower.contains("cockatiel") ||
                                petTypeLower.contains("african grey") ||
                                petTypeLower.contains("canary") ||
                                petTypeLower.contains("finch") ||
                                petTypeLower.contains("conure") ||
                                petTypeLower.contains("amazon") ||
                                petTypeLower.contains("eclectus") ||
                                petTypeLower.contains("pigeon") ||
                                petTypeLower.contains("dove") ||
                                petTypeLower.contains("quaker") ||
                                petTypeLower.contains("senegal") ||
                                petTypeLower.contains("cockatoo") ||
                                petTypeLower.contains("mynah") ||
                                petTypeLower.contains("java sparrow") ||
                                petTypeLower.contains("zebra finch") ||
                                petTypeLower.contains("gouldian finch") ||
                                petTypeLower.contains("ringneck") ||
                                petTypeLower.contains("unknown bird") ||
                                petTypeLower.contains("other bird") -> "BIRD"

                        else -> null
                    }
                }

                if (category != null) {
                    holder.categoryBadge.text = category
                    holder.categoryBadge.visibility = View.VISIBLE

                    // Set background color based on category
                    when (category) {
                        "DOG" -> {
                            holder.categoryBadge.setBackgroundResource(R.drawable.category_badge_rounded)
                            holder.categoryBadge.background.setTint(Color.parseColor("#B88B4A"))
                        }
                        "CAT" -> {
                            holder.categoryBadge.setBackgroundResource(R.drawable.category_badge_rounded)
                            holder.categoryBadge.background.setTint(Color.parseColor("#FF9800"))
                        }
                        "FISH" -> {
                            holder.categoryBadge.setBackgroundResource(R.drawable.category_badge_rounded)
                            holder.categoryBadge.background.setTint(Color.parseColor("#00BCD4"))
                        }
                        "BIRD" -> {
                            holder.categoryBadge.setBackgroundResource(R.drawable.category_badge_rounded)
                            holder.categoryBadge.background.setTint(Color.parseColor("#2196F3"))
                        }
                    }
                    holder.categoryBadge.setTextColor(Color.WHITE)
                    holder.categoryBadge.setPadding(8.dp, 2.dp, 8.dp, 2.dp)
                } else {
                    holder.categoryBadge.visibility = View.GONE
                }
            } catch (e: Exception) {
                holder.categoryBadge.visibility = View.GONE
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
                            .override(800, 800)
                            .format(DecodeFormat.PREFER_ARGB_8888)
                            .skipMemoryCache(false)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
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

// Add this extension function at the bottom
private val Int.dp: Int
    get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()