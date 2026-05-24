package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Account
import com.example.data.Category
import com.example.data.Transaction
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceApp(viewModel: FinanceViewModel) {
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val activeTab by viewModel.currentTab.collectAsStateWithLifecycle()

    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    var showAddTxDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.navigationBarsPadding(),
                tonalElevation = 8.dp
            ) {
                listOf(
                    Triple(0, Localization.get("calendar", language), Icons.Default.CalendarToday),
                    Triple(1, Localization.get("transactions", language), Icons.Default.List),
                    Triple(2, Localization.get("analytics", language), Icons.Default.BarChart),
                    Triple(3, Localization.get("settings", language), Icons.Default.Settings)
                ).forEach { (index, title, icon) ->
                    NavigationBarItem(
                        selected = activeTab == index,
                        onClick = { viewModel.currentTab.value = index },
                        icon = { Icon(icon, contentDescription = title) },
                        label = { Text(title, minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("nav_tab_$index")
                    )
                }
            }
        },
        floatingActionButton = {
            if (activeTab == 0 || activeTab == 1) {
                FloatingActionButton(
                    onClick = { showAddTxDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.testTag("fab_add_tx")
                ) {
                    Icon(Icons.Default.Add, contentDescription = Localization.get("add_transaction", language))
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                0 -> CalendarScreen(viewModel, onEditTx = { editingTransaction = it })
                1 -> TransactionsScreen(viewModel, onEditTx = { editingTransaction = it })
                2 -> AnalyticsScreen(viewModel)
                3 -> SettingsScreen(viewModel)
            }
        }
    }

    // Overlay dialogs
    if (showAddTxDialog) {
        AddOrEditTransactionDialog(
            viewModel = viewModel,
            onDismiss = { showAddTxDialog = false },
            transactionToEdit = null
        )
    }

    if (editingTransaction != null) {
        AddOrEditTransactionDialog(
            viewModel = viewModel,
            onDismiss = { editingTransaction = null },
            transactionToEdit = editingTransaction
        )
    }
}

// FORMAT HELPER
fun formatAmount(amount: Double, currency: String): String {
    val df = DecimalFormat("#,##0.00")
    val symbol = when (currency.uppercase()) {
        "BYN" -> "Br"
        "USD" -> "$"
        "EUR" -> "€"
        "RUB" -> "₽"
        else -> currency
    }
    return "${df.format(amount)} $symbol"
}

// MINIMAL FORMAT HELPER (without decimals if integer-like)
fun formatMinAmount(amount: Double, currency: String): String {
    val df = if (amount % 1.0 == 0.0) DecimalFormat("#,##0") else DecimalFormat("#,##0.0")
    val symbol = when (currency.uppercase()) {
        "BYN" -> "Br"
        "USD" -> "$"
        "EUR" -> "€"
        "RUB" -> "₽"
        else -> currency
    }
    return "${df.format(amount)}$symbol"
}

// CALENDAR FORMAT HELPER (Always rounds to whole units without cents / decimals)
fun formatCalendarAmount(amount: Double, currency: String): String {
    val roundedAmount = Math.round(amount).toDouble()
    val df = DecimalFormat("#,##0")
    val symbol = when (currency.uppercase()) {
        "BYN", "BR" -> ""
        "USD" -> "$"
        "EUR" -> "€"
        "RUB" -> "₽"
        else -> currency
    }
    return if (symbol.isEmpty()) df.format(roundedAmount) else "${df.format(roundedAmount)}$symbol"
}

// DYNAMIC DATE UTILS FOR CALENDAR
fun generateMonthDayItems(yearMonthStr: String): List<String> {
    // yearMonthStr is "yyyy-MM"
    if (yearMonthStr.length < 7) return emptyList()
    val year = yearMonthStr.substring(0, 4).toIntOrNull() ?: 2026
    val month = yearMonthStr.substring(5, 7).toIntOrNull()?.minus(1) ?: 4 // 0-indexed

    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, year)
    cal.set(Calendar.MONTH, month)
    cal.set(Calendar.DAY_OF_MONTH, 1)

    // Weeks start on Monday for Belarus/RU. 
    // Calendar.DAY_OF_WEEK: Sunday is 1, Monday is 2 ... Saturday is 7
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val leadingEmptyDays = when (firstDayOfWeek) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6
        else -> 0
    }

    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val totalItems = mutableListOf<String>()

    // Get trailing days of previous month for complete cells
    val prevMonthCal = cal.clone() as Calendar
    prevMonthCal.add(Calendar.MONTH, -1)
    val daysInPrevMonth = prevMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val prevYear = prevMonthCal.get(Calendar.YEAR)
    val prevMonth = prevMonthCal.get(Calendar.MONTH) + 1
    for (i in (daysInPrevMonth - leadingEmptyDays + 1)..daysInPrevMonth) {
        val formMonth = String.format(Locale.US, "%02d", prevMonth)
        val formDay = String.format(Locale.US, "%02d", i)
        totalItems.add("$prevYear-$formMonth-$formDay")
    }

    // Current Month Days
    val currentMonthRaw = month + 1
    for (i in 1..daysInMonth) {
        val formMonth = String.format(Locale.US, "%02d", currentMonthRaw)
        val formDay = String.format(Locale.US, "%02d", i)
        totalItems.add("$year-$formMonth-$formDay")
    }

    // Trailing next month padding
    val nextMonthCal = cal.clone() as Calendar
    nextMonthCal.add(Calendar.MONTH, 1)
    val nextYear = nextMonthCal.get(Calendar.YEAR)
    val nextMonth = nextMonthCal.get(Calendar.MONTH) + 1

    val remainingCells = (7 - (totalItems.size % 7)) % 7
    for (i in 1..remainingCells) {
        val formMonth = String.format(Locale.US, "%02d", nextMonth)
        val formDay = String.format(Locale.US, "%02d", i)
        totalItems.add("$nextYear-$formMonth-$formDay")
    }

    // Force exact 5 or 6 rows (35 or 42 cells)
    while (totalItems.size < 35) {
        val index = totalItems.size - 35 + 1 // dummy offset
        val formMonth = String.format(Locale.US, "%02d", nextMonth)
        val formDay = String.format(Locale.US, "%02d", index)
        totalItems.add("$nextYear-$formMonth-$formDay")
    }

    return totalItems
}

