package com.example.pawsociety

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.util.SessionManager

class ProfileTabFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private var tabType: String? = null
    private var userId: String? = null
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: ProfileViewModel

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

        sessionManager = SessionManager(requireContext())
        viewModel = ViewModelProvider(requireActivity())[ProfileViewModel::class.java]

        setupRecyclerView()
        loadContent()

        return view
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
    }

    private fun loadContent() {
        when (tabType) {
            "New", "My Pets" -> {
                viewModel.userPosts.observe(viewLifecycleOwner) { posts ->
                    if (posts.isNullOrEmpty()) {
                        showEmptyState("No posts yet")
                    } else {
                        showPosts(posts)
                    }
                }
            }
            "Favorites" -> {
                viewModel.favoritePosts.observe(viewLifecycleOwner) { posts ->
                    if (posts.isNullOrEmpty()) {
                        showEmptyState("No favorites yet")
                    } else {
                        showPosts(posts)
                    }
                }
            }
            else -> showEmptyState("Coming soon")
        }
    }

    private fun showPosts(posts: List<ApiPost>) {
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        // You'll need to create a PostGridAdapter for this
        // For now, just show empty state with count
        showEmptyState("${posts.size} posts")
    }

    private fun showEmptyState(message: String) {
        emptyView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyView.text = message
    }
}