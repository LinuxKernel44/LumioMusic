package com.davidpallier.lumiomusic.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/** Extra bottom padding so the last list rows can scroll clear of an extended FAB. */
fun withFabClearance(innerPadding: PaddingValues): PaddingValues = PaddingValues(
    top = innerPadding.calculateTopPadding(),
    bottom = innerPadding.calculateBottomPadding() + 88.dp
)
