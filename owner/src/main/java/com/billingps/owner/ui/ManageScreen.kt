package com.billingps.owner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.billingps.owner.data.FnbItemInput
import com.billingps.owner.data.GroupInput
import com.billingps.owner.data.PackageInput
import kotlinx.coroutines.launch

@Composable
fun ManageScreen(
    state: OwnerUiState,
    onSubmitFnbItem: (FnbItemInput) -> Unit,
    onClearFnbItemNotice: () -> Unit,
    onSubmitPackage: (PackageInput) -> Unit,
    onClearPackageNotice: () -> Unit,
    onSubmitGroup: (GroupInput) -> Unit,
    onClearGroupNotice: () -> Unit,
) {
    val groups = remember(state.jenisPsList, state.paketMain) {
        state.jenisPsList.ifEmpty { state.paketMain.keys.sorted() }
    }
    val foodItems = remember(state.menuMakanan, state.stokMakanan) {
        val names = (state.menuMakanan.keys + state.stokMakanan.keys).sorted()
        names.map { it to (state.stokMakanan[it] ?: -1) }
    }
    val drinkItems = remember(state.menuMinuman, state.stokMinuman) {
        val names = (state.menuMinuman.keys + state.stokMinuman.keys).sorted()
        names.map { it to (state.stokMinuman[it] ?: -1) }
    }

    var category by remember(state.username) { mutableStateOf("makanan") }
    var itemName by remember(state.username) { mutableStateOf("") }
    var itemPrice by remember(state.username) { mutableStateOf("") }
    var itemStock by remember(state.username) { mutableStateOf("") }
    var fnbAction by remember(state.username) { mutableStateOf("add") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var selectedGroup by remember(state.username) { mutableStateOf("") }
    var packageAction by remember(state.username) { mutableStateOf("add") }
    var selectedPackage by remember(state.username) { mutableStateOf("") }
    var packageName by remember(state.username) { mutableStateOf("") }
    var packagePrice by remember(state.username) { mutableStateOf("") }
    var packageDuration by remember(state.username) { mutableStateOf("") }
    var showGroupDialog by remember(state.username) { mutableStateOf(false) }
    var newGroupName by remember(state.username) { mutableStateOf("") }

    LaunchedEffect(groups) {
        if (selectedGroup !in groups) selectedGroup = groups.firstOrNull().orEmpty()
    }
    LaunchedEffect(state.fnbItemNotice) {
        if (state.fnbItemNotice != null) {
            fnbAction = "add"
            itemName = ""
            itemPrice = ""
            itemStock = ""
        }
    }
    LaunchedEffect(state.packageNotice) {
        if (state.packageNotice != null) {
            packageName = ""
            packagePrice = ""
            packageDuration = ""
            selectedPackage = ""
        }
    }
    LaunchedEffect(state.groupNotice) {
        if (state.groupNotice != null) {
            newGroupName = ""
        }
    }

    val groupPackages = state.paketMain[selectedGroup].orEmpty()

    fun selectPackage(name: String) {
        selectedPackage = name
        packageName = name
        packagePrice = groupPackages[name]?.toString().orEmpty()
        packageDuration = state.paketDurasi[name]?.toString().orEmpty()
    }

    fun selectAdd() {
        packageAction = "add"
        selectedPackage = ""
        packageName = ""
        packagePrice = ""
        packageDuration = ""
    }

    fun selectGroup(group: String) {
        selectedGroup = group
        selectedPackage = ""
        packageName = ""
        packagePrice = ""
        packageDuration = ""
        if (packageAction == "update") {
            val first = state.paketMain[group].orEmpty().entries.firstOrNull()
            if (first != null) {
                selectedPackage = first.key
                packageName = first.key
                packagePrice = first.value.toString()
                packageDuration = state.paketDurasi[first.key]?.toString().orEmpty()
            }
        }
    }

    fun resetFnbForm() {
        fnbAction = "add"
        itemName = ""
        itemPrice = ""
        itemStock = ""
    }

    fun selectFnbItem(name: String, itemCategory: String, price: Int) {
        fnbAction = "update"
        category = itemCategory
        itemName = name
        itemPrice = price.toString()
        itemStock = ""
        scope.launch { listState.animateScrollToItem(1) }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = OwnerPanel),
                border = BorderStroke(1.dp, OwnerBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Kelola Data Owner",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = OwnerText,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Item F&B dan paket yang dikirim akan diterapkan oleh aplikasi billing " +
                            "ke data rental ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OwnerMuted,
                    )
                }
            }
        }

        item {
            FormCard(
                title = if (fnbAction == "update") "Edit Harga F&B" else "Tambah Item F&B",
                icon = Icons.Default.Restaurant,
                accent = OwnerCyan,
            ) {
                ChipRow {
                    FilterChip(
                        selected = category == "makanan",
                        onClick = { category = "makanan" },
                        enabled = fnbAction == "add",
                        label = { Text("Makanan") },
                        colors = filterColors(),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = category == "minuman",
                        onClick = { category = "minuman" },
                        enabled = fnbAction == "add",
                        label = { Text("Minuman") },
                        colors = filterColors(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nama item") },
                    enabled = fnbAction == "add",
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = itemPrice,
                        onValueChange = { itemPrice = it.filter { ch -> ch.isDigit() }.take(9) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Harga") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    if (fnbAction == "add") {
                        OutlinedTextField(
                            value = itemStock,
                            onValueChange = { itemStock = it.filter { ch -> ch.isDigit() }.take(6) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Stok awal") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }
                }
                if (fnbAction == "update") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Hanya harga yang diubah. Stok tetap seperti di aplikasi billing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OwnerMuted,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        onSubmitFnbItem(
                            FnbItemInput(
                                action = fnbAction,
                                category = category,
                                name = itemName.trim(),
                                price = itemPrice.toIntOrNull() ?: 0,
                                initialStock = itemStock.toIntOrNull() ?: 0,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = itemName.isNotBlank() &&
                        itemPrice.isNotBlank() &&
                        !state.isSubmittingFnbItem,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OwnerCyan,
                        contentColor = OwnerBg,
                    ),
                ) {
                    if (state.isSubmittingFnbItem) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = OwnerBg,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (fnbAction == "update") {
                            "Kirim Perubahan Harga"
                        } else {
                            "Kirim Item F&B"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (fnbAction == "update") {
                    TextButton(
                        onClick = { resetFnbForm() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSubmittingFnbItem,
                    ) {
                        Text("Batal Edit", color = OwnerMuted)
                    }
                }
            }
        }

        state.fnbItemNotice?.let { notice ->
            item { SuccessNotice(text = notice, onClose = onClearFnbItemNotice) }
        }

        item {
            SectionHeader(
                title = "Daftar F&B",
                subtitle = "${foodItems.size + drinkItems.size} item",
            )
        }
        if (foodItems.isEmpty() && drinkItems.isEmpty()) {
            item { EmptyState("Belum ada item F&B.") }
        }
        if (foodItems.isNotEmpty()) {
            item { SectionHeader(title = "Makanan", subtitle = "${foodItems.size} item") }
            items(foodItems, key = { "food-${it.first}" }) { (name, stock) ->
                FnbItemRow(
                    name = name,
                    category = "Makanan",
                    price = state.menuMakanan[name] ?: 0,
                    stock = stock,
                    onEdit = { selectFnbItem(name, "makanan", state.menuMakanan[name] ?: 0) },
                )
            }
        }
        if (drinkItems.isNotEmpty()) {
            item { SectionHeader(title = "Minuman", subtitle = "${drinkItems.size} item") }
            items(drinkItems, key = { "drink-${it.first}" }) { (name, stock) ->
                FnbItemRow(
                    name = name,
                    category = "Minuman",
                    price = state.menuMinuman[name] ?: 0,
                    stock = stock,
                    onEdit = { selectFnbItem(name, "minuman", state.menuMinuman[name] ?: 0) },
                )
            }
        }

        item {
            FormCard(
                title = "Paket Sesi",
                icon = Icons.Default.SportsEsports,
                accent = OwnerPurpleLight,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Grup PS",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = OwnerText,
                    )
                    OutlinedButton(
                        onClick = {
                            newGroupName = ""
                            showGroupDialog = true
                        },
                        enabled = !state.isSubmittingGroup,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Tambah Grup")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (groups.isEmpty()) {
                    Text(
                        "Belum ada grup PS. Tambah grup baru untuk mulai mengatur paket.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OwnerMuted,
                    )
                } else {
                    ChipRow {
                        groups.forEach { group ->
                            FilterChip(
                                selected = selectedGroup == group,
                                onClick = { selectGroup(group) },
                                label = { Text(group) },
                                colors = filterColors(),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ChipRow {
                        FilterChip(
                            selected = packageAction == "add",
                            onClick = { selectAdd() },
                            label = { Text("Tambah paket") },
                            colors = filterColors(),
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = packageAction == "update",
                            onClick = {
                                packageAction = "update"
                                val first = groupPackages.entries.firstOrNull()
                                if (first != null) selectPackage(first.key)
                            },
                            enabled = groupPackages.isNotEmpty(),
                            label = { Text("Edit paket") },
                            colors = filterColors(),
                        )
                    }
                    if (packageAction == "update") {
                        Spacer(Modifier.height(8.dp))
                        if (groupPackages.isEmpty()) {
                            Text(
                                "Belum ada paket di grup $selectedGroup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OwnerMuted,
                            )
                        } else {
                            Text(
                                "Pilih paket yang diedit",
                                style = MaterialTheme.typography.bodySmall,
                                color = OwnerMuted,
                            )
                            Spacer(Modifier.height(6.dp))
                            ChipRow {
                                groupPackages.keys.sorted().forEach { name ->
                                    FilterChip(
                                        selected = selectedPackage == name,
                                        onClick = { selectPackage(name) },
                                        label = { Text(name) },
                                        colors = filterColors(),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nama paket") },
                    enabled = packageAction == "add",
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = packagePrice,
                        onValueChange = { packagePrice = it.filter { ch -> ch.isDigit() }.take(9) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Harga") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = packageDuration,
                        onValueChange = {
                            packageDuration = it.filter { ch -> ch.isDigit() }.take(5)
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Durasi (menit)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Durasi 0 berarti main bebas. Durasi berlaku untuk nama paket yang sama " +
                        "di semua grup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OwnerMuted,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        onSubmitPackage(
                            PackageInput(
                                action = packageAction,
                                groupName = selectedGroup,
                                name = packageName.trim(),
                                price = packagePrice.toIntOrNull() ?: 0,
                                durationMinutes = packageDuration.toIntOrNull() ?: 0,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = groups.isNotEmpty() &&
                        selectedGroup.isNotBlank() &&
                        packageName.isNotBlank() &&
                        !state.isSubmittingPackage,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OwnerPurpleLight,
                        contentColor = OwnerBg,
                    ),
                ) {
                    if (state.isSubmittingPackage) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = OwnerBg,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (packageAction == "add") "Kirim Paket Baru" else "Kirim Perubahan Paket",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        state.groupNotice?.let { notice ->
            item { SuccessNotice(text = notice, onClose = onClearGroupNotice) }
        }

        state.packageNotice?.let { notice ->
            item { SuccessNotice(text = notice, onClose = onClearPackageNotice) }
        }

        item {
            SectionHeader(
                title = "Daftar Paket",
                subtitle = selectedGroup.ifBlank { "-" } + " / ${groupPackages.size} paket",
            )
        }
        if (groupPackages.isEmpty()) {
            item { EmptyState("Belum ada paket di grup ini.") }
        }
        items(groupPackages.entries.sortedBy { it.key }, key = { "pkg-${it.key}" }) { entry ->
            PackageRow(
                name = entry.key,
                price = entry.value,
                durationMinutes = state.paketDurasi[entry.key] ?: 0,
                onEdit = {
                    packageAction = "update"
                    selectPackage(entry.key)
                },
            )
        }
    }

    if (showGroupDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isSubmittingGroup) showGroupDialog = false
            },
            title = {
                Text(
                    "Tambah Grup Baru",
                    color = OwnerText,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    Text(
                        "Grup baru akan dibuat dengan paket default. Paket bisa diubah " +
                            "setelah grup muncul di daftar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OwnerMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nama grup") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSubmitGroup(GroupInput(newGroupName.trim()))
                        showGroupDialog = false
                    },
                    enabled = newGroupName.isNotBlank() && !state.isSubmittingGroup,
                ) {
                    Text("Kirim", color = OwnerGreen)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showGroupDialog = false },
                    enabled = !state.isSubmittingGroup,
                ) {
                    Text("Batal", color = OwnerMuted)
                }
            },
        )
    }
}

@Composable
private fun FormCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = OwnerPanel),
        border = BorderStroke(1.dp, OwnerBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = OwnerText,
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun filterColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = OwnerGreen.copy(alpha = 0.2f),
    selectedLabelColor = OwnerGreen,
    labelColor = OwnerMuted,
)

@Composable
private fun SuccessNotice(text: String, onClose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF063B2B)),
        border = BorderStroke(1.dp, OwnerGreen.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = OwnerGreen,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = OwnerGreen,
            )
            TextButton(onClick = onClose) {
                Text("Tutup", color = OwnerMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun FnbItemRow(
    name: String,
    category: String,
    price: Int,
    stock: Int,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = OwnerPanel2),
        border = BorderStroke(1.dp, OwnerBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = OwnerText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$category / ${rupiah(price)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OwnerMuted,
                )
            }
            Text(
                if (stock < 0) "Stok: -" else "Stok: $stock",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (stock <= 0) OwnerAmber else OwnerGreen,
            )
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = onEdit) {
                Text("Edit", color = OwnerCyan, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PackageRow(
    name: String,
    price: Int,
    durationMinutes: Int,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = OwnerPanel2),
        border = BorderStroke(1.dp, OwnerBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = OwnerText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${rupiah(price)} / " +
                        if (durationMinutes > 0) "$durationMinutes menit" else "main bebas",
                    style = MaterialTheme.typography.bodySmall,
                    color = OwnerMuted,
                )
            }
            TextButton(onClick = onEdit) {
                Text("Edit", color = OwnerPurpleLight, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
