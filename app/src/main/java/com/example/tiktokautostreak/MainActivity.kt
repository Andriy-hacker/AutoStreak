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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.tiktokautostreak.ui.theme.AutoStreakTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request Notification permission on Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            AutoStreakTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val prefs = getSharedPreferences("streak_prefs", Context.MODE_PRIVATE)
                    var savedNames by remember { 
                        mutableStateOf(prefs.getString("contact_names", "") ?: "") 
                    }

                    MainScreen(
                        currentNames = savedNames,
                        onNamesChange = { 
                            savedNames = it
                            prefs.edit().putString("contact_names", it).apply()
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
    currentNames: String, 
    onNamesChange: (String) -> Unit, 
    onOpenSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Greeting(name = "AutoStreak")
        Spacer(modifier = Modifier.height(20.dp))
        
        OutlinedTextField(
            value = currentNames,
            onValueChange = onNamesChange,
            label = { Text("Імена контактів (через кому)") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Друг1, Друг2, Мама") }
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Увімкнути Accessibility Service")
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Дозволити Сповіщення (Toasts)")
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "1. Впишіть імена\n2. Надайте дозволи (Accessibility та Сповіщення)\n3. Додайте віджет на екран", 
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Привіт, $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AutoStreakTheme {
        Greeting("AutoStreak")
    }
}