package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.ThemePreference

@Immutable
data class FinanceSemanticColors(
    val income: Color,
    val incomeBg: Color,
    val expense: Color,
    val expenseBg: Color,
    val warning: Color,
    val warningBg: Color,
    val savings: Color,
    val savingsBg: Color,
    val cardBorder: Color,
    val mutedText: Color
)

val LocalFinanceColors = staticCompositionLocalOf {
    FinanceSemanticColors(
        income = IncomeGreen,
        incomeBg = IncomeGreenBgLight,
        expense = ExpenseRed,
        expenseBg = ExpenseRedBgLight,
        warning = WarningAmber,
        warningBg = WarningAmberBgLight,
        savings = SavingsIndigo,
        savingsBg = SavingsIndigoBgLight,
        cardBorder = LightOutline,
        mutedText = LightTextSecondary
    )
}

private val LightColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueContainerLight,
    onPrimaryContainer = BrandBlueDark,
    secondary = SavingsIndigo,
    onSecondary = Color.White,
    secondaryContainer = SavingsIndigoBgLight,
    onSecondaryContainer = SavingsIndigo,
    tertiary = IncomeGreen,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    error = ExpenseRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueContainerDark,
    onPrimaryContainer = Color(0xFF93C5FD),
    secondary = Color(0xFF818CF8),
    onSecondary = Color.White,
    secondaryContainer = SavingsIndigoBgDark,
    onSecondaryContainer = Color(0xFFC7D2FE),
    tertiary = IncomeGreenDark,
    onTertiary = Color(0xFF052E16),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    error = ExpenseRedDark,
    onError = Color(0xFF450A0A)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyFinanceTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val semanticColors = if (isDark) {
        FinanceSemanticColors(
            income = IncomeGreenDark,
            incomeBg = IncomeGreenBgDark,
            expense = ExpenseRedDark,
            expenseBg = ExpenseRedBgDark,
            warning = WarningAmber,
            warningBg = WarningAmberBgDark,
            savings = Color(0xFF818CF8),
            savingsBg = SavingsIndigoBgDark,
            cardBorder = DarkOutline,
            mutedText = DarkTextSecondary
        )
    } else {
        FinanceSemanticColors(
            income = IncomeGreen,
            incomeBg = IncomeGreenBgLight,
            expense = ExpenseRed,
            expenseBg = ExpenseRedBgLight,
            warning = WarningAmber,
            warningBg = WarningAmberBgLight,
            savings = SavingsIndigo,
            savingsBg = SavingsIndigoBgLight,
            cardBorder = LightOutline,
            mutedText = LightTextSecondary
        )
    }

    CompositionLocalProvider(LocalFinanceColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
