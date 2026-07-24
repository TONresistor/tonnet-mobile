package com.tonnet.browser.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import com.tonnet.browser.R
import kotlin.math.min

object TelegramDialogs {
    fun showConfirmation(
        context: Context,
        title: String,
        message: String,
        primaryLabel: String,
        secondaryLabel: String,
        onConfirmed: () -> Unit,
        onDismissed: () -> Unit,
    ): Dialog {
        val parent = FrameLayout(context)
        val content = LayoutInflater.from(context).inflate(R.layout.telegram_dialog, parent, false)
        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(content)
            setCancelable(true)
        }
        content.findViewById<TextView>(R.id.dialog_title).text = title
        content.findViewById<TextView>(R.id.dialog_message).text = message
        content.findViewById<TextView>(R.id.dialog_secondary).apply {
            text = secondaryLabel
            setOnClickListener { dialog.dismiss() }
        }
        content.findViewById<TextView>(R.id.dialog_primary).apply {
            text = primaryLabel
            setOnClickListener {
                dialog.dismiss()
                onConfirmed()
            }
        }
        dialog.setOnShowListener { configureWindow(context, dialog) }
        dialog.setOnDismissListener { onDismissed() }
        dialog.show()
        return dialog
    }

    fun showTextInput(
        context: Context,
        title: String,
        message: String,
        hint: String,
        initialValue: String,
        primaryLabel: String,
        secondaryLabel: String,
        onConfirmed: (String) -> Boolean,
        onDismissed: () -> Unit,
    ): Dialog {
        val parent = FrameLayout(context)
        val content = LayoutInflater.from(context).inflate(R.layout.telegram_text_input_dialog, parent, false)
        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(content)
            setCancelable(true)
        }
        val input = content.findViewById<EditText>(R.id.dialog_input).apply {
            this.hint = hint
            setText(initialValue)
            setSelection(text.length)
        }
        content.findViewById<TextView>(R.id.dialog_title).text = title
        content.findViewById<TextView>(R.id.dialog_message).text = message
        content.findViewById<TextView>(R.id.dialog_secondary).apply {
            text = secondaryLabel
            setOnClickListener { dialog.dismiss() }
        }
        content.findViewById<TextView>(R.id.dialog_primary).apply {
            text = primaryLabel
            setOnClickListener {
                if (onConfirmed(input.text.toString())) dialog.dismiss()
            }
        }
        dialog.setOnShowListener {
            configureWindow(context, dialog)
            input.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.setOnDismissListener { onDismissed() }
        dialog.show()
        return dialog
    }

    fun showMessage(
        context: Context,
        title: String,
        message: String,
        buttonLabel: String,
        onDismissed: () -> Unit,
    ): Dialog {
        val parent = FrameLayout(context)
        val content = LayoutInflater.from(context).inflate(R.layout.telegram_dialog, parent, false)
        val dialog = Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(content)
            setCancelable(true)
        }
        content.findViewById<TextView>(R.id.dialog_title).text = title
        content.findViewById<TextView>(R.id.dialog_message).text = message
        content.findViewById<TextView>(R.id.dialog_secondary).visibility = View.GONE
        content.findViewById<TextView>(R.id.dialog_primary).apply {
            text = buttonLabel
            setOnClickListener { dialog.dismiss() }
        }
        dialog.setOnShowListener { configureWindow(context, dialog) }
        dialog.setOnDismissListener { onDismissed() }
        dialog.show()
        return dialog
    }

    private fun configureWindow(context: Context, dialog: Dialog) {
        dialog.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.6f }
            val availableWidth = context.resources.displayMetrics.widthPixels - dp(context, 56)
            setLayout(min(dp(context, 356), availableWidth), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
