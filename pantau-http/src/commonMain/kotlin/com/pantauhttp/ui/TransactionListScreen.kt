package com.pantauhttp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pantauhttp.HttpTransaction
import com.pantauhttp.PantauHttpCore
import com.pantauhttp.TransactionState

@Composable
internal fun TransactionListScreen(onSelect: (String) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { InspectorViewModel(PantauHttpCore.transactions, scope) }
    val items by viewModel.items.collectAsState()
    val query by viewModel.query.collectAsState()
    val filter by viewModel.filter.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(items) { PantauHttpCore.markAllSeen() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pantau HTTP") },
                navigationIcon = {
                    IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.Delete, contentDescription = "Clear") }
                },
                actions = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("Method, URL or status code") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(StatusFilter.entries) { option ->
                    FilterChip(
                        selected = option == filter,
                        onClick = { viewModel.filter.value = option },
                        label = { Text(option.label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank() && filter == StatusFilter.All) "No transactions yet" else "No matching transactions",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { transaction ->
                        TransactionRow(transaction, onClick = { onSelect(transaction.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all transactions?") },
            text = { Text("This removes every recorded transaction from memory.") },
            confirmButton = {
                TextButton(onClick = { PantauHttpCore.clearTransactions(); confirmClear = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun TransactionRow(transaction: HttpTransaction, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        leadingContent = {
            Text(
                transaction.statusText,
                modifier = Modifier.width(48.dp),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = InspectorColors.status(transaction),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    transaction.method,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = InspectorColors.method(transaction.method),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    transaction.path,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        supportingContent = {
            val secondary = buildString {
                append(transaction.host ?: "-")
                append(" · ").append(transaction.timeText)
                if (transaction.state == TransactionState.Failed) {
                    append(" · ").append(transaction.errorDescription ?: "failed")
                } else {
                    if (transaction.durationText.isNotEmpty()) append(" · ").append(transaction.durationText)
                    if (transaction.state == TransactionState.Completed) append(" · ").append(transaction.sizeText)
                }
            }
            Text(secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        },
    )
}
