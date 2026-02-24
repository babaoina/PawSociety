// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
    // Remove google.gms.google.services for now - we'll add it differently
    // alias(libs.plugins.google.gms.google.services) apply false
}