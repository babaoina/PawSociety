package com.example.pawsociety

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast
class   ProfileTabFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private var tabType: String? = null
    private var userId: String? = null

    companion object {
        fun newInstance(tabType: String, userId: String): ProfileTabFragment {
            val fragment = ProfileTabFragment()
            val args = Bundle()
            args.putString("tab_type", tabType)
            args.putString("user_id", userId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile_tab, container, false)

        recyclerView = view.findViewById(R.id.recycler_view)
        emptyView = view.findViewById(R.id.empty_view)

        tabType = arguments?.getString("tab_type")
        userId = arguments?.getString("user_id")

        setupRecyclerView()
        loadContent()

        return view
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
    }

    private fun loadContent() {
        when (tabType) {
            "New" -> loadNewContent()
            "My Pets" -> loadMyPetsContent()
            "Favorites" -> loadFavoritesContent()
            "Rescues" -> loadRescuesContent()
            else -> showEmptyState("Coming soon")
        }
    }

    private fun loadNewContent() {
        // Show all user's posts in chronological order
        val userPosts = UserDatabase.getAllPosts(requireContext())
            .filter { it.userId == userId }
            .sortedByDescending { it.createdAt }

        if (userPosts.isEmpty()) {
            showEmptyState("No posts yet")
        } else {
            showPosts(userPosts)
        }
    }

    private fun loadMyPetsContent() {
        // Show posts about user's own pets
        val myPets = UserDatabase.getAllPosts(requireContext())
            .filter { it.userId == userId }
            .take(6) // Limit to 6 for now

        if (myPets.isEmpty()) {
            showEmptyState("Add your pets")
        } else {
            showPosts(myPets)
        }
    }

    private fun loadFavoritesContent() {
        // Show saved/favorited posts
        showEmptyState("Save your favorite posts")
    }

    private fun loadRescuesContent() {
        // Show rescue stories
        showEmptyState("Share your rescue stories")
    }

    private fun showPosts(posts: List<Post>) {
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        // You'll need to create a PostGridAdapter for this
        // For now, just show a message
        Toast.makeText(requireContext(), "Showing ${posts.size} posts", Toast.LENGTH_SHORT).show()
        showEmptyState("Posts will appear here")
    }

    private fun showEmptyState(message: String) {
        emptyView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyView.text = message
    }
}