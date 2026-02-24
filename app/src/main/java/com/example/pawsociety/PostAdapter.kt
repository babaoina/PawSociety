package com.example.pawsociety

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PostAdapter(private val posts: List<Post>) : RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val petName: TextView = itemView.findViewById(R.id.post_pet_name)
        val userName: TextView = itemView.findViewById(R.id.post_user_name)
        val status: TextView = itemView.findViewById(R.id.post_status)
        val description: TextView = itemView.findViewById(R.id.post_description)
        val location: TextView = itemView.findViewById(R.id.post_location)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = posts[position]
        holder.petName.text = post.petName
        holder.userName.text = "By: ${post.userName}"
        holder.status.text = post.status
        holder.description.text = post.description
        holder.location.text = "📍 ${post.location}"
    }

    override fun getItemCount() = posts.size
}