package com.example.pawsociety.util

import android.animation.Animator
import android.view.View
import android.widget.ImageView
import com.airbnb.lottie.LottieAnimationView
import com.example.pawsociety.R

object LikeButtonHelper {

    fun setupLikeButton(
        likeAnimationView: LottieAnimationView,
        staticImageView: ImageView,
        initialIsLiked: Boolean,
        onLikeToggled: (Boolean) -> Unit
    ) {
        // Set initial state
        if (initialIsLiked) {
            // If already liked, show the final frame of animation (filled heart)
            likeAnimationView.progress = 1f
            likeAnimationView.visibility = View.VISIBLE
            staticImageView.visibility = View.GONE
        } else {
            // If not liked, show static unfilled heart
            likeAnimationView.visibility = View.GONE
            staticImageView.visibility = View.VISIBLE
            staticImageView.setImageResource(R.drawable.heart)
            staticImageView.clearColorFilter() // Remove any tint
        }

        // Set click listener on the container (or pass click handler separately)
    }

    fun playLikeAnimation(
        likeAnimationView: LottieAnimationView,
        staticImageView: ImageView,
        currentIsLiked: Boolean,
        onAnimationComplete: (Boolean) -> Unit
    ) {
        if (currentIsLiked) {
            // Currently liked -> going to unliked
            // For unlike, we can just hide animation and show static
            likeAnimationView.visibility = View.GONE
            staticImageView.visibility = View.VISIBLE
            staticImageView.setImageResource(R.drawable.heart)
            onAnimationComplete(false)
        } else {
            // Currently unliked -> going to liked
            staticImageView.visibility = View.GONE
            likeAnimationView.visibility = View.VISIBLE

            // Reset and play animation
            likeAnimationView.progress = 0f
            likeAnimationView.playAnimation()

            likeAnimationView.addAnimatorListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    // Keep the final frame (filled heart)
                    likeAnimationView.progress = 1f
                    onAnimationComplete(true)
                    likeAnimationView.removeAllAnimatorListeners()
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
        }
    }
}