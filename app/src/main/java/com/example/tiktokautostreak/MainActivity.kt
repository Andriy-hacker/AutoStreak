package com.example.tiktokautostreak

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.tiktokautostreak.ui.theme.AutoStreakTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            AutoStreakTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val prefs = remember { getSharedPreferences("streak_prefs", Context.MODE_PRIVATE) }
                    
                    var contactsList by remember {
                        val savedJson = prefs.getString("contacts_json", null)
                        val initialList = if (savedJson != null) {
                            ContactConfig.fromJsonList(savedJson)
                        } else {
                            val legacyNames = prefs.getString("contact_names", "") ?: ""
                            ContactConfig.fromJsonList(legacyNames)
                        }
                        mutableStateOf(initialList.ifEmpty { listOf(ContactConfig(name = "")) })
                    }

                    fun saveContacts(newList: List<ContactConfig>) {
                        contactsList = newList
                        prefs.edit().putString("contacts_json", ContactConfig.toJsonList(newList)).apply()
                    }

                    MainScreen(
                        contactsList = contactsList,
                        onAddContact = {
                            saveContacts(contactsList + ContactConfig())
                        },
                        onUpdateContact = { index, updatedContact ->
                            val updatedList = contactsList.toMutableList()
                            updatedList[index] = updatedContact
                            saveContacts(updatedList)
                        },
                        onDeleteContact = { index ->
                            val updatedList = contactsList.toMutableList()
                            updatedList.removeAt(index)
                            saveContacts(updatedList)
                        },
                        onOpenSettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onOpenNotificationSettings = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                                != PackageManager.PERMISSION_GRANTED) {
                                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                    }
                                } else {
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", packageName, null)
                                    }
                                }
                                startActivity(intent)
                            }
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    contactsList: List<ContactConfig>,
    onAddContact: () -> Unit,
    onUpdateContact: (Int, ContactConfig) -> Unit,
    onDeleteContact: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "🔥 AutoStreak Settings",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }

        itemsIndexed(contactsList, key = { _, item -> item.id }) { index, contact ->
            ContactCard(
                contact = contact,
                onUpdate = { updated -> onUpdateContact(index, updated) },
                onDelete = { onDeleteContact(index) }
            )
        }

        item {
            Button(
                onClick = onAddContact,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("+ Додати контакт", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Увімкнути Accessibility Service")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenNotificationSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Дозволити Сповіщення (Toasts)")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "1. Додайте контакти та налаштуйте текст/емодзі\n2. Увімкніть Accessibility Service\n3. Натисніть віджет на робочому столі",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ContactCard(
    contact: ContactConfig,
    onUpdate: (ContactConfig) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = contact.name,
                    onValueChange = { onUpdate(contact.copy(name = it)) },
                    label = { Text("Нікнейм в TikTok") },
                    placeholder = { Text("Shpak") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                IconButton(onClick = onDelete) {
                    Text("🗑️", fontSize = 18.sp)
                }
            }

            // Перемикач Текст / Емодзі
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = contact.messageType == MessageType.TEXT,
                    onClick = { onUpdate(contact.copy(messageType = MessageType.TEXT)) },
                    label = { Text("Власний текст") }
                )

                FilterChip(
                    selected = contact.messageType == MessageType.EMOJI,
                    onClick = { onUpdate(contact.copy(messageType = MessageType.EMOJI)) },
                    label = { Text("Швидкий Емодзі") }
                )
            }

            // Вміст залежно від типу
            if (contact.messageType == MessageType.TEXT) {
                OutlinedTextField(
                    value = contact.customText,
                    onValueChange = { onUpdate(contact.copy(customText = it)) },
                    label = { Text("Текст повідомлення") },
                    placeholder = { Text("🔥 streak") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                val availableEmojis = listOf("❤️", "😂", "👍", "😴", "👈", "👋", "🌹", "🔥")
                Column {
                    Text("Оберіть смайлик TikTok:", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        availableEmojis.forEach { emoji ->
                            val isSelected = contact.emojiSymbol == emoji
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { onUpdate(contact.copy(emojiSymbol = emoji)) }
                            ) {
                                Text(emoji, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}