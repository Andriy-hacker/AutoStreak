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
            delay(3500) // Стабільна пауза на запуск TikTok (~1.4x)

            // 2. Розумна навігація у Вхідні (Inbox)
            ensureInboxScreen(contacts.first().name)
            delay(1500)

            // 3. Проходимо циклом по контактах
            for (contact in contacts) {
                showToast("👤 Обробка: ${contact.name}")
                performStreakForContact(contact)
                delay(1500) // Надійна пауза між контактами
            }
            showToast("✅ Автострік завершено!")
        }
    }

    private suspend fun ensureInboxScreen(firstContactName: String) {
        val root = rootInActiveWindow
        if (root != null) {
            // Перевіряємо, чи ми не застрягли всередині відкритого чату
            val isInsideChat = findNodeByText(root, "message") != null 
                    || findNodeByText(root, "повідомлення") != null
                    || findNodeByText(root, "сообщение") != null
                    || findNodeByText(root, "Відповісти") != null

            if (isInsideChat) {
                showToast("↩️ Виходимо з відкритого чату...")
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(1200)
            }
        }

        val displayMetrics = resources.displayMetrics
        val minListY = (displayMetrics.heightPixels * 0.28).toInt()
        val maxListY = (displayMetrics.heightPixels * 0.88).toInt()
        val currentRoot = rootInActiveWindow

        if (currentRoot != null && findNodeInYRange(currentRoot, firstContactName, minListY, maxListY) != null) {
            Log.d(TAG, "Contact $firstContactName is already visible in chat list")
            return
        }

        showToast("💬 Шукаємо вкладку Inbox...")
        clickOnText("Inbox") || clickOnText("Вхідні") || clickOnText("Входящие")
    }

    private suspend fun performStreakForContact(contact: ContactConfig) {
        val name = contact.name
        val chatOpened = openChatWithContact(name)
        
        if (chatOpened) {
            delay(1500) // Надійний час на відкриття чату

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
                // Намагаємося натиснути на панель швидких емодзі ВНИЗУ екрану
                val clickedEmojiBar = clickQuickEmojiBar(contact.emojiSymbol, inputNode)

                if (clickedEmojiBar) {
                    showToast("😊 Надіслано емодзі ${contact.emojiSymbol}")
                    delay(1200)
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(1000)
                    return
                }
            }

            // Якщо обрано текст або якщо емодзі-панель не була знайдена
            val textToSend = if (contact.messageType == MessageType.EMOJI) contact.emojiSymbol else contact.customText

            if (inputNode != null) {
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    textToSend
                )
                inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                delay(1000)

                // Натискаємо кнопку надсилання
                val sent = clickOnText("Send") || clickOnText("Надіслати") || clickOnText("Отправить")
                if (!sent) {
                    val rect = Rect()
                    inputNode.getBoundsInScreen(rect)
                    clickAt(rect.right + 50f, rect.centerY().toFloat())
                }

                delay(1000)
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(1000)
            } else {
                showToast("⚠️ Поле вводу не знайдено для $name")
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(1000)
            }
        } else {
            showToast("❌ Чат з $name не знайдено")
        }
    }

    private suspend fun openChatWithContact(name: String): Boolean {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val minListY = (screenHeight * 0.28).toInt() // Строго нижче смуги Сторіз (28%)
        val maxListY = (screenHeight * 0.88).toInt() // Строго вище нижнього меню з "+" (88%)

        val root = rootInActiveWindow ?: return false

        // 1. Пошук контакту строго в межах списку чатів
        val listNode = findNodeInYRange(root, name, minListY, maxListY)
        if (listNode != null) {
            Log.d(TAG, "Found contact $name in chat list range")
            clickNode(listNode)
            return true
        }

        // 2. Якщо контакту немає серед перших видимих — використовуємо Пошук 🔍 Вхідних
        val searchNode = findNodeByText(root, "Search")
            ?: findNodeByText(root, "Пошук")
            ?: findNodeByText(root, "Поиск")

        if (searchNode != null) {
            Log.d(TAG, "Clicking search icon in Inbox for $name")
            clickNode(searchNode)
            delay(1200)

            val searchRoot = rootInActiveWindow
            val searchInput = findNodeByText(searchRoot, "Search")
                ?: findNodeByText(searchRoot, "Пошук")
                ?: findNodeByText(searchRoot, "Поиск")

            if (searchInput != null) {
                val arguments = Bundle()
                arguments.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    name
                )
                searchInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                delay(1200)

                val resultRoot = rootInActiveWindow
                val resultNode = findNodeInYRange(resultRoot, name, minListY, maxListY)
                if (resultNode != null) {
                    clickNode(resultNode)
                    return true
                }
            }
        }

        return false
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

    private fun clickQuickEmojiBar(emojiSymbol: String, inputNode: AccessibilityNodeInfo?): Boolean {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels
        val minTopY = (screenHeight * 0.60).toInt() // Лише нижні 40% екрана!

        val root = rootInActiveWindow ?: return false

        // 1. Шукаємо емодзі СТРОГО в нижній частині екрана
        val bottomEmojiNode = findNodeInYRange(root, emojiSymbol, minTopY, screenHeight)
        if (bottomEmojiNode != null) {
            clickNode(bottomEmojiNode)
            Log.d(TAG, "Found and clicked bottom emoji node for $emojiSymbol")
            return true
        }

        // 2. Резервний варіант: розрахунок точних координат панелі над полем вводу
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

                Log.d(TAG, "Clicking quick emoji $emojiSymbol by coordinates ($targetX, $emojiBarY)")
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

        val matchesText = root.text?.toString()?.contains(text, ignoreCase = true) == true ||
                root.contentDescription?.toString()?.contains(text, ignoreCase = true) == true

        if (matchesText && rect.top >= minTopY && rect.bottom <= maxBottomY) {
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