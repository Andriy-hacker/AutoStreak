package com.example.tiktokautostreak

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.*

class AutomationService : AccessibilityService() {

    private val TAG = "AutomationService"
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service Connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }

    fun startAutomation() {
        serviceScope.launch {
            val prefs = getSharedPreferences("streak_prefs", Context.MODE_PRIVATE)
            val savedJson = prefs.getString("contacts_json", null)
            val contacts = if (savedJson != null) {
                ContactConfig.fromJsonList(savedJson)
            } else {
                val legacyNames = prefs.getString("contact_names", "") ?: ""
                ContactConfig.fromJsonList(legacyNames)
            }.filter { it.name.isNotEmpty() }

            if (contacts.isEmpty()) {
                showToast("⚠️ Список контактів порожній! Введіть імена в додаток.")
                return@launch
            }

            // 1. Знаходимо та відкриваємо TikTok
            val tikTokPackages = listOf(
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                "com.zhiliaoapp.musically.go",
                "com.ss.android.ugc.aweme"
            )
            var launchIntent: Intent? = null

            for (pkg in tikTokPackages) {
                launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) break
            }

            if (launchIntent == null) {
                showToast("❌ TikTok не знайдено на пристрої!")
                return@launch
            }

            showToast("📱 Запуск TikTok...")
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            delay(5000) // Надійна пауза на запуск (ver 1.0)

            // 2. Повернення у Вхідні (Inbox) з перевіркою активного чату
            ensureInboxScreen()
            delay(2500) // Пауза на завантаження списку (ver 1.0)

            // 3. Проходимо циклом по контактах
            for (contact in contacts) {
                showToast("👤 Обробка: ${contact.name}")
                performStreakForContact(contact)
                delay(2000) // Пауза між контактами (ver 1.0)
            }
            showToast("✅ Автострік завершено!")
        }
    }

    private suspend fun ensureInboxScreen() {
        val root = rootInActiveWindow
        if (root != null) {
            // Перевіряємо, чи ми застрягли всередині відкритого чату
            val isInsideChat = findNodeByText(root, "message") != null 
                    || findNodeByText(root, "повідомлення") != null
                    || findNodeByText(root, "сообщение") != null
                    || findNodeByText(root, "Відповісти") != null

            if (isInsideChat) {
                showToast("↩️ Виходимо з відкритого чату...")
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(1500)
            }
        }

        showToast("💬 Шукаємо вкладку Inbox...")
        val openedInbox = clickOnText("Inbox") || clickOnText("Вхідні") || clickOnText("Входящие")
        if (!openedInbox) {
            // Резервний клік на нижню панель у правому кутку (де зазвичай Inbox)
            val displayMetrics = resources.displayMetrics
            clickAt(displayMetrics.widthPixels * 0.7f, displayMetrics.heightPixels * 0.95f)
        }
    }

    private suspend fun performStreakForContact(contact: ContactConfig) {
        val name = contact.name
        if (clickOnText(name)) {
            delay(2000) // Пауза на відкриття чату (ver 1.0)
            
            val root = rootInActiveWindow
            if (root == null) {
                showToast("⚠️ Не вдалося зчитати екран для $name")
                return
            }
            
            val inputNode = findNodeByText(root, "message") 
                ?: findNodeByText(root, "повідомлення")
                ?: findNodeByText(root, "сообщение")
                ?: findNodeByText(root, "напишіть")

            if (contact.messageType == MessageType.EMOJI) {
                // Натискаємо на точні координати емодзі на панелі TikTok над полем вводу
                val clickedEmojiBar = clickQuickEmojiBar(contact.emojiSymbol, inputNode)

                if (clickedEmojiBar) {
                    showToast("😊 Надіслано емодзі ${contact.emojiSymbol}")
                    delay(1500)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    return
                }
            }

            // Якщо обрано текст або якщо емодзі-панель не спрацювала
            val textToSend = if (contact.messageType == MessageType.EMOJI) contact.emojiSymbol else contact.customText

            if (inputNode != null) {
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    textToSend
                )
                inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                delay(1500) // Пауза (ver 1.0)
                
                // Натискаємо кнопку надсилання
                val sent = clickOnText("Send") || clickOnText("Надіслати") || clickOnText("Отправить")
                if (!sent) {
                    val rect = Rect()
                    inputNode.getBoundsInScreen(rect)
                    clickAt(rect.right + 50f, rect.centerY().toFloat())
                }
                
                delay(1500) // Пауза (ver 1.0)
                performGlobalAction(GLOBAL_ACTION_BACK)
            } else {
                showToast("⚠️ Поле вводу не знайдено для $name")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } else {
            showToast("❌ Чат з $name не знайдено у списку")
        }
    }

    private fun clickQuickEmojiBar(emojiSymbol: String, inputNode: AccessibilityNodeInfo?): Boolean {
        if (inputNode != null) {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels

            val inputRect = Rect()
            inputNode.getBoundsInScreen(inputRect)

            // Панель швидких емодзі в TikTok завжди розташована приблизно на 55 пікселів вище поля вводу
            val emojiBarY = (inputRect.top - 55).toFloat()

            // Порядок емодзі на панелі TikTok зліва направо:
            val emojis = listOf("❤️", "😂", "👍", "😴", "👈", "👋", "🌹", "🔥")
            val index = emojis.indexOf(emojiSymbol)
            val targetIndex = if (index >= 0) index else 0

            // Розрахунок X-координати по ширині екрана (від 8% до 92%)
            val spacing = screenWidth / 8.0f
            val targetX = spacing * (targetIndex + 0.5f)

            Log.d(TAG, "Tapping quick emoji $emojiSymbol (index $targetIndex) at coordinates ($targetX, $emojiBarY)")
            clickAt(targetX, emojiBarY)
            return true
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun findNodeByText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null) return null
        
        val nodeText = root.text?.toString()?.trim() ?: ""
        val nodeDesc = root.contentDescription?.toString()?.trim() ?: ""

        // Шукаємо точний збіг або початок рядка з ім'ям контакту
        if (nodeText.equals(text, ignoreCase = true) || 
            nodeDesc.equals(text, ignoreCase = true) ||
            nodeText.startsWith("$text ", ignoreCase = true) ||
            nodeDesc.startsWith("$text ", ignoreCase = true) ||
            nodeText.contains(text, ignoreCase = true)) {
            return root
        }
        
        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            val result = findNodeByText(child, text)
            if (result != null) return result
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo) {
        var clickableNode: AccessibilityNodeInfo? = node
        while (clickableNode != null && !clickableNode.isClickable) {
            clickableNode = clickableNode.parent
        }
        
        if (clickableNode != null && clickableNode.isClickable) {
            clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            clickAt(rect.centerX().toFloat(), rect.centerY().toFloat())
        }
    }

    private fun clickOnText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeByText(root, text)
        if (node != null) {
            clickNode(node)
            return true
        }
        return false
    }

    private fun clickAt(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun showToast(message: String) {
        Log.d(TAG, "Toast: $message")
        try {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show toast", e)
        }
    }

    companion object {
        var instance: AutomationService? = null
    }
}