// ---------------- SCREEN 1: CALENDAR SCREEN ----------------
@Composable
fun CalendarScreen(viewModel: FinanceViewModel, onEditTx: (Transaction) -> Unit) {
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val rawSelectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val calendarMonth by viewModel.calendarYearMonth.collectAsStateWithLifecycle()
    val financialPeriodStartDate by viewModel.financialPeriodStartDate.collectAsStateWithLifecycle()

    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    val sdfDisplay = if (language == Localization.Language.RU) {
        SimpleDateFormat("d MMMM yyyy", Locale("ru"))
    } else {
        SimpleDateFormat("MMMM d, yyyy", Locale.US)
    }

    val selectedDateFormatted = remember(rawSelectedDate, language) {
        try {
            val sdfStr = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val parsed = sdfStr.parse(rawSelectedDate) ?: Date()
            sdfDisplay.format(parsed)
        } catch (e: Exception) {
            rawSelectedDate
        }
    }

    val days = remember(calendarMonth) { generateMonthDayItems(calendarMonth) }

    // Navigation triggers
    val currentCalendarInstance = remember(calendarMonth) {
        val cal = Calendar.getInstance()
        try {
            val yr = calendarMonth.substring(0, 4).toInt()
            val mn = calendarMonth.substring(5, 7).toInt() - 1
            cal.set(Calendar.YEAR, yr)
            cal.set(Calendar.MONTH, mn)
        } catch (_: Exception) {}
        cal
    }

    val monthDisplayName = remember(calendarMonth, language) {
        val sdf = if (language == Localization.Language.RU) {
            SimpleDateFormat("LLLL yyyy", Locale("ru"))
        } else {
            SimpleDateFormat("MMMM yyyy", Locale.US)
        }
        sdf.format(currentCalendarInstance.time).replaceFirstChar { it.uppercase() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("calendar_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // TOP CONTROLS: GO TO CURRENT PERIOD & HEADER
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                            viewModel.selectedDate.value = today
                            viewModel.calendarYearMonth.value = today.substring(0, 7)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_go_to_current")
                    ) {
                        Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Localization.get("go_to_current", language), fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            currentCalendarInstance.add(Calendar.MONTH, -1)
                            val yr = currentCalendarInstance.get(Calendar.YEAR)
                            val mn = String.format(Locale.US, "%02d", currentCalendarInstance.get(Calendar.MONTH) + 1)
                            viewModel.calendarYearMonth.value = "$yr-$mn"
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev Month")
                        }

                        Text(
                            text = monthDisplayName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        IconButton(onClick = {
                            currentCalendarInstance.add(Calendar.MONTH, 1)
                            val yr = currentCalendarInstance.get(Calendar.YEAR)
                            val mn = String.format(Locale.US, "%02d", currentCalendarInstance.get(Calendar.MONTH) + 1)
                            viewModel.calendarYearMonth.value = "$yr-$mn"
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                        }
                    }
                }
            }
        }

        // CALENDAR GRID
        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Weekday headers (Пн, Вт, Ср...)
                    val wds = if (language == Localization.Language.RU) {
                        listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
                    } else {
                        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        wds.forEach {
                            Text(
                                text = it,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Calendar Days Grid as custom rows of 7
                    val chunks = days.chunked(7)
                    chunks.forEach { rawRow ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            rawRow.forEach { dateKey ->
                                val indexInSelectedMonth = dateKey.substring(5, 7) == calendarMonth.substring(5, 7)
                                val dayNum = dateKey.substring(8, 10).toInt().toString()
                                val isSelected = dateKey == rawSelectedDate

                                // Calculate the dynamic balance on this day
                                val dayBalance = viewModel.getTotalActiveBalanceOnDate(dateKey, accounts, transactions)

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(0.85f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent
                                        )
                                        .clickable { viewModel.selectedDate.value = dateKey }
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = dayNum,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                            indexInSelectedMonth -> MaterialTheme.colorScheme.onSurface
                                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                        }
                                    )

                                    // Daily volume of money
                                    if (accounts.any { it.isActive }) {
                                        Text(
                                            text = formatCalendarAmount(dayBalance, viewModel.baseCurrency.value),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontStyle = run {
                                                val dayVal = dateKey.split("-").lastOrNull()?.toIntOrNull()
                                                val targetVal = financialPeriodStartDate.toIntOrNull()
                                                if (dayVal != null && targetVal != null && dayVal == targetVal) {
                                                    androidx.compose.ui.text.font.FontStyle.Italic
                                                } else {
                                                    androidx.compose.ui.text.font.FontStyle.Normal
                                                }
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = when {
                                                dayBalance > 0.0 -> androidx.compose.ui.graphics.Color(0xFF2D8035)
                                                dayBalance < 0.0 -> androidx.compose.ui.graphics.Color(0xFFD32F2F)
                                                else -> MaterialTheme.colorScheme.outline
                                            }
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // DETAILED BOTTOM BANNER FOR SELECTED DATE
        item {
            Text(
                text = selectedDateFormatted,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // BALANCES FOR EACH ACCOUNT SEPARATELY ON SELECTED DAY
        item {
            val dailyBalancesOnAccounts = viewModel.getAccountBalancesOnDate(rawSelectedDate, accounts, transactions)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = Localization.get("remaining_on_accounts", language),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        dailyBalancesOnAccounts.filter { it.first.isActive }.forEach { (acc, balance) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        if (acc.isActive) Icons.Default.AccountBalanceWallet else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = if (acc.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = acc.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (acc.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = formatAmount(balance, acc.currency),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // TRANSACTIONS FOR SELECTED DAY
        val dayTxs = viewModel.getTransactionsForDate(rawSelectedDate, transactions)
        if (dayTxs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = Localization.get("empty_transactions", language),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            items(dayTxs, key = { "cal_tx_${it.id}_${it.date}" }) { tx ->
                TransactionListItem(
                    tx = tx,
                    viewModel = viewModel,
                    onClick = { onEditTx(tx) }
                )
            }
        }
    }
}

// ---------------- TRANSACTION ITEM VIEW ----------------
@Composable
fun TransactionListItem(tx: Transaction, viewModel: FinanceViewModel, onClick: () -> Unit) {
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    val sourceAcc = accounts.find { it.id == tx.accountId }
    val destAcc = accounts.find { it.id == tx.toAccountId }
    val cat = categories.find { it.id == tx.categoryId }

    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        when (tx.type) {
                            "INCOME" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            "EXPENSE" -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val icon = when (tx.type) {
                    "TRANSFER" -> Icons.Default.SwapHoriz
                    else -> cat?.iconName?.let { CategoryIconHelper.getIcon(it) } ?: Icons.Default.Category
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = when (tx.type) {
                        "INCOME" -> MaterialTheme.colorScheme.primary
                        "EXPENSE" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (tx.type) {
                            "TRANSFER" -> "${sourceAcc?.name ?: "???"} → ${destAcc?.name ?: "???"}"
                            else -> cat?.name ?: Localization.get("type_transfer", language)
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (tx.isPlanned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = Localization.get("planned", language),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tx.description.ifEmpty {
                            when (tx.type) {
                                "INCOME" -> Localization.get("type_income", language)
                                "EXPENSE" -> Localization.get("type_expense", language)
                                else -> Localization.get("type_transfer", language)
                            }
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (tx.recurrence != "NONE") {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = tx.recurrence,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount
            if (tx.type == "TRANSFER") {
                val rate = tx.transferRate ?: 1.0
                val incomingAmt = if (sourceAcc?.currency == destAcc?.currency) tx.amount else tx.amount * rate
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "-${formatAmount(tx.amount, sourceAcc?.currency ?: "???")}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "+${formatAmount(incomingAmt, destAcc?.currency ?: "???")}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = androidx.compose.ui.graphics.Color(0xFF2D8035)
                    )
                }
            } else {
                Text(
                    text = "${if (tx.type == "INCOME") "+" else "-"}${formatAmount(tx.amount, sourceAcc?.currency ?: "BYN")}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = when (tx.type) {
                        "INCOME" -> androidx.compose.ui.graphics.Color(0xFF2D8035)
                        "EXPENSE" -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

// ---------------- SCREEN 2: TRANSACTIONS WITH SEARCH & FILTER ----------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionsScreen(viewModel: FinanceViewModel, onEditTx: (Transaction) -> Unit) {
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val search by viewModel.searchQuery.collectAsStateWithLifecycle()

    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    val filterAccId by viewModel.filterAccountId.collectAsStateWithLifecycle()
    val filterCatId by viewModel.filterCategoryId.collectAsStateWithLifecycle()
    val filterTxType by viewModel.filterType.collectAsStateWithLifecycle()

    val filteredList = remember(transactions, search, filterAccId, filterCatId, filterTxType) {
        transactions.filter { tx ->
            val descOk = search.isEmpty() || tx.description.contains(search, ignoreCase = true)
            val accOk = filterAccId == null || tx.accountId == filterAccId || tx.toAccountId == filterAccId
            val catOk = filterCatId == null || tx.categoryId == filterCatId
            val typeOk = filterTxType == null || tx.type == filterTxType
            descOk && accOk && catOk && typeOk
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("transactions_screen")
    ) {
        // TOP CONTROL SEARCH PANEL
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text(Localization.get("search_placeholder", language)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_field"),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Filtering Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Type Filter Choice (Income / Expense / Transfer)
                val typesList = listOf(null, "EXPENSE", "INCOME", "TRANSFER")
                typesList.forEach { typeVal ->
                    val isSelected = filterTxType == typeVal
                    val label = when (typeVal) {
                        null -> if (language == Localization.Language.RU) "Все типы" else "All Types"
                        "EXPENSE" -> Localization.get("type_expense", language)
                        "INCOME" -> Localization.get("type_income", language)
                        else -> Localization.get("type_transfer", language)
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.filterType.value = typeVal },
                        label = { Text(label, fontSize = 11.sp) },
                        modifier = Modifier.testTag("chip_type_$typeVal")
                    )
                }

                // Account Selector Filter
                Box {
                    var menuExpanded by remember { mutableStateOf(false) }
                    val currentAccountName = accounts.find { it.id == filterAccId }?.name
                        ?: (if (language == Localization.Language.RU) "Все счета" else "All Accounts")

                    AssistChip(
                        onClick = { menuExpanded = true },
                        label = { Text(currentAccountName, fontSize = 11.sp) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("chip_account_filter")
                    )
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(if (language == Localization.Language.RU) "Все счета" else "All Accounts") },
                            onClick = {
                                viewModel.filterAccountId.value = null
                                menuExpanded = false
                            }
                        )
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text(acc.name) },
                                onClick = {
                                    viewModel.filterAccountId.value = acc.id
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Category Selector Filter
                Box {
                    var menuExpanded by remember { mutableStateOf(false) }
                    val currentCategoryName = categories.find { it.id == filterCatId }?.name
                        ?: (if (language == Localization.Language.RU) "Все категории" else "All Categories")

                    AssistChip(
                        onClick = { menuExpanded = true },
                        label = { Text(currentCategoryName, fontSize = 11.sp) },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("chip_category_filter")
                    )
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(if (language == Localization.Language.RU) "Все категории" else "All Categories") },
                            onClick = {
                                viewModel.filterCategoryId.value = null
                                menuExpanded = false
                            }
                        )
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    viewModel.filterCategoryId.value = cat.id
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Quick clear filter button
                if (filterAccId != null || filterCatId != null || filterTxType != null) {
                    IconButton(
                        onClick = {
                            viewModel.filterAccountId.value = null
                            viewModel.filterCategoryId.value = null
                            viewModel.filterType.value = null
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.FilterAltOff,
                            contentDescription = "Clear Filters",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // RESULTS LIST
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = Localization.get("empty_transactions", language),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("filtered_tx_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredList, key = { "tx_${it.id}" }) { tx ->
                    TransactionListItem(
                        tx = tx,
                        viewModel = viewModel,
                        onClick = { onEditTx(tx) }
                    )
                }
            }
        }
    }
}

// ---------------- SCREEN 3: ANALYTICS ----------------
@Composable
fun AnalyticsScreen(viewModel: FinanceViewModel) {
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val periodType by viewModel.analyticsPeriodType.collectAsStateWithLifecycle()
    val selectedPeriodDate by viewModel.analyticsSelectedDate.collectAsStateWithLifecycle()

    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val rates by viewModel.nbrbRates.collectAsStateWithLifecycle()

    // Aggregate transactions by date interval
    val baseCur = viewModel.baseCurrency.value
    val baseRateInByn = rates[baseCur] ?: 1.0

    val matchingTxs = remember(transactions, periodType, selectedPeriodDate) {
        transactions.filter { tx ->
            when (periodType) {
                "DAY" -> tx.date == selectedPeriodDate
                "MONTH" -> tx.date.startsWith(selectedPeriodDate)
                "YEAR" -> tx.date.startsWith(selectedPeriodDate.substring(0, 4))
                else -> true // ALL TIME
            }
        }
    }

    // Calculations (converted to main Base Currency BYN/USD)
    var totalIncome = 0.0
    var totalExpense = 0.0

    // Categorized expense breakdown
    val expenseCategorySums = mutableMapOf<Long, Double>()
    val incomeCategorySums = mutableMapOf<Long, Double>()

    matchingTxs.forEach { tx ->
        val sourceAcc = accounts.find { it.id == tx.accountId }
        val curOfTx = sourceAcc?.currency ?: "BYN"
        val rateToByn = rates[curOfTx] ?: 1.0
        val valInByn = tx.amount * rateToByn
        val valInBaseCur = valInByn / baseRateInByn

        when (tx.type) {
            "INCOME" -> {
                totalIncome += valInBaseCur
                if (tx.categoryId != null) {
                    incomeCategorySums[tx.categoryId] = (incomeCategorySums[tx.categoryId] ?: 0.0) + valInBaseCur
                }
            }
            "EXPENSE" -> {
                totalExpense += valInBaseCur
                if (tx.categoryId != null) {
                    expenseCategorySums[tx.categoryId] = (expenseCategorySums[tx.categoryId] ?: 0.0) + valInBaseCur
                }
            }
        }
    }

    val netBalance = totalIncome - totalExpense

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("analytics_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // PERIOD TIMEFRAME RADIAL CONTROLS
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = Localization.get("analytics_period", language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Period Type Row switcher
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "DAY" to Localization.get("period_day", language),
                            "MONTH" to Localization.get("period_month", language),
                            "YEAR" to Localization.get("period_year", language),
                            "ALL" to Localization.get("period_all", language)
                        ).forEach { (code, label) ->
                            val isSelected = periodType == code
                            Button(
                                onClick = { viewModel.analyticsPeriodType.value = code },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("analytics_btn_$code"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Selected Period Nav Bar (if selection is not ALL)
                    if (periodType != "ALL") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                val cal = Calendar.getInstance()
                                val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                try {
                                    val dateToParse = when (periodType) {
                                        "DAY" -> selectedPeriodDate
                                        "MONTH" -> "$selectedPeriodDate-01"
                                        "YEAR" -> "$selectedPeriodDate-01-01"
                                        else -> "2026-05-23"
                                    }
                                    cal.time = formatter.parse(dateToParse) ?: Date()
                                } catch (_: Exception) {}

                                when (periodType) {
                                    "DAY" -> cal.add(Calendar.DAY_OF_YEAR, -1)
                                    "MONTH" -> cal.add(Calendar.MONTH, -1)
                                    "YEAR" -> cal.add(Calendar.YEAR, -1)
                                }

                                val resultStr = formatter.format(cal.time)
                                viewModel.analyticsSelectedDate.value = when (periodType) {
                                    "DAY" -> resultStr
                                    "MONTH" -> resultStr.substring(0, 7)
                                    else -> resultStr.substring(0, 4)
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev")
                            }

                            val titleStr = remember(selectedPeriodDate, periodType, language) {
                                try {
                                    val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                    val dateToParse = when (periodType) {
                                        "DAY" -> selectedPeriodDate
                                        "MONTH" -> "$selectedPeriodDate-01"
                                        "YEAR" -> "$selectedPeriodDate-01-01"
                                        else -> "2026-05-23"
                                    }
                                    val parsed = sdfIn.parse(dateToParse) ?: Date()

                                    val sdfOut = when (periodType) {
                                        "DAY" -> SimpleDateFormat("d MMMM yyyy", if (language == Localization.Language.RU) Locale("ru") else Locale.US)
                                        "MONTH" -> SimpleDateFormat("LLLL yyyy", if (language == Localization.Language.RU) Locale("ru") else Locale.US)
                                        else -> SimpleDateFormat("yyyy", if (language == Localization.Language.RU) Locale("ru") else Locale.US)
                                    }
                                    sdfOut.format(parsed).replaceFirstChar { it.uppercase() }
                                } catch (e: Exception) {
                                    selectedPeriodDate
                                }
                            }

                            Text(
                                titleStr,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )

                            IconButton(onClick = {
                                val cal = Calendar.getInstance()
                                val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                try {
                                    val dateToParse = when (periodType) {
                                        "DAY" -> selectedPeriodDate
                                        "MONTH" -> "$selectedPeriodDate-01"
                                        "YEAR" -> "$selectedPeriodDate-01-01"
                                        else -> "2026-05-23"
                                    }
                                    cal.time = formatter.parse(dateToParse) ?: Date()
                                } catch (_: Exception) {}

                                when (periodType) {
                                    "DAY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                                    "MONTH" -> cal.add(Calendar.MONTH, 1)
                                    "YEAR" -> cal.add(Calendar.YEAR, 1)
                                }

                                val resultStr = formatter.format(cal.time)
                                viewModel.analyticsSelectedDate.value = when (periodType) {
                                    "DAY" -> resultStr
                                    "MONTH" -> resultStr.substring(0, 7)
                                    else -> resultStr.substring(0, 4)
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next")
                            }
                        }
                    }
                }
            }
        }

        // FINANCIAL BRIEF SUMMARIES
        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Net Balance Center Block
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = Localization.get("net_balance", language),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = formatAmount(netBalance, baseCur),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            color = if (netBalance < 0.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                    // Income / Expense Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Localization.get("total_income", language),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Text(
                                text = formatAmount(totalIncome, baseCur),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Localization.get("total_expense", language),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Text(
                                text = formatAmount(totalExpense, baseCur),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // CHART BREAKDOWN: EXPENSE BY CATEGORY
        item {
            Text(
                text = Localization.get("chart_title", language),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (expenseCategorySums.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = Localization.get("no_expenses_data", language),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            val totalExpenseSumValue = expenseCategorySums.values.sum()
            val sortedCategories = expenseCategorySums.toList()
                .sortedByDescending { it.second }

            items(sortedCategories, key = { "expense_pie_${it.first}" }) { (catId, sum) ->
                val cat = categories.find { it.id == catId }
                val percentage = if (totalExpenseSumValue > 0) (sum / totalExpenseSumValue) else 0.0

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = cat?.iconName?.let { CategoryIconHelper.getIcon(it) } ?: Icons.Default.Category,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = cat?.name ?: "???",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = formatAmount(sum, baseCur),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f%%", percentage * 100),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { percentage.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.error,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

// ---------------- SCREEN 4: SETTINGS ----------------
@Composable
fun SettingsScreen(viewModel: FinanceViewModel) {
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val themeStr by viewModel.appTheme.collectAsStateWithLifecycle()

    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    val currentBalances = remember(accounts, transactions) {
        viewModel.getAccountBalancesOnDate("9999-12-31", accounts, transactions)
    }

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // THEME & LANGUAGE CONFIG
        item {
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Theme block
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = Localization.get("settings_theme", language),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "LIGHT" to Localization.get("theme_light", language),
                                "DARK" to Localization.get("theme_dark", language),
                                "SYSTEM" to Localization.get("theme_system", language)
                            ).forEach { (code, label) ->
                                val isSelected = themeStr == code
                                Button(
                                    onClick = { viewModel.appTheme.value = code },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("theme_btn_$code"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                    // Language block
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = Localization.get("settings_lang", language),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Localization.Language.RU to "Русский",
                                Localization.Language.EN to "English"
                            ).forEach { (langVal, label) ->
                                val isSelected = language == langVal
                                Button(
                                    onClick = { viewModel.appLanguage.value = langVal },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("lang_btn_${langVal.name}"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ACCOUNTS MANAGEMENT CARD
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Localization.get("settings_accounts", language),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = { showAddAccountDialog = true },
                    modifier = Modifier.testTag("btn_add_account")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Account", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        items(accounts, key = { "settings_account_${it.id}" }) { acc ->
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (acc.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val accBalance = currentBalances.find { it.first.id == acc.id }?.second ?: acc.initialBalance
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (acc.isActive) Icons.Default.AccountBalanceWallet else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = if (acc.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = acc.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (acc.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatAmount(accBalance, acc.currency),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (accBalance < 0.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (acc.isActive) Localization.get("account_active", language) else Localization.get("account_inactive", language),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Switch active/inactive
                        Switch(
                            checked = acc.isActive,
                            onCheckedChange = { viewModel.toggleAccountActive(acc) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("switch_active_${acc.id}")
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Delete
                        IconButton(onClick = { viewModel.deleteAccount(acc) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // CATEGORY CONFIG MANAGEMENT CARD
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Localization.get("settings_categories", language),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(onClick = { showAddCategoryDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Category", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        items(categories, key = { "settings_category_${it.id}" }) { cat ->
            ElevatedCard(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (cat.type == "EXPENSE") MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CategoryIconHelper.getIcon(cat.iconName),
                                contentDescription = null,
                                tint = if (cat.type == "EXPENSE") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = cat.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (cat.type == "EXPENSE") Localization.get("type_expense", language) else Localization.get("type_income", language),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    // Only allow deleting non-default entries to avoid breaking core look
                    if (!cat.isDefault) {
                        IconButton(onClick = { viewModel.deleteCategory(cat) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // FINANCIAL PERIOD SETTING
        item {
            val financialPeriodStartDate by viewModel.financialPeriodStartDate.collectAsStateWithLifecycle()
            var textInputVal by remember(financialPeriodStartDate) { mutableStateOf(financialPeriodStartDate) }
            
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (language == Localization.Language.RU) "Финансовый период" else "Financial Period",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    Text(
                        text = if (language == Localization.Language.RU) 
                            "Выберите численный день начала периода (1..31). Баланс на это число в календаре будет выделен курсивом." 
                            else "Select the day of the month for the period start (1..31). The balance on this date will be italicized in the calendar.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    
                    var dropdownExpanded by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = if (textInputVal.isEmpty()) "" else textInputVal,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(if (language == Localization.Language.RU) "День (1-31)" else "Day (1-31)") },
                                trailingIcon = {
                                    IconButton(onClick = { dropdownExpanded = true }) {
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = "Dropdown"
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { dropdownExpanded = true },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            
                            // Invisible tap overlay to cleanly open dropdown
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { dropdownExpanded = true }
                            )
                            
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.heightIn(max = 240.dp)
                            ) {
                                (1..31).forEach { day ->
                                    DropdownMenuItem(
                                        text = { Text(day.toString()) },
                                        onClick = {
                                            textInputVal = day.toString()
                                            viewModel.financialPeriodStartDate.value = day.toString()
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Button(
                            onClick = {
                                val todayDay = SimpleDateFormat("d", Locale.US).format(Date())
                                textInputVal = todayDay
                                viewModel.financialPeriodStartDate.value = todayDay
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (language == Localization.Language.RU) "Сегодня" else "Today")
                        }
                    }
                }
            }
        }

        // BACKUP & RESTORE
        item {
            val context = LocalContext.current
            var showBackupDialog by remember { mutableStateOf(false) }
            var showRestoreDialog by remember { mutableStateOf(false) }
            var restoreJsonInput by remember { mutableStateOf("") }
            var restoreSuccessMessage by remember { mutableStateOf<String?>(null) }
            var restoreErrorMessage by remember { mutableStateOf<String?>(null) }
            
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (language == Localization.Language.RU) "Резервное копирование" else "Backup & Restore",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    Text(
                        text = if (language == Localization.Language.RU) 
                            "Вы можете скопировать настройки и данные в текстовом формате для переноса или сохранения." 
                            else "You can copy all your settings and transactions in JSON text format to save or export.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val json = viewModel.exportBackupJson()
                                // Copy to clipboard
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Бюджет резервная копия", json)
                                clipboard.setPrimaryClip(clip)
                                
                                // Direct share text
                                try {
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_TEXT, json)
                                        type = "text/plain"
                                    }
                                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Сохранить резервную копию")
                                    context.startActivity(shareIntent)
                                } catch (_: Exception) {}
                                
                                showBackupDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (language == Localization.Language.RU) "Экспортировать" else "Export")
                        }
                        
                        OutlinedButton(
                            onClick = {
                                showRestoreDialog = true
                                restoreJsonInput = ""
                                restoreSuccessMessage = null
                                restoreErrorMessage = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (language == Localization.Language.RU) "Импортировать" else "Import")
                        }
                    }
                }
            }
            
            // Backup Success Dialog
            if (showBackupDialog) {
                AlertDialog(
                    onDismissRequest = { showBackupDialog = false },
                    title = { Text(if (language == Localization.Language.RU) "Экспорт успешен!" else "Export Success!") },
                    text = {
                        Text(
                            text = if (language == Localization.Language.RU) 
                                "Резервная копия скопирована в буфер обмена и отправлена в меню общего доступа. Сохраните этот текст в безопасное место."
                                else "Backup text has been copied to your clipboard and shared. Keep this text safe."
                        )
                    },
                    confirmButton = {
                        Button(onClick = { showBackupDialog = false }) {
                            Text("OK")
                        }
                    }
                )
            }
            
            // Restore Dialog
            if (showRestoreDialog) {
                Dialog(onDismissRequest = { showRestoreDialog = false }) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (language == Localization.Language.RU) "Восстановление данных" else "Restore Data",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Text(
                                text = if (language == Localization.Language.RU) 
                                    "Вставьте текст резервной копии (JSON) в поле ниже:" 
                                    else "Paste the backup JSON text into the input field below:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            
                            androidx.compose.material3.OutlinedTextField(
                                value = restoreJsonInput,
                                onValueChange = { restoreJsonInput = it },
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                placeholder = { Text("{ ... }") },
                                shape = RoundedCornerShape(12.dp)
                            )
                            
                            if (restoreSuccessMessage != null) {
                                Text(restoreSuccessMessage!!, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            if (restoreErrorMessage != null) {
                                Text(restoreErrorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showRestoreDialog = false },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(Localization.get("cancel", language))
                                }
                                
                                Button(
                                    onClick = {
                                        restoreSuccessMessage = null
                                        restoreErrorMessage = null
                                        val ok = viewModel.restoreBackupJson(restoreJsonInput)
                                        if (ok) {
                                            restoreSuccessMessage = if (language == Localization.Language.RU) "Успешно импортировано!" else "Successfully restored!"
                                        } else {
                                            restoreErrorMessage = if (language == Localization.Language.RU) "Ошибка импорта! Неверный формат." else "Restore failed! Invalid format."
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = restoreJsonInput.isNotBlank()
                                ) {
                                    Text(if (language == Localization.Language.RU) "Применить" else "Apply")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal forms
    if (showAddAccountDialog) {
        AddAccountDialog(
            viewModel = viewModel,
            onDismiss = { showAddAccountDialog = false }
        )
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            viewModel = viewModel,
            onDismiss = { showAddCategoryDialog = false }
        )
    }
}

// ---------------- DIALOG 1: ADD TRANSACTION ----------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddOrEditTransactionDialog(
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    transactionToEdit: Transaction? = null
) {
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()
    val rawSelectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()

    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var type by remember { mutableStateOf(transactionToEdit?.type ?: "EXPENSE") }
    var amountStr by remember { mutableStateOf(transactionToEdit?.amount?.toString() ?: "") }
    var accountId by remember { mutableStateOf(transactionToEdit?.accountId ?: accounts.firstOrNull()?.id ?: 0L) }
    var toAccountId by remember { mutableStateOf(transactionToEdit?.toAccountId ?: accounts.getOrNull(1)?.id ?: 0L) }
    var categoryId by remember { mutableStateOf(transactionToEdit?.categoryId ?: categories.firstOrNull { it.type == type }?.id ?: 0L) }
    var date by remember { mutableStateOf(transactionToEdit?.date ?: rawSelectedDate) }
    var description by remember { mutableStateOf(transactionToEdit?.description ?: "") }
    var recurrence by remember { mutableStateOf(transactionToEdit?.recurrence ?: "NONE") }
    var recurrencePeriod by remember { mutableStateOf(transactionToEdit?.recurrencePeriod ?: if (transactionToEdit?.recurrence != "NONE" && transactionToEdit?.recurrence != null) {
        when (transactionToEdit.recurrence) {
            "DAILY" -> "DAY"
            "WEEKLY" -> "WEEK"
            "MONTHLY" -> "MONTH"
            "YEARLY" -> "YEAR"
            else -> "NONE"
        }
    } else "NONE") }
    var recurrenceCount by remember { mutableStateOf(transactionToEdit?.recurrenceCount ?: 1) }

    // Dynamic list computed based on type
    val filteredCategories = remember(categories, type) {
        categories.filter { it.type == type }
    }

    // Auto update selected category when type toggles
    LaunchedEffect(type) {
        if (transactionToEdit == null) {
            val matchedCat = categories.firstOrNull { it.type == type }
            categoryId = matchedCat?.id ?: 0L
        }
    }

    // Cross-Currency Transfer Rate calculations
    val sourceAccObj = accounts.find { it.id == accountId }
    val targetAccObj = accounts.find { it.id == toAccountId }

    val showTransferRateInput = type == "TRANSFER" &&
            sourceAccObj != null && targetAccObj != null &&
            sourceAccObj.currency != targetAccObj.currency

    var transferRateStr by remember { mutableStateOf(transactionToEdit?.transferRate?.toString() ?: "") }

    // Fetch rate live from NBRB when choosing cross currency transfer
    LaunchedEffect(accountId, toAccountId, type) {
        if (showTransferRateInput && transactionToEdit == null) {
            val dynamicRate = viewModel.getCrossRate(sourceAccObj!!.currency, targetAccObj!!.currency)
            transferRateStr = String.format(Locale.US, "%.4f", dynamicRate)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Title
                item {
                    Text(
                        text = if (transactionToEdit == null) Localization.get("add_transaction", language) else Localization.get("edit_transaction", language),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                // TRANSACTION TYPE SELECTOR
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "EXPENSE" to Localization.get("type_expense", language),
                            "INCOME" to Localization.get("type_income", language),
                            "TRANSFER" to Localization.get("type_transfer", language)
                        ).forEach { (code, label) ->
                            val isSel = type == code
                            Button(
                                onClick = { type = code },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSel) {
                                        when (code) {
                                            "EXPENSE" -> MaterialTheme.colorScheme.error
                                            "INCOME" -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.tertiary
                                        }
                                    } else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // AMOUNT INPUT
                item {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text(Localization.get("amount", language)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tx_input_amount"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        suffix = {
                            Text(sourceAccObj?.currency ?: "BYN", fontWeight = FontWeight.Bold)
                        }
                    )
                }

                // SOURCE ACCOUNT (From / Main)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (type == "TRANSFER") (if (language == Localization.Language.RU) "Со счета" else "From Account") else Localization.get("account", language),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Box {
                            var exp by remember { mutableStateOf(false) }
                            val valName = accounts.find { it.id == accountId }?.name ?: "???"
                            OutlinedButton(
                                onClick = { exp = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(valName, color = MaterialTheme.colorScheme.onSurface)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
                                accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text("${acc.name} [${acc.currency}]") },
                                        onClick = {
                                            accountId = acc.id
                                            exp = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // TARGET ACCOUNT (ONLY FOR TRANSFERS)
                if (type == "TRANSFER") {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = Localization.get("target_account", language),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Box {
                                var exp by remember { mutableStateOf(false) }
                                val valName = accounts.find { it.id == toAccountId }?.name ?: "???"
                                OutlinedButton(
                                    onClick = { exp = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(valName, color = MaterialTheme.colorScheme.onSurface)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                }
                                DropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
                                    accounts.forEach { acc ->
                                        DropdownMenuItem(
                                            text = { Text("${acc.name} [${acc.currency}]") },
                                            onClick = {
                                                toAccountId = acc.id
                                                exp = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // EXTRATE FOR CURRENCY CONVERSION (ON TRANSFERS)
                if (showTransferRateInput) {
                    item {
                        OutlinedTextField(
                            value = transferRateStr,
                            onValueChange = { transferRateStr = it },
                            label = { Text(Localization.get("transfer_rate", language)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            helperText = {
                                Text(
                                    text = "1 ${sourceAccObj?.currency} = $transferRateStr ${targetAccObj?.currency} (НБРБ)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                }

                // TRANSFER PROJECTION SPECIAL DETAILS
                if (type == "TRANSFER") {
                    item {
                        val parsedAmount = amountStr.toDoubleOrNull() ?: 0.0
                        val rate = transferRateStr.toDoubleOrNull() ?: 1.0
                        val departingValStr = formatAmount(parsedAmount, sourceAccObj?.currency ?: "???")
                        
                        val rawArriving = if (sourceAccObj?.currency == targetAccObj?.currency) parsedAmount else parsedAmount * rate
                        val arrivingValStr = formatAmount(rawArriving, targetAccObj?.currency ?: "???")
                        
                        ElevatedCard(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (language == Localization.Language.RU) "Информация о переводе" else "Transfer Details",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (language == Localization.Language.RU) "Уходит со счета:" else "Departing from source:",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = departingValStr,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (language == Localization.Language.RU) "Приходит на счет:" else "Arriving to target:",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = arrivingValStr,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // CATEGORIES (ONLY FOR INCOME / EXPENSE)
                if (type != "TRANSFER") {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = Localization.get("category", language),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Box {
                                var exp by remember { mutableStateOf(false) }
                                val valName = filteredCategories.find { it.id == categoryId }?.name ?: "???"
                                OutlinedButton(
                                    onClick = { exp = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(valName, color = MaterialTheme.colorScheme.onSurface)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                }
                                DropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
                                    filteredCategories.forEach { cat ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(CategoryIconHelper.getIcon(cat.iconName), contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(cat.name)
                                                }
                                            },
                                            onClick = {
                                                categoryId = cat.id
                                                exp = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // RECURRENCE SELECTION AND MULTIPLIER (FOR EXPENSE & INCOME)
                if (type != "TRANSFER") {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = Localization.get("recurrence", language),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Box {
                                    var exp by remember { mutableStateOf(false) }
                                    val label = when (recurrencePeriod) {
                                        "NONE" -> Localization.get("rec_none", language)
                                        "DAY" -> Localization.get("rec_daily", language)
                                        "WEEK" -> Localization.get("rec_weekly", language)
                                        "MONTH" -> Localization.get("rec_monthly", language)
                                        "YEAR" -> Localization.get("rec_yearly", language)
                                        else -> recurrencePeriod
                                    }
                                    OutlinedButton(
                                        onClick = { exp = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(label, color = MaterialTheme.colorScheme.onSurface)
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                    }
                                    DropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
                                        listOf(
                                            "NONE" to Localization.get("rec_none", language),
                                            "DAY" to Localization.get("rec_daily", language),
                                            "WEEK" to Localization.get("rec_weekly", language),
                                            "MONTH" to Localization.get("rec_monthly", language),
                                            "YEAR" to Localization.get("rec_yearly", language)
                                        ).forEach { (code, lbl) ->
                                            DropdownMenuItem(
                                                text = { Text(lbl) },
                                                onClick = {
                                                    recurrencePeriod = code
                                                    recurrence = when (code) {
                                                        "DAY" -> "DAILY"
                                                        "WEEK" -> "WEEKLY"
                                                        "MONTH" -> "MONTHLY"
                                                        "YEAR" -> "YEARLY"
                                                        else -> "NONE"
                                                    }
                                                    exp = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Times-Per-Period Repetition Stepper
                            if (recurrencePeriod != "NONE") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = if (language == Localization.Language.RU) "Раз за период:" else "Times per period:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        IconButton(
                                            onClick = { if (recurrenceCount > 1) recurrenceCount-- },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Minus", modifier = Modifier.size(16.dp))
                                        }
                                        
                                        Text(
                                            text = "$recurrenceCount",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        )
                                        
                                        IconButton(
                                            onClick = { if (recurrenceCount < 10) recurrenceCount++ },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Plus", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // DATE SELECTOR ENTRY
                item {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text(Localization.get("date", language)) },
                        placeholder = { Text("yyyy-MM-dd") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // OPTIONAL DESCRIPTION
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(Localization.get("description", language)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // DIALOG CONFIRMATION BUTTONS row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(Localization.get("cancel", language))
                        }

                        Button(
                            onClick = {
                                val amtNum = amountStr.toDoubleOrNull() ?: 0.0
                                if (amtNum > 0 && accountId > 0) {
                                    val finalRate = if (type == "TRANSFER") transferRateStr.toDoubleOrNull() else null
                                    val catIdVal = if (type != "TRANSFER") categoryId else null

                                    // Auto check if it's planned (if date is in the future)
                                    val isPlannedTx = date > SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

                                    if (transactionToEdit == null) {
                                        viewModel.addTransaction(
                                            amount = amtNum,
                                            accountId = accountId,
                                            toAccountId = if (type == "TRANSFER") toAccountId else null,
                                            type = type,
                                            categoryId = catIdVal,
                                            date = date,
                                            description = description,
                                            recurrence = recurrence,
                                            recurrencePeriod = recurrencePeriod,
                                            recurrenceCount = recurrenceCount,
                                            transferRate = finalRate,
                                            isPlanned = isPlannedTx
                                        )
                                    } else {
                                        viewModel.updateTransaction(
                                            transactionToEdit.copy(
                                                amount = amtNum,
                                                accountId = accountId,
                                                toAccountId = if (type == "TRANSFER") toAccountId else null,
                                                type = type,
                                                categoryId = catIdVal,
                                                date = date,
                                                description = description,
                                                recurrence = recurrence,
                                                recurrencePeriod = recurrencePeriod,
                                                recurrenceCount = recurrenceCount,
                                                transferRate = finalRate,
                                                isPlanned = isPlannedTx
                                            )
                                        )
                                    }
                                    onDismiss()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_save_tx"),
                            shape = RoundedCornerShape(12.dp),
                            enabled = amountStr.toDoubleOrNull() != null
                        ) {
                            Text(Localization.get("save", language))
                        }
                    }
                }

                // Delete button (Only if editing!)
                if (transactionToEdit != null) {
                    item {
                        Button(
                            onClick = {
                                viewModel.deleteTransaction(transactionToEdit)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_delete_tx"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(Localization.get("delete", language))
                        }
                    }
                }
            }
        }
    }
}

// ---------------- DIALOG 2: ADD ACCOUNT ----------------
@Composable
fun AddAccountDialog(viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var initialBalanceStr by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("BYN") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = Localization.get("add_account", language),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(Localization.get("account_name", language)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_input_name"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = initialBalanceStr,
                    onValueChange = { initialBalanceStr = it },
                    label = { Text(Localization.get("initial_balance", language)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Currency Dropdown Selector Grid
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = Localization.get("currency", language),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("BYN", "USD", "EUR", "RUB").forEach { cur ->
                            val isSel = currency == cur
                            Button(
                                onClick = { currency = cur },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    cur,
                                    fontSize = 11.sp,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(Localization.get("cancel", language))
                    }

                    Button(
                        onClick = {
                            val initBal = initialBalanceStr.toDoubleOrNull() ?: 0.0
                            if (name.isNotEmpty()) {
                                viewModel.addAccount(name, currency, initBal)
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_save_account"),
                        shape = RoundedCornerShape(12.dp),
                        enabled = name.isNotEmpty()
                    ) {
                        Text(Localization.get("save", language))
                    }
                }
            }
        }
    }
}

// ---------------- DIALOG 3: ADD CATEGORY ----------------
@Composable
fun AddCategoryDialog(viewModel: FinanceViewModel, onDismiss: () -> Unit) {
    val language by viewModel.appLanguage.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("EXPENSE") }
    var iconName by remember { mutableStateOf("category") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = Localization.get("add_category", language),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(Localization.get("category_name", language)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Type Choice
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "EXPENSE" to Localization.get("type_expense", language),
                            "INCOME" to Localization.get("type_income", language)
                        ).forEach { (code, lbl) ->
                            val isSel = type == code
                            Button(
                                onClick = { type = code },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    lbl,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Icon picker layout grid
                item {
                    Text(
                        text = "Выбор иконки / Icon Selection",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CategoryIconHelper.availableIcons.chunked(4).forEach { rowIcons ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                rowIcons.forEach { (icName, icLabel) ->
                                    val isSel = iconName == icName
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSel) MaterialTheme.colorScheme.primaryContainer
                                                else Color.Transparent
                                            )
                                            .clickable { iconName = icName }
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = CategoryIconHelper.getIcon(icName),
                                            contentDescription = icLabel,
                                            tint = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(Localization.get("cancel", language))
                        }

                        Button(
                            onClick = {
                                if (name.isNotEmpty()) {
                                    viewModel.addCategory(name, type, iconName)
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = name.isNotEmpty()
                        ) {
                            Text(Localization.get("save", language))
                        }
                    }
                }
            }
        }
    }
}

// Extra field custom helper for material inputs to look beautiful
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    modifier: Modifier = Modifier,
    helperText: @Composable (() -> Unit)? = null
) {
    Column {
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            suffix = suffix,
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            shape = shape,
            modifier = modifier
        )
        if (helperText != null) {
            Spacer(modifier = Modifier.height(2.dp))
            helperText()
        }
    }
}
