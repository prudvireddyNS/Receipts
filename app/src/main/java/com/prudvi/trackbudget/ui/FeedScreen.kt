package com.prudvi.trackbudget.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.prudvi.trackbudget.model.Drop

data class WrappedFeedEntry(
    val periodKey: String,
    val title: String = "Latest Wrapped",
    val subtitle: String = "Your five-card spending story is ready",
    val seen: Boolean = false,
)

@Composable
fun FeedScreen(
    drops: List<Drop>,
    modifier: Modifier = Modifier,
    wrappedEntries: List<WrappedFeedEntry> = emptyList(),
    onOpenWrapped: (WrappedFeedEntry) -> Unit = {},
    onAdd: () -> Unit,
    onDropShared: (Drop) -> Unit = {},
    onNotUseful: (Drop) -> Unit = {},
    onCategory: (String) -> Unit = {},
) {
    LazyColumn(
        modifier.fillMaxSize().background(receiptsColors.paper),
        contentPadding = PaddingValues(
            start = ReceiptsSpace.screen,
            top = ReceiptsSpace.screen,
            end = ReceiptsSpace.screen,
            bottom = ReceiptsSpace.x16,
        ),
        verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x3),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Feed", Modifier.weight(1f), color = receiptsColors.ink, style = ReceiptsType.title)
                if (drops.isNotEmpty()) ReceiptLabel("${drops.size} new")
                ReceiptIconButton(Icons.Default.Add, "New receipt", onAdd)
            }
            ReceiptDivider(Modifier.padding(top = ReceiptsSpace.x3))
        }
        items(wrappedEntries, key = { "wrapped-${it.periodKey}" }) { entry ->
            WrappedFeedCard(entry, onClick = { onOpenWrapped(entry) })
        }
        if (drops.isEmpty()) {
            item { FeedEmptyState() }
        } else {
            itemsIndexed(drops, key = { _, drop -> drop.key }) { index, drop ->
                DropFeedCard(
                    drop = drop,
                    perforated = index == 0,
                    onShared = { onDropShared(drop) },
                    onNotUseful = { onNotUseful(drop) },
                    onCategory = { drop.categoryId?.let(onCategory) },
                )
            }
        }
    }
}

@Composable
private fun FeedEmptyState() {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(ReceiptsRadius.small))
            .background(receiptsColors.paper)
            .dashedStampBorder(receiptsColors.ruleHard)
            .padding(ReceiptsSpace.x4),
    ) {
        ReceiptLabel("Feed")
        Text("No Drops yet", Modifier.padding(top = ReceiptsSpace.x2), color = receiptsColors.ink, style = ReceiptsType.title)
        Text(
            "Give it a week. Drops need something to notice.",
            Modifier.padding(top = ReceiptsSpace.x2),
            color = receiptsColors.inkSoft,
            style = ReceiptsType.body,
        )
    }
}

@Composable
private fun WrappedFeedCard(entry: WrappedFeedEntry, onClick: () -> Unit) {
    ReceiptPaperCard(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Open ${entry.title}" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ReceiptLabel("WRAPPED")
                Text(
                    entry.title,
                    Modifier.padding(top = ReceiptsSpace.x2),
                    color = receiptsColors.ink,
                    style = ReceiptsType.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.subtitle,
                    Modifier.padding(top = ReceiptsSpace.x1),
                    color = receiptsColors.inkSoft,
                    style = ReceiptsType.meta,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!entry.seen) ReceiptPill("NEW", selected = true)
        }
    }
}

@Composable
private fun DropFeedCard(
    drop: Drop,
    perforated: Boolean,
    onShared: () -> Unit,
    onNotUseful: () -> Unit,
    onCategory: () -> Unit,
) {
    val shareState = rememberShareCaptureState(
        fileNamePrefix = "drop-${drop.key}",
        onFileCreated = onShared,
    )
    val cardColor = if (perforated) receiptsColors.paperRaised else receiptsColors.paper
    // Only color a drop when it names a real category — that color means "this is what it's
    // about," matching the same color you'd see for that category in Ledger/Where it went.
    // A drop with no category stays neutral instead of getting an arbitrary decorative tint.
    val visual = drop.categoryId?.let { categoryVisual(it) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(ReceiptsRadius.small)).background(cardColor)
            .border(BorderStroke(Dp.Hairline, receiptsColors.ruleHard), RoundedCornerShape(ReceiptsRadius.small)),
    ) {
        Column(
            Modifier.fillMaxWidth().captureForShare(shareState).background(cardColor).padding(ReceiptsSpace.x4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2)) {
                if (visual != null) ReceiptCategoryMark(drop.categoryId)
                ReceiptLabel(drop.kicker, color = visual?.color ?: receiptsColors.fade)
            }
            Text(
                drop.figure,
                Modifier.padding(top = ReceiptsSpace.x3),
                color = receiptsColors.ink,
                style = ReceiptsType.display,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(drop.line, Modifier.padding(top = ReceiptsSpace.x3), color = receiptsColors.inkSoft, style = ReceiptsType.body)
            if (perforated) ReceiptPerforation(Modifier.padding(vertical = ReceiptsSpace.x2))
            else ReceiptDivider(Modifier.padding(vertical = ReceiptsSpace.x2))
            ReceiptLabel("Receipts")
        }
        Column(
            Modifier.fillMaxWidth().padding(start = ReceiptsSpace.x4, end = ReceiptsSpace.x4, bottom = ReceiptsSpace.x4),
            verticalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2),
        ) {
            if (drop.categoryId != null) {
                FeedAction(
                    text = "See ${categoryName(drop.categoryId)} transactions",
                    description = "See ${categoryName(drop.categoryId)} transactions",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCategory,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ReceiptsSpace.x2), verticalAlignment = Alignment.CenterVertically) {
                FeedAction(
                    text = "Share",
                    description = "Share ${drop.kicker}",
                    modifier = Modifier.weight(1f),
                    onClick = shareState::share,
                )
                FeedAction(
                    text = "Not useful",
                    description = "Mark ${drop.kicker} as not useful",
                    modifier = Modifier.weight(1f),
                    quiet = true,
                    onClick = onNotUseful,
                )
            }
        }
    }
}

@Composable
private fun FeedAction(
    text: String,
    description: String,
    modifier: Modifier = Modifier,
    quiet: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier.height(ReceiptsSpace.x12)
            .border(BorderStroke(Dp.Hairline, receiptsColors.ruleHard), RoundedCornerShape(ReceiptsRadius.small))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .padding(horizontal = ReceiptsSpace.x3),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), color = if (quiet) receiptsColors.fade else receiptsColors.ink, style = ReceiptsType.label, maxLines = 1)
    }
}

@Composable
private fun ReceiptPaperCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth()
            .background(receiptsColors.paper, RoundedCornerShape(ReceiptsRadius.small))
            .border(BorderStroke(Dp.Hairline, receiptsColors.ruleHard), RoundedCornerShape(ReceiptsRadius.small))
            .padding(ReceiptsSpace.x4),
        content = content,
    )
}
