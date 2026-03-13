package com.example.pawsociety

object PetData {
    // Dog breeds
    val dogBreeds = listOf(
        "Aspin", "Shih Tzu", "Labrador Retriever", "Golden Retriever",
        "German Shepherd", "Poodle", "Chow Chow", "Pug", "Beagle",
        "Dachshund", "Rottweiler", "Pomeranian", "Husky", "Corgi",
        "Maltese", "Chihuahua", "Pitbull", "Bulldog", "Boxer",
        "Shiba Inu", "Akita", "Samoyed", "Cocker Spaniel", "Doberman",
        "Great Dane", "Saint Bernard", "Siberian Husky", "Jack Russell",
        "Border Collie", "Australian Shepherd", "Bichon Frise"
    ).sorted()

    // Cat breeds
    val catBreeds = listOf(
        "Puspin", "Persian", "Siamese", "Maine Coon", "Bengal",
        "Sphynx", "Ragdoll", "British Shorthair", "Scottish Fold",
        "Abyssinian", "Burmese", "Russian Blue", "Norwegian Forest",
        "Birman", "Oriental Shorthair", "Devon Rex", "Cornish Rex",
        "Himalayan", "American Shorthair", "Exotic Shorthair"
    ).sorted()

    // Fish types
    val fishBreeds = listOf(
        "Goldfish", "Betta (Siamese Fighting Fish)", "Guppy", "Molly",
        "Platy", "Swordtail", "Angelfish", "Discus", "Oscar",
        "Cichlid", "Koi", "Tetra", "Barb", "Corydoras Catfish",
        "Plecostomus", "Danio", "Rainbowfish", "Killifish",
        "Arowana", "Flowerhorn", "Parrot Fish", "Gourami"
    ).sorted()

    // Bird types
    val birdBreeds = listOf(
        "Lovebird", "Parakeet (Budgie)", "Cockatiel", "African Grey Parrot",
        "Macaw", "Canary", "Finch", "Conure", "Amazon Parrot",
        "Eclectus Parrot", "Pigeon", "Dove", "Quaker Parrot",
        "Senegal Parrot", "Cockatoo", "Mynah Bird", "Java Sparrow",
        "Zebra Finch", "Gouldian Finch", "Ringneck Parakeet"
    ).sorted()

    fun getAllBreeds(): List<String> {
        return (dogBreeds + catBreeds + fishBreeds + birdBreeds).sorted()
    }

    fun filterBreeds(query: String): List<String> {
        return getAllBreeds().filter {
            it.contains(query, ignoreCase = true)
        }.take(10)
    }
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
