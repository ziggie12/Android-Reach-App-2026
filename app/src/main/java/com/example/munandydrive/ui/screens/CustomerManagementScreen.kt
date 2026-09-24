package com.example.munandydrive.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.munandydrive.data.model.Customer
import com.example.munandydrive.ui.viewmodel.CustomerUiState
import com.example.munandydrive.ui.viewmodel.CustomerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerManagementScreen(navController: NavHostController, viewModel: CustomerViewModel) {
    val customers by viewModel.customers.collectAsState()
    val uiState   by viewModel.uiState.collectAsState()
    var search      by remember { mutableStateOf("") }
    var showAdd     by remember { mutableStateOf(false) }
    var editTarget  by remember { mutableStateOf<Customer?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshCustomers() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customer Management") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshCustomers() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, "Add Customer")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search customers…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = MaterialTheme.shapes.medium,
                singleLine = true
            )

            when {
                uiState is CustomerUiState.Loading && customers.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                }
                uiState is CustomerUiState.Error && customers.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text((uiState as CustomerUiState.Error).message,
                                color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.refreshCustomers() }) { Text("Retry") }
                        }
                    }
                }
                else -> {
                    val filtered = customers.filter {
                        it.name.contains(search, ignoreCase = true) ||
                        (it.address?.contains(search, ignoreCase = true) == true)
                    }
                    LazyColumn {
                        items(filtered) { customer ->
                            CustomerItem(customer, onEdit = { editTarget = customer })
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                                    Text("No customers found. Tap + to add one.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) AddCustomerDialog(
        onDismiss = { showAdd = false },
        onConfirm = { name, address, openingTime, closingTime, contact, products ->
            viewModel.createCustomer(name, address, openingTime, closingTime, contact, products)
            showAdd = false
        }
    )

    editTarget?.let { customer ->
        EditCustomerDialog(
            customer  = customer,
            onDismiss = { editTarget = null },
            onConfirm = { name, address, openingTime, closingTime, contact, products ->
                viewModel.editCustomer(customer.id, name, address, openingTime, closingTime, contact, products)
                editTarget = null
            },
            onDelete = {
                viewModel.deleteCustomer(customer.id)
                editTarget = null
            }
        )
    }
}

@Composable
fun CustomerItem(customer: Customer, onEdit: () -> Unit = {}) {
    ListItem(
        headlineContent = {
            Text(customer.name, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Column {
                customer.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (customer.openingTime != null) {
                        SuggestionChip(
                            onClick = {},
                            label  = { Text("${customer.openingTime} – ${customer.closingTime ?: "?"}",
                                style = MaterialTheme.typography.labelSmall) },
                            icon   = { Icon(Icons.Default.Schedule, null, Modifier.size(14.dp)) }
                        )
                    }
                    customer.contact?.let {
                        SuggestionChip(
                            onClick = {},
                            label  = { Text(it, style = MaterialTheme.typography.labelSmall) },
                            icon   = { Icon(Icons.Default.Phone, null, Modifier.size(14.dp)) }
                        )
                    }
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Edit")
            }
        }
    )
}

@Composable
private fun EditCustomerDialog(
    customer: Customer,
    onDismiss: () -> Unit,
    onConfirm: (name: String, address: String?, openingTime: String?,
                closingTime: String?, contact: String?, products: String?) -> Unit,
    onDelete: () -> Unit
) {
    var name        by remember { mutableStateOf(customer.name) }
    var address     by remember { mutableStateOf(customer.address ?: "") }
    var openingTime by remember { mutableStateOf(customer.openingTime ?: "") }
    var closingTime by remember { mutableStateOf(customer.closingTime ?: "") }
    var contact     by remember { mutableStateOf(customer.contact ?: "") }
    var products    by remember { mutableStateOf(customer.preferredProducts ?: "") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Customer") },
            text  = { Text("Delete \"${customer.name}\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = { TextButton({ showDeleteConfirm = false }) { Text("Cancel") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Business Name *") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("Address") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(openingTime, { openingTime = it },
                        label = { Text("Opens") }, singleLine = true,
                        placeholder = { Text("08:00") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(closingTime, { closingTime = it },
                        label = { Text("Closes") }, singleLine = true,
                        placeholder = { Text("17:00") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(contact, { contact = it }, label = { Text("Contact Number") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(products, { products = it }, label = { Text("Preferred Products") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name,
                        address.ifBlank { null },
                        openingTime.ifBlank { null },
                        closingTime.ifBlank { null },
                        contact.ifBlank { null },
                        products.ifBlank { null }
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
                TextButton(onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun AddCustomerDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, address: String?, openingTime: String?,
                closingTime: String?, contact: String?, products: String?) -> Unit
) {
    var name         by remember { mutableStateOf("") }
    var address      by remember { mutableStateOf("") }
    var openingTime  by remember { mutableStateOf("") }
    var closingTime  by remember { mutableStateOf("") }
    var contact      by remember { mutableStateOf("") }
    var products     by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Customer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Business Name *") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("Address") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(openingTime, { openingTime = it },
                        label = { Text("Opens") }, singleLine = true,
                        placeholder = { Text("08:00") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(closingTime, { closingTime = it },
                        label = { Text("Closes") }, singleLine = true,
                        placeholder = { Text("17:00") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(contact, { contact = it }, label = { Text("Contact Number") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(products, { products = it }, label = { Text("Preferred Products") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name,
                        address.ifBlank { null },
                        openingTime.ifBlank { null },
                        closingTime.ifBlank { null },
                        contact.ifBlank { null },
                        products.ifBlank { null }
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}
