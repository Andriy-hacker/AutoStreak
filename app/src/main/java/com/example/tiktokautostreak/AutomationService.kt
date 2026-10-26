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
            delay(5000) // Повна оригінальна пауза ver 1.0 (5 сек)

            // 2. Переходимо у вкладку Inbox (як у ver 1.0)
            showToast("💬 Шукаємо вкладку Inbox...")
            val openedInbox = clickOnText("Inbox") || clickOnText("Вхідні") || clickOnText("Входящие")
            if (openedInbox) {
                delay(2500) // Оригінальна пауза на завантаження списку
                
                // 3. Проходимо циклом по контактах
                for (contact in contacts) {
                    showToast("👤 Обробка: ${contact.name}")
                    performStreakForContact(contact)
                    delay(2000) // Оригінальна пауза між контактами (2 сек)
                }
                showToast("✅ Автострік завершено!")
            } else {
                showToast("⚠️ Не вдалося відкрити вкладку Inbox/Вхідні")
            }
        }
    }

    private suspend fun performStreakForContact(contact: ContactConfig) {
        val name = contact.name
        if (clickOnText(name)) {
            delay(2000) // Оригінальна пауза на відкриття чату (2 сек)
            
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
                val clickedEmojiBar = clickQuickEmojiBar(contact.emojiSymbol, inputNode)
                if (clickedEmojiBar) {
                    showToast("😊 Надіслано емодзі ${contact.emojiSymbol}")
                    delay(1500)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    return
                }
            }

            val textToSend = if (contact.messageType == MessageType.EMOJI) contact.emojiSymbol else contact.customText

            if (inputNode != null) {
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    textToSend
                )
                inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                delay(1500) // Оригінальна пауза (1.5 сек)
                
                // Натискаємо кнопку надсилання
                val sent = clickOnText("Send") || clickOnText("Надіслати") || clickOnText("Отправить")
                if (!sent) {
                    val rect = Rect()
                    inputNode.getBoundsInScreen(rect)
                    clickAt(rect.right + 50f, rect.centerY().toFloat())
                }
                
                delay(1500) // Оригінальна пауза (1.5 сек)
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
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels
        val minTopY = (screenHeight * 0.60).toInt()

        val root = rootInActiveWindow ?: return false
        val bottomEmojiNode = findNodeInYRange(root, emojiSymbol, minTopY, screenHeight)
        if (bottomEmojiNode != null) {
            clickNode(bottomEmojiNode)
            return true
        }

        if (inputNode != null) {
            val inputRect = Rect()
            inputNode.getBoundsInScreen(inputRect)
            val emojiBarY = (inputRect.top - 60).toFloat()
            val emojis = listOf("❤️", "😂", "👍", "😴", "👈", "👋", "🌹", "🔥")
            val index = emojis.indexOf(emojiSymbol)
            if (index >= 0 && emojiBarY > 0) {
                val totalEmojis = 7.0f
                val startX = 70f
                val endX = screenWidth - 70f
                val step = (endX - startX) / totalEmojis
                val targetX = startX + (index * step)
                clickAt(targetX, emojiBarY)
                return true
            }
        }
        return false
    }

    private fun findNodeInYRange(root: AccessibilityNodeInfo?, text: String, minTopY: Int, maxBottomY: Int): AccessibilityNodeInfo? {
        if (root == null) return null
        val rect = Rect()
        root.getBoundsInScreen(rect)
        val nodeText = root.text?.toString()?.trim() ?: ""
        val nodeDesc = root.contentDescription?.toString()?.trim() ?: ""

        if ((nodeText.contains(text, ignoreCase = true) || nodeDesc.contains(text, ignoreCase = true)) 
            && rect.top >= minTopY && rect.bottom <= maxBottomY) {
            return root
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            val result = findNodeInYRange(child, text, minTopY, maxBottomY)
            if (result != null) return result
        }
        return null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun findNodeByText(root: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.text?.toString()?.contains(text, ignoreCase = true) == true) return root
        if (root.contentDescription?.toString()?.contains(text, ignoreCase = true) == true) return root
        
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