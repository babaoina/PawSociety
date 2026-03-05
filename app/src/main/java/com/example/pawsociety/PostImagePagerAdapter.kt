package com.example.pawsociety

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.RequestOptions

class PostImagePagerAdapter(
    private val imageUrls: List<String>
) : RecyclerView.Adapter<PostImagePagerAdapter.ImageViewHolder>() {

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.image_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post_image_pager, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUrl = imageUrls[position]
        val fullImageUrl = if (imageUrl.startsWith("http")) {
            imageUrl
        } else {
            "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}$imageUrl"
        }

        Glide.with(holder.itemView.context)
            .load(fullImageUrl)
            .apply(RequestOptions().transform(CenterCrop()))
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
            .into(holder.imageView)
    }

    override fun getItemCount() = imageUrls.size
}