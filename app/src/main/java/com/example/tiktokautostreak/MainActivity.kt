package com.example.tiktokautostreak

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.example.tiktokautostreak.ui.theme.AutoStreakTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
        Text("1. Впишіть імена\n2. Надайте дозвіл\n3. Додайте віджет на екран", modifier = Modifier.fillMaxWidth())
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