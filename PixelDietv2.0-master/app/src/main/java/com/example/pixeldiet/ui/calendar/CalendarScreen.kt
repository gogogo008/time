package com.example.pixeldiet.ui.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pixeldiet.model.AppUsage
import com.example.pixeldiet.ui.common.WrappedBarChart
import com.example.pixeldiet.ui.common.WrappedMaterialCalendar
import com.example.pixeldiet.viewmodel.SharedViewModel

@Composable
fun CalendarScreen(viewModel: SharedViewModel) {

    // Flow를 Compose에서 직접 수집
    val decoratorData by viewModel.calendarDecoratorDataFlow.collectAsState(initial = emptyList())
    val statsText by viewModel.calendarStatsTextFlow.collectAsState(initial = "")
    val streakText by viewModel.streakTextFlow.collectAsState(initial = "")
    val chartData by viewModel.chartDataFlow.collectAsState(initial = emptyList())
    val goalMinutes by viewModel.calendarGoalTimeFlow.collectAsState(initial = 0)

    val appList by viewModel.appUsageListFlow.collectAsState(initial = emptyList())
    val trackedPackages by viewModel.trackedPackagesFlow.collectAsState(initial = emptySet())

    val selectedFilterLabel by viewModel.selectedFilterTextFlow.collectAsState(initial = "전체")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 스피너
        item {
            FilterSpinner(
                appList = appList,
                trackedPackages = trackedPackages,
                selectedLabel = selectedFilterLabel,
                onFilterSelected = { pkgOrNull ->
                    viewModel.setCalendarFilter(pkgOrNull)
                }
            )
        }

        // 2. 캘린더
        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                WrappedMaterialCalendar(
                    modifier = Modifier.fillMaxWidth(),
                    decoratorData = decoratorData,
                    onMonthChanged = { year, month ->
                        viewModel.setSelectedMonth(year, month)
                    }
                )
            }
        }

        // 3. 안내 문구
        item {
            Text(
                streakText,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                statsText,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // 4. 그래프
        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "이번 달 사용 시간",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(16.dp))

                    WrappedBarChart(
                        modifier = Modifier.fillMaxSize(),
                        chartData = chartData,
                        goalLine = goalMinutes.takeIf { it > 0 }?.toFloat()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSpinner(
    appList: List<AppUsage>,
    trackedPackages: Set<String>,
    selectedLabel: String,
    onFilterSelected: (String?) -> Unit
) {
    val trackedApps by remember(appList, trackedPackages) {
        derivedStateOf {
            appList.filter { it.packageName in trackedPackages }
                .sortedBy { it.appLabel.lowercase() }
        }
    }

    val options: List<Pair<String?, String>> = remember(trackedApps) {
        buildList {
            add(null to "전체")
            trackedApps.forEach { app -> add(app.packageName to app.appLabel) }
        }
    }

    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (pkg, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onFilterSelected(pkg)
                    }
                )
            }
        }
    }
}
