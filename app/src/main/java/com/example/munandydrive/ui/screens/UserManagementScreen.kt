package com.example.munandydrive.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.munandydrive.data.model.User
import com.example.munandydrive.data.model.UserRole
import com.example.munandydrive.ui.viewmodel.UserUiState
import com.example.munandydrive.ui.viewmodel.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(navController: NavHostController, viewModel: UserViewModel) {
    val users    by viewModel.users.collectAsState()
    val uiState  by viewModel.uiState.collectAsState()
    var search          by remember { mutableStateOf("") }
    var showAdd         by remember { mutableStateOf(false) }
    var resetTarget     by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshUsers() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Management") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshUsers() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, "Add User")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = search, onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search users…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = MaterialTheme.shapes.medium,
                singleLine = true
            )

            when {
                uiState is UserUiState.Loading && users.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                }
                uiState is UserUiState.Error && users.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text((uiState as UserUiState.Error).message,
                                color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.refreshUsers() }) { Text("Retry") }
                        }
                    }
                }
                else -> {
                    val filtered = users.filter {
                        it.name.contains(search, ignoreCase = true) ||
                        it.email.contains(search, ignoreCase = true)
                    }
                    LazyColumn {
                        items(filtered) { user ->
                            UserListItem(
                                user = user,
                                onToggle = { viewModel.updateUserStatus(user.id, !user.isActive) },
                                onResetPassword = { resetTarget = user }
                            )
                            HorizontalDivider()
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                                    Text("No users found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) AddUserDialog(
        onDismiss = { showAdd = false },
        onConfirm = { name, email, password, role ->
            viewModel.createUser(name, email, password, role)
            showAdd = false
        }
    )

    resetTarget?.let { user ->
        ResetPasswordDialog(
            userName  = user.name,
            onDismiss = { resetTarget = null },
            onConfirm = { newPwd ->
                viewModel.resetUserPassword(user.id, newPwd)
                resetTarget = null
            }
        )
    }
}

@Composable
private fun UserListItem(user: User, onToggle: () -> Unit, onResetPassword: () -> Unit = {}) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoleBadge(user.role)
                Spacer(Modifier.width(8.dp))
                Text(user.name, fontWeight = FontWeight.SemiBold)
            }
        },
        supportingContent = { Text(user.email) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onResetPassword) {
                    Icon(Icons.Default.LockReset, "Reset Password",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = user.isActive, onCheckedChange = { onToggle() })
            }
        }
    )
}

@Composable
private fun RoleBadge(role: UserRole) {
    val (label, color) = when (role) {
        UserRole.ADMIN      -> "ADMIN"      to MaterialTheme.colorScheme.error
        UserRole.SUPERVISOR -> "SUPERVISOR" to MaterialTheme.colorScheme.tertiary
        UserRole.SALESMAN   -> "SALESMAN"   to MaterialTheme.colorScheme.primary
    }
    Surface(
        color = color,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ResetPasswordDialog(
    userName: String,
    onDismiss: () -> Unit,
    onConfirm: (newPassword: String) -> Unit
) {
    var newPwd  by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = newPwd.isNotBlank() && confirm.isNotBlank() && newPwd != confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Set a new password for $userName.",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = newPwd, onValueChange = { newPwd = it },
                    label = { Text("New Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                OutlinedTextField(
                    value = confirm, onValueChange = { confirm = it },
                    label = { Text("Confirm Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = mismatch
                )
                if (mismatch) {
                    Text("Passwords do not match", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newPwd) },
                enabled = newPwd.length >= 6 && newPwd == confirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Reset") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddUserDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, email: String, password: String, role: String) -> Unit
) {
    var name     by remember { mutableStateOf("") }
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role     by remember { mutableStateOf("Salesman") }
    var roleExpanded by remember { mutableStateOf(false) }
    val roles = listOf("Admin", "Supervisor", "Salesman")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New User") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(email, { email = it }, label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                ExposedDropdownMenuBox(roleExpanded, { roleExpanded = it }) {
                    OutlinedTextField(
                        value = role, onValueChange = {},
                        label = { Text("Role") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(roleExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(roleExpanded, { roleExpanded = false }) {
                        roles.forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r) },
                                onClick = { role = r; roleExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, email, password, role) },
                enabled = name.isNotBlank() && email.isNotBlank() && password.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}
