package com.harikrishnarao.financetracker.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.harikrishnarao.financetracker.domain.Category

/** Icon and colour for each category: a tinted rounded tile like in the design. */
@Suppress("DEPRECATION") // ReceiptLong moved to AutoMirrored in newer icon sets
fun Category.icon(): ImageVector = when (this) {
    Category.FOOD -> Icons.Filled.Restaurant
    Category.TRAVEL -> Icons.Filled.DirectionsCar
    Category.SHOPPING -> Icons.Filled.ShoppingBag
    Category.BILLS -> Icons.Filled.ReceiptLong
    Category.HEALTH -> Icons.Filled.LocalHospital
    Category.ENTERTAINMENT -> Icons.Filled.SportsEsports
    Category.OTHER -> Icons.Filled.Category
    Category.UNCATEGORISED -> Icons.Filled.QuestionMark
    Category.INCOME -> Icons.Filled.Payments
    Category.TRANSFER -> Icons.Filled.SwapHoriz
}

fun Category.color(): Color = when (this) {
    Category.FOOD -> Color(0xFFFF8A4C)
    Category.TRAVEL -> Color(0xFF4FA3FF)
    Category.SHOPPING -> Color(0xFFC084FC)
    Category.BILLS -> Color(0xFFFBBF24)
    Category.HEALTH -> Color(0xFFFF6B81)
    Category.ENTERTAINMENT -> Color(0xFF2DD4BF)
    Category.OTHER -> Color(0xFF94A3B8)
    Category.UNCATEGORISED -> Color(0xFF8B8FA3)
    Category.INCOME -> Color(0xFF5EEAD4)
    Category.TRANSFER -> Color(0xFF818CF8)
}
