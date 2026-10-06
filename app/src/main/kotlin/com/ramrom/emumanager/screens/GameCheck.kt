package com.ramrom.emumanager.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ramrom.emumanager.knowledge.SearchHit
import com.ramrom.emumanager.knowledge.Verdict
import com.ramrom.emumanager.ui.components.ButtonKind
import com.ramrom.emumanager.ui.components.EmuButton
import com.ramrom.emumanager.ui.components.ListRow
import com.ramrom.emumanager.ui.components.Tag
import com.ramrom.emumanager.ui.components.TagKind
import com.ramrom.emumanager.ui.components.focusRing
import com.ramrom.emumanager.ui.handheld.Face
import com.ramrom.emumanager.ui.handheld.HandheldState
import com.ramrom.emumanager.ui.input.initialFocus
import com.ramrom.emumanager.ui.layout.GuidedScaffold
import com.ramrom.emumanager.ui.theme.EmuColors
import com.ramrom.emumanager.ui.theme.EmuType

/** A search result as S08 shows it. */
data class GameCheckRow(val hit: SearchHit, val systemName: String, val verdict: Verdict, val inPlan: Boolean)

/** S08 Game check: "will my games run?" from the offline index, before committing to a plan. */
@Composable
fun GameCheckScreen(
    query: String,
    onQuery: (String) -> Unit,
    results: List<GameCheckRow>?,
    searching: Boolean,
    chipName: String?,
    onAddSystem: (String) -> Unit,
    onDone: () -> Unit,
) {
    val focus = initialFocus()
    val keyboard = LocalSoftwareKeyboardController.current
    var fieldFocused by remember { mutableStateOf(false) }
    val best = results?.firstOrNull()?.verdict
    GuidedScaffold(
        handheld = HandheldState(awake = true, face = when (best) {
            null -> Face.Look; Verdict.Great, Verdict.Good -> Face.Happy; Verdict.Varies -> Face.LookDown; Verdict.Tough -> Face.Worried
        }),
        bubble = when {
            results == null -> "What do you want to play?"
            results.isEmpty() -> "I don't know that one yet."
            else -> null
        },
        hints = listOf("A" to "Type / select", "B" to "Back"),
        listHeavy = true,
        actions = { EmuButton("Done", onDone, kind = ButtonKind.Go) },
    ) {
        BasicText("Will my games run?", style = EmuType.Headline)
        Spacer(Modifier.height(10.dp))
        BasicTextField(
            query, onQuery,
            Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { fieldFocused = it.isFocused }
                .focusRing(fieldFocused, 16.dp)
                .background(EmuColors.Surface, RoundedCornerShape(16.dp))
                .border(1.dp, EmuColors.Hairline, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            textStyle = EmuType.Lead.copy(color = EmuColors.Ink),
            singleLine = true,
            cursorBrush = SolidColor(EmuColors.YourTurn),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            decorationBox = { inner ->
                Box { if (query.isEmpty()) BasicText("Type a game's name", style = EmuType.Lead.copy(color = EmuColors.InkSoft)); inner() }
            },
        )
        Spacer(Modifier.height(8.dp))
        BasicText(
            "Estimates by system${chipName?.let { " for $it" } ?: ""}, not tested game by game.",
            style = EmuType.Hint,
        )
        Spacer(Modifier.height(10.dp))
        when {
            searching && results == null -> BasicText("Loading the game list...", style = EmuType.Body)
            results == null -> {}
            results.isEmpty() -> BasicText("No match in the offline list. It may still run; the list grows with updates.", style = EmuType.Body)
            else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                results.forEach { r ->
                    ListRow(
                        r.hit.name,
                        detail = listOfNotNull(r.systemName, r.hit.year.takeIf { it > 0 }?.toString()).joinToString(" · "),
                        // Every row takes focus so the d-pad can scroll the list; only rows outside the plan act.
                        onClick = { if (!r.inPlan) onAddSystem(r.hit.system) },
                    ) {
                        Tag(r.verdict.label, when (r.verdict) {
                            Verdict.Great, Verdict.Good -> TagKind.Done; Verdict.Varies -> TagKind.Caveat; Verdict.Tough -> TagKind.Blocked
                        })
                        if (!r.inPlan) Tag("Add ${r.systemName}", TagKind.YourTurn)
                    }
                }
            }
        }
    }
}
