package com.example.data.scanner

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

enum class KeyboardPublisher {
    GOOGLE,
    SAMSUNG,
    MICROSOFT,
    THIRD_PARTY
}

data class EnabledKeyboardInfo(
    val id: String,
    val packageName: String,
    val displayName: String,
    val isDefault: Boolean,
    val publisher: KeyboardPublisher
) {
    val isKnownPublisher: Boolean
        get() = publisher != KeyboardPublisher.THIRD_PARTY
}

interface KeyboardInspector {
    fun getEnabledKeyboards(): List<EnabledKeyboardInfo>
}

class DeviceKeyboardInspector(
    private val context: Context
) : KeyboardInspector {

    override fun getEnabledKeyboards(): List<EnabledKeyboardInfo> {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return emptyList()
        val pm = context.packageManager

        val defaultId = try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            ).orEmpty()
        } catch (_: Exception) {
            ""
        }

        return try {
            imm.enabledInputMethodList.map { imi ->
                val pkg = imi.packageName
                val label = try {
                    imi.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: pkg
                } catch (_: Exception) {
                    pkg
                }
                val isDefault = defaultId.isNotEmpty() &&
                    (imi.id == defaultId || defaultId.startsWith("$pkg/"))

                EnabledKeyboardInfo(
                    id = imi.id,
                    packageName = pkg,
                    displayName = label,
                    isDefault = isDefault,
                    publisher = classifyKeyboardPublisher(pkg)
                )
            }.sortedWith(
                compareByDescending<EnabledKeyboardInfo> { !it.isKnownPublisher }
                    .thenByDescending { it.isDefault }
                    .thenBy { it.displayName.lowercase() }
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        fun classifyKeyboardPublisher(packageName: String): KeyboardPublisher {
            val lower = packageName.lowercase()
            return when {
                lower == "com.google.android.inputmethod.latin" ||
                    lower.startsWith("com.google.android.inputmethod.") ||
                    lower == "com.google.android.tts" ||
                    lower == "com.google.android.googlequicksearchbox" -> KeyboardPublisher.GOOGLE

                lower == "com.samsung.android.honeyboard" ||
                    lower == "com.sec.android.inputmethod" ||
                    lower.startsWith("com.samsung.android.inputmethod") -> KeyboardPublisher.SAMSUNG

                lower == "com.touchtype.swiftkey" ||
                    lower == "com.touchtype.swiftkey.beta" ||
                    lower.startsWith("com.microsoft.swiftkey") -> KeyboardPublisher.MICROSOFT

                else -> KeyboardPublisher.THIRD_PARTY
            }
        }
    }
}
