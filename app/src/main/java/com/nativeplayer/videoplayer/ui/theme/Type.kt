package com.nativeplayer.videoplayer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import com.nativeplayer.videoplayer.R

val QuicksandFontFamily = FontFamily(
    Font(R.font.quicksand_light, FontWeight.Light),
    Font(R.font.quicksand_regular, FontWeight.Normal),
    Font(R.font.quicksand_medium, FontWeight.Medium),
    Font(R.font.quicksand_bold, FontWeight.Bold)
)

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = QuicksandFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        lineBreak = LineBreak.Paragraph
    ),
    bodyMedium = TextStyle(
        fontFamily = QuicksandFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineBreak = LineBreak.Paragraph
    ),
    bodySmall = TextStyle(
        fontFamily = QuicksandFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineBreak = LineBreak.Paragraph
    ),
    titleLarge = TextStyle(
        fontFamily = QuicksandFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineBreak = LineBreak.Paragraph
    ),
    titleMedium = TextStyle(
        fontFamily = QuicksandFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineBreak = LineBreak.Paragraph
    ),
    titleSmall = TextStyle(
        fontFamily = QuicksandFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineBreak = LineBreak.Paragraph
    ),
    labelLarge = TextStyle(
        fontFamily = QuicksandFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineBreak = LineBreak.Paragraph
    ),
    labelMedium = TextStyle(
        fontFamily = QuicksandFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineBreak = LineBreak.Paragraph
    ),
    labelSmall = TextStyle(
        fontFamily = QuicksandFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineBreak = LineBreak.Paragraph
    )
)
