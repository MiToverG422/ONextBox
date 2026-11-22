package com.mi.onextbox.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mi.onextbox.R
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun AboutDonationPage(records: List<DonationRecord>?, refreshFailed: Boolean) {
    var sort by rememberSaveable { mutableStateOf(DonationSort.AmountDescending) }
    val status = when {
        refreshFailed && records == null -> R.string.about_donation_leaderboard_load_failed
        refreshFailed -> R.string.about_donation_leaderboard_cached
        records == null -> R.string.about_donation_leaderboard_loading
        records.isEmpty() -> R.string.about_donation_leaderboard_empty
        else -> null
    }
    val labels = listOf(
        stringResource(R.string.about_donation_sort_amount_desc),
        stringResource(R.string.about_donation_sort_amount_asc),
        stringResource(R.string.about_donation_sort_time_desc),
        stringResource(R.string.about_donation_sort_time_asc),
    )
    SettingsSection(stringResource(R.string.about_donation_responsible))
    SettingsGroup {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.wechat_appreciation_code),
                contentDescription = stringResource(R.string.about_donation_wechat_code),
                modifier = Modifier.widthIn(max = 172.dp).fillMaxWidth().aspectRatio(1f),
            )
            Text(
                text = stringResource(R.string.about_donation_wechat_note),
                style = COUITheme.textStyles.body1,
                color = COUITheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SettingsDivider()
        Text(
            text = stringResource(R.string.about_donation_instructions),
            style = COUITheme.textStyles.body1,
            color = COUITheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
    SettingsSection(stringResource(R.string.about_donation_leaderboard))
    SettingsGroup {
        SettingsWindowDropdownPreference(
            title = stringResource(R.string.about_donation_sort),
            items = labels,
            selectedIndex = sort.ordinal,
            onSelectedIndexChange = { DonationSort.entries.getOrNull(it)?.let { selected -> sort = selected } },
        )
        SettingsDivider()
        AnimatedContent(
            targetState = Triple(sort, records, status),
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "Donation leaderboard",
        ) { (order, entries, message) ->
            val sortedEntries = remember(order, entries) { entries.orEmpty().sortedFor(order) }
            Column {
                sortedEntries.forEachIndexed { index, record ->
                    if (index > 0) SettingsDivider()
                    DonationRecordRow(record)
                }
                if (message != null) {
                    if (sortedEntries.isNotEmpty()) SettingsDivider()
                    Text(
                        text = stringResource(message),
                        style = COUITheme.textStyles.body2,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DonationRecordRow(record: DonationRecord) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("donationRecord"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = record.name,
            style = COUITheme.textStyles.body1,
            color = COUITheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = record.amountText,
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = record.dateText,
                style = COUITheme.textStyles.body2,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
