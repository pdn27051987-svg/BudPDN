package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryIconHelper {
    fun getIcon(name: String): ImageVector {
        return when (name) {
            "shopping_cart" -> Icons.Default.ShoppingCart
            "directions_car" -> Icons.Default.DirectionsCar
            "home" -> Icons.Default.Home
            "sports_esports" -> Icons.Default.SportsEsports
            "medical_services" -> Icons.Default.MedicalServices
            "restaurant" -> Icons.Default.Restaurant
            "checkroom" -> Icons.Default.Checkroom
            "school" -> Icons.Default.School
            "payments" -> Icons.Default.Payments
            "business_center" -> Icons.Default.BusinessCenter
            "redeem" -> Icons.Default.Redeem
            "trending_up" -> Icons.Default.TrendingUp
            "monetization_on" -> Icons.Default.MonetizationOn
            "category" -> Icons.Default.Category
            "work" -> Icons.Default.Work
            "flight" -> Icons.Default.Flight
            "fitness_center" -> Icons.Default.FitnessCenter
            "local_gas_station" -> Icons.Default.LocalGasStation
            "phone_android" -> Icons.Default.PhoneAndroid
            "electric_bolt" -> Icons.Default.ElectricBolt
            else -> Icons.Default.Category
        }
    }

    val availableIcons = listOf(
        "shopping_cart" to "Продукты",
        "directions_car" to "Транспорт",
        "home" to "Жилье",
        "sports_esports" to "Развлечения",
        "medical_services" to "Здоровье",
        "restaurant" to "Кафе",
        "checkroom" to "Одежда",
        "school" to "Образование",
        "payments" to "Деньги",
        "business_center" to "Бизнес",
        "redeem" to "Подарки",
        "trending_up" to "Рост",
        "monetization_on" to "Монеты",
        "work" to "Работа",
        "flight" to "Путешествия",
        "fitness_center" to "Спорт",
        "local_gas_station" to "Топливо",
        "phone_android" to "Связь",
        "electric_bolt" to "Коммунальные"
    )
}
