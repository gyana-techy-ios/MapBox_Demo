package com.raahi.app.feature.language

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.core.os.LocaleListCompat
import com.raahi.app.R
import com.raahi.app.ui.components.RaahiTopBar
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.theme.Sizes

private data class LanguageOption(val tag: String, @StringRes val label: Int)

private val languageOptions = listOf(
    LanguageOption("", R.string.language_system),
    LanguageOption("en", R.string.language_english),
    LanguageOption("hi", R.string.language_hindi),
    LanguageOption("or", R.string.language_odia),
)

@Composable
fun LanguageRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    var selectedTag by remember {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(','))
    }
    Scaffold(
        modifier = modifier.testTag("screen_language"),
        topBar = { RaahiTopBar(title = stringResource(R.string.language_title), onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).selectableGroup()) {
            languageOptions.forEach { option ->
                val selected = option.tag == selectedTag
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.minTouchTarget)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = {
                                selectedTag = option.tag
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(option.tag))
                            },
                        )
                        .padding(horizontal = spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Text(
                        text = stringResource(option.label),
                        modifier = Modifier.padding(start = spacing.md),
                    )
                }
            }
        }
    }
}
