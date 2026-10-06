package com.ramrom.emumanager.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ramrom.emumanager.ui.R

/** Colour tokens from product/DESIGN.md. The app is light-only by product decision. */
object EmuColors {
    val Canvas = Color(0xFFF6F7FB)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF171B2F)
    val InkSoft = Color(0xFF555B73)
    val InkFaint = Color(0xFF8A90A6)
    val Hairline = Color(0xFFE3E6EF)

    /** Reserved for the user's turn and focus. Never decoration. */
    val YourTurn = Color(0xFF4452FF)
    val YourTurnWash = Color(0xFFE8EAFF)

    val Mint = Color(0xFF16C08A)
    val MintInk = Color(0xFF0B8A62)
    val MintWash = Color(0xFFDCF6EC)
    val Berry = Color(0xFFFF5C8A)
    val Tangerine = Color(0xFFFF8A3D)
    val Sunshine = Color(0xFFFFC53D)
    val Sky = Color(0xFF36B5FF)
    val Grape = Color(0xFF8B6CFF)
    val AlertRed = Color(0xFFE5484D)
    val AlertWash = Color(0xFFFDE4E4)
    val HandheldScreen = Color(0xFF232849)

    /** Unlock (grape on its wash); community source (sky ink on sky wash); caveat (sunshine wash). */
    val LockInk = Color(0xFF5B3FD6)
    val LockWash = Color(0xFFEFEAFF)
    val CommunityInk = Color(0xFF0A6FB0)
    val CommunityWash = Color(0xFFE1F3FF)
    val CaveatInk = Color(0xFF8A5A00)
    val CaveatWash = Color(0xFFFFF1CC)
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Bricolage = FontFamily(variable(R.font.bricolage_grotesque, 700), variable(R.font.bricolage_grotesque, 800))
val Figtree = FontFamily(
    variable(R.font.figtree, 400), variable(R.font.figtree, 500), variable(R.font.figtree, 600),
    variable(R.font.figtree, 700), variable(R.font.figtree, 800),
)
val GeistMono = FontFamily(variable(R.font.geist_mono, 500))

object EmuType {
    /** Milestones only ("Let's bring your handheld to life.", "You're ready to play."). */
    val Display = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight(800), fontSize = 40.sp, lineHeight = 41.sp, letterSpacing = (-0.03).em, color = EmuColors.Ink)
    /** One per screen: the screen's job. */
    val Headline = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.025).em, color = EmuColors.Ink)
    val Headline2 = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 24.sp, letterSpacing = (-0.015).em, color = EmuColors.Ink)
    val Title = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp, color = EmuColors.Ink)
    val Lead = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp, color = EmuColors.InkSoft)
    val Body = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, color = EmuColors.InkSoft)
    val Button = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = EmuColors.Ink)
    val Label = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(800), fontSize = 12.sp, letterSpacing = 0.07.em, color = EmuColors.InkFaint)
    val Hint = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.Medium, fontSize = 13.5.sp, color = EmuColors.InkSoft)
    val Detail = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = EmuColors.InkSoft)
    val Value = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = 24.sp, color = EmuColors.Ink)
}

object EmuShape {
    val Sm = 8.dp
    val Md = 14.dp
    val Lg = 18.dp
}

object EmuSpace {
    val Xs = 6.dp
    val Sm = 10.dp
    val Md = 14.dp
    val Lg = 26.dp
    val Xl = 32.dp
}
