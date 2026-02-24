package com.example.pawsociety

object PetData {
    // Common pet breeds in the Philippines
    val dogBreeds = listOf(
        "Aspin", "Shih Tzu", "Labrador Retriever", "Golden Retriever",
        "German Shepherd", "Poodle", "Chow Chow", "Pug", "Beagle",
        "Dachshund", "Rottweiler", "Pomeranian", "Husky", "Corgi",
        "Maltese", "Chihuahua", "Pitbull", "Bulldog", "Boxer",
        "Shiba Inu", "Akita", "Samoyed", "Cocker Spaniel", "Doberman"
    )

    val catBreeds = listOf(
        "Puspin", "Persian", "Siamese", "Maine Coon", "Bengal",
        "Sphynx", "Ragdoll", "British Shorthair", "Scottish Fold",
        "Abyssinian", "Burmese", "Russian Blue", "Norwegian Forest"
    )

    val otherPets = listOf(
        "Rabbit", "Hamster", "Guinea Pig", "Bird", "Parrot",
        "Fish", "Turtle", "Lizard", "Snake", "Ferret",
        "Chicken", "Duck", "Goat", "Horse", "Cat", "Dog"
    )

    fun getAllBreeds(): List<String> {
        return (dogBreeds + catBreeds + otherPets).sorted()
    }

    fun filterBreeds(query: String): List<String> {
        return getAllBreeds().filter {
            it.contains(query, ignoreCase = true)
        }.take(10) // Limit to 10 suggestions
    }

    // Common locations in the Philippines
    val locations = listOf(
        "Metro Manila", "Quezon City", "Manila", "Makati", "Taguig",
        "Pasig", "Mandaluyong", "San Juan", "Marikina", "Pasay",
        "Paranaque", "Las Piñas", "Muntinlupa", "Valenzuela",
        "Caloocan", "Malabon", "Navotas", "Pateros",
        "Cebu City", "Davao City", "Zamboanga City", "Cagayan de Oro",
        "Baguio City", "Angeles City", "Iloilo City", "Bacolod City",
        "Tagaytay", "Laguna", "Cavite", "Rizal", "Bulacan",
        "Batangas", "Pampanga", "Nueva Ecija", "Tarlac", "Zambales",
        "Albay", "Camarines Sur", "Palawan", "Mindoro", "Marinduque",
        "Leyte", "Samar", "Bohol", "Negros Oriental", "Negros Occidental",
        "Bukidnon", "Misamis Oriental", "South Cotabato", "North Cotabato",
        "Sultan Kudarat", "Maguindanao", "Basilan", "Sulu", "Tawi-Tawi"
    )

    fun filterLocations(query: String): List<String> {
        return locations.filter {
            it.contains(query, ignoreCase = true)
        }.take(10)
    }
}