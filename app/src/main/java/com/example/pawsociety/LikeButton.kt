package com.example.pawsociety.widget

import android.animation.Animator
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.airbnb.lottie.LottieAnimationView
import com.example.pawsociety.R

class LikeButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var likeAnimation: LottieAnimationView
    private lateinit var likeStatic: ImageView
    var isLiked: Boolean = false
        private set

    init {
        LayoutInflater.from(context).inflate(R.layout.custom_like_button, this, true)

        likeAnimation = findViewById(R.id.like_animation)
        likeStatic = findViewById(R.id.like_static)

        // Set animation file
        likeAnimation.setAnimation("like.json")
        likeAnimation.repeatCount = 0
        likeAnimation.speed = 1.2f

        // Remove any existing listeners to prevent conflicts
        likeAnimation.removeAllAnimatorListeners()

        likeAnimation.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                Log.d("LikeButton", "Animation started")
            }

            override fun onAnimationEnd(animation: Animator) {
                Log.d("LikeButton", "Animation ended - showing filled heart")
                // FIX: Show filled heart after animation
                likeStatic.visibility = View.VISIBLE
                likeStatic.setImageResource(R.drawable.ic_heart_filled)
                likeAnimation.visibility = View.GONE
                likeAnimation.removeAllAnimatorListeners()
                // Re-attach listener for next time
                setupAnimationListener()
            }

            override fun onAnimationCancel(animation: Animator) {
                Log.d("LikeButton", "Animation cancelled")
                // FIX: Show appropriate heart on cancel
                likeStatic.visibility = View.VISIBLE
                likeStatic.setImageResource(if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
                likeAnimation.visibility = View.GONE
                likeAnimation.removeAllAnimatorListeners()
                setupAnimationListener()
            }

            override fun onAnimationRepeat(animation: Animator) {}
        })

        updateUI(false)
    }

    private fun setupAnimationListener() {
        likeAnimation.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                likeStatic.visibility = View.VISIBLE
                likeStatic.setImageResource(R.drawable.ic_heart_filled)
                likeAnimation.visibility = View.GONE
                likeAnimation.removeAllAnimatorListeners()
                setupAnimationListener()
            }
            override fun onAnimationCancel(animation: Animator) {
                likeStatic.visibility = View.VISIBLE
                likeStatic.setImageResource(if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
                likeAnimation.visibility = View.GONE
                likeAnimation.removeAllAnimatorListeners()
                setupAnimationListener()
            }
            override fun onAnimationRepeat(animation: Animator) {}
        })
    }

    fun setLiked(liked: Boolean, animate: Boolean = true) {
        if (isLiked == liked) return

        isLiked = liked
        Log.d("LikeButton", "setLiked: $liked, animate: $animate")

        if (animate && liked) {
            // Play animation
            likeStatic.visibility = View.GONE
            likeAnimation.visibility = View.VISIBLE
            likeAnimation.progress = 0f
            likeAnimation.playAnimation()
        } else {
            updateUI(liked)
        }
    }

    fun toggleLike() {
        if (!isEnabled) return
        setLiked(!isLiked, animate = true)
    }

    private fun updateUI(liked: Boolean) {
        likeStatic.visibility = View.VISIBLE
        likeAnimation.visibility = View.GONE
        likeAnimation.cancelAnimation()

        if (liked) {
            likeStatic.setImageResource(R.drawable.ic_heart_filled)
        } else {
            likeStatic.setImageResource(R.drawable.ic_heart_outline)
        }
    }
}