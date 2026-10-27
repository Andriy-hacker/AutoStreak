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
            delay(5000) // Пауза на повний запуск TikTok

            // 2. Переходимо у вкладку Inbox
            ensureInboxScreen()
            delay(2500) // Пауза на завантаження списку

            // 3. Проходимо циклом по контактах
            for (contact in contacts) {
                showToast("👤 Обробка: ${contact.name}")
                performStreakForContact(contact)
                delay(2000) // Пауза між контактами
            }
            showToast("✅ Автострік завершено!")
        }
    }

    private suspend fun ensureInboxScreen() {
        val root = rootInActiveWindow
        if (root != null) {
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
            val displayMetrics = resources.displayMetrics
            clickAt(displayMetrics.widthPixels * 0.7f, displayMetrics.heightPixels * 0.95f)
        }
    }

    private suspend fun performStreakForContact(contact: ContactConfig) {
        val name = contact.name
        val chatOpened = openChatWithContact(name)
        
        if (chatOpened) {
            // Чекаємо та опитуємо появу поля вводу в чаті (до 2 секунд)
            var inputNode: AccessibilityNodeInfo? = null
            repeat(4) {
                delay(500)
                val root = rootInActiveWindow
                if (root != null) {
                    inputNode = findNodeByText(root, "message") 
                        ?: findNodeByText(root, "повідомлення")
                        ?: findNodeByText(root, "сообщение")
                        ?: findNodeByText(root, "напишіть")
                    if (inputNode != null) return@repeat
                }
            }

            if (inputNode == null) {
                showToast("⚠️ Поле вводу не з'явилося для $name")
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }

            if (contact.messageType == MessageType.EMOJI) {
                // Намагаємося натиснути на панель швидких емодзі ВНИЗУ екрану
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

            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                textToSend
            )
            inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            delay(1500)

            // Натискаємо кнопку надсилання
            val sent = clickOnText("Send") || clickOnText("Надіслати") || clickOnText("Отправить")
            if (!sent) {
                val rect = Rect()
                inputNode.getBoundsInScreen(rect)
                clickAt(rect.right + 50f, rect.centerY().toFloat())
            }

            delay(1500)
            performGlobalAction(GLOBAL_ACTION_BACK)
        } else {
            showToast("❌ Чат з $name не знайдено у списку")
        }
    }

    private fun openChatWithContact(name: String): Boolean {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels
        val minListY = (screenHeight * 0.28).toInt() // Нижче смуги Сторіз (28%)
        val maxListY = (screenHeight * 0.88).toInt() // Вище нижнього меню з "+" (88%)

        val root = rootInActiveWindow ?: return false

        // Шукаємо контакт СТРОГО в межах списку чатів
        val node = findExactContactNode(root, name, minListY, maxListY)
        if (node != null) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            
            // ЗСУВАЄМО КЛІК ВПРАВО (на 140 пікселів вправо від центру тексту), 
            // щоб гарантовано натиснути на область імені/чату, а НЕ на аватарку зліва (яка відкриває Сторіз)!
            val targetX = (rect.centerX() + 140f).coerceAtMost(screenWidth * 0.75f)
            val targetY = rect.centerY().toFloat()

            Log.d(TAG, "Clicking chat row for $name safely away from avatar at coordinates ($targetX, $targetY)")
            clickAt(targetX, targetY)
            return true
        }

        Log.d(TAG, "Contact $name NOT found in chat list range")
        return false
    }

    private fun clickQuickEmojiBar(emojiSymbol: String, inputNode: AccessibilityNodeInfo?): Boolean {
        if (inputNode != null) {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels

            val inputRect = Rect()
            inputNode.getBoundsInScreen(inputRect)

            val emojiBarY = (inputRect.top - 55).toFloat()
            val emojis = listOf("❤️", "😂", "👍", "😴", "👈", "👋", "🌹", "🔥")
            val index = emojis.indexOf(emojiSymbol)
            val targetIndex = if (index >= 0) index else 0

            val spacing = screenWidth / 8.0f
            val targetX = spacing * (targetIndex + 0.5f)

            Log.d(TAG, "Tapping quick emoji $emojiSymbol (index $targetIndex) at coordinates ($targetX, $emojiBarY)")
            clickAt(targetX, emojiBarY)
            return true
        }
        return false
    }

    private fun findExactContactNode(root: AccessibilityNodeInfo?, text: String, minTopY: Int, maxBottomY: Int): AccessibilityNodeInfo? {
        if (root == null) return null

        val rect = Rect()
        root.getBoundsInScreen(rect)

        val nodeText = root.text?.toString()?.trim() ?: ""
        val nodeDesc = root.contentDescription?.toString()?.trim() ?: ""

        // Шукаємо точний збіг імені контакту всередині діапазону списку
        if ((nodeText.equals(text, ignoreCase = true) || 
             nodeDesc.equals(text, ignoreCase = true) ||
             nodeText.startsWith("$text ", ignoreCase = true) ||
             nodeDesc.startsWith("$text ", ignoreCase = true)) && 
            rect.top >= minTopY && rect.bottom <= maxBottomY) {
            return root
        }

        for (i in 0 until root.childCount) {
            val child = root.getChild(i)
            val result = findExactContactNode(child, text, minTopY, maxBottomY)
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

    private fun clickOnText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeByText(root, text)
        if (node != null) {
            var clickableNode = node
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