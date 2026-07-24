package com.tonnet.browser.ui

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.tonnet.browser.R

data class BrowserSheetItem(
    val key: String,
    val title: String,
    val supportingText: String?,
    val active: Boolean = false,
)

interface BrowserSheetHost {
    fun tabSheetItems(): List<BrowserSheetItem>
    fun bookmarkSheetItems(): List<BrowserSheetItem>
    fun languageSheetItems(): List<BrowserSheetItem>
    fun onTabSelected(key: String)
    fun onBookmarkSelected(key: String)
    fun onLanguageSelected(key: String)
    fun onNewTabRequested()
    fun onTabCloseRequested(key: String): Boolean
    fun onBookmarkRemoveRequested(key: String, onComplete: (Boolean) -> Unit)
    fun onBrowserSheetDismissed()
}

class TabsBottomSheet : BrowserListBottomSheet() {
    override val titleRes: Int = R.string.tabs
    override val emptyRes: Int = R.string.tabs
    override val showTabActions: Boolean = true
    override val layoutRes: Int = R.layout.bottom_sheet_tabs
    override val rowLayoutRes: Int = R.layout.telegram_tab_list_item
    override val rowHeightDp: Int = 68
    override val fixedHeightDp: Int = 140
    override val removalContentDescriptionRes: Int = R.string.close_named_tab

    override fun items(host: BrowserSheetHost): List<BrowserSheetItem> = host.tabSheetItems()

    override fun onItemSelected(host: BrowserSheetHost, key: String) {
        host.onTabSelected(key)
    }

    override fun onItemRemoved(
        host: BrowserSheetHost,
        key: String,
        onComplete: (Boolean) -> Unit,
    ) {
        onComplete(host.onTabCloseRequested(key))
    }
}

class BookmarksBottomSheet : BrowserListBottomSheet() {
    override val titleRes: Int = R.string.bookmarks
    override val emptyRes: Int = R.string.no_bookmarks
    override val showTabActions: Boolean = false
    override val removalContentDescriptionRes: Int = R.string.remove_named_bookmark

    override fun items(host: BrowserSheetHost): List<BrowserSheetItem> = host.bookmarkSheetItems()

    override fun onItemSelected(host: BrowserSheetHost, key: String) {
        host.onBookmarkSelected(key)
    }

    override fun onItemRemoved(
        host: BrowserSheetHost,
        key: String,
        onComplete: (Boolean) -> Unit,
    ) {
        host.onBookmarkRemoveRequested(key, onComplete)
    }
}

class LanguageBottomSheet : BrowserListBottomSheet() {
    override val titleRes: Int = R.string.language
    override val emptyRes: Int = R.string.language
    override val showTabActions: Boolean = false
    override val rowLayoutRes: Int = R.layout.telegram_language_list_item
    override val highlightActiveTitle: Boolean = false

    override fun items(host: BrowserSheetHost): List<BrowserSheetItem> = host.languageSheetItems()

    override fun onItemSelected(host: BrowserSheetHost, key: String) {
        host.onLanguageSelected(key)
    }
}

abstract class BrowserListBottomSheet : BottomSheetDialogFragment() {
    protected abstract val titleRes: Int
    protected abstract val emptyRes: Int
    protected abstract val showTabActions: Boolean
    protected open val layoutRes: Int = R.layout.bottom_sheet_browser_list
    protected open val rowLayoutRes: Int = R.layout.telegram_list_item
    protected open val rowHeightDp: Int = ROW_HEIGHT_DP
    protected open val highlightActiveTitle: Boolean = true
    protected open val removalContentDescriptionRes: Int? = null
    protected open val fixedHeightDp: Int
        get() = if (showTabActions) FIXED_HEIGHT_WITH_ACTIONS_DP else FIXED_HEIGHT_DP
    protected abstract fun items(host: BrowserSheetHost): List<BrowserSheetItem>
    protected abstract fun onItemSelected(host: BrowserSheetHost, key: String)
    protected open fun onItemRemoved(
        host: BrowserSheetHost,
        key: String,
        onComplete: (Boolean) -> Unit,
    ) {
        onComplete(false)
    }

    override fun getTheme(): Int = R.style.TonnetBottomSheetDialogTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(layoutRes, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = activity as? BrowserSheetHost ?: return
        val availableHeight = (resources.displayMetrics.heightPixels * MAX_HEIGHT_RATIO).toInt()
        val fixedHeight = dp(fixedHeightDp)
        val empty = view.findViewById<TextView>(R.id.sheet_empty)
        val list = view.findViewById<RecyclerView>(R.id.sheet_list)
        view.findViewById<TextView>(R.id.sheet_title).setText(titleRes)
        empty.apply {
            setText(emptyRes)
        }
        val adapter = BrowserSheetAdapter(
            initialItems = items(host),
            rowLayoutRes = rowLayoutRes,
            highlightActiveTitle = highlightActiveTitle,
            removalContentDescriptionRes = removalContentDescriptionRes,
            onClick = { key ->
                onItemSelected(host, key)
                dismiss()
            },
            onRemove = removalContentDescriptionRes?.let {
                { key ->
                    onItemRemoved(host, key) { removed ->
                        if (removed && isAdded) {
                            val updated = items(host)
                            (list.adapter as? BrowserSheetAdapter)?.replaceItems(updated)
                            empty.isVisible = updated.isEmpty()
                            list.isVisible = updated.isNotEmpty()
                            list.updateLayoutParams<LinearLayout.LayoutParams> {
                                height = BrowserSheetSizing.listHeight(
                                    itemCount = updated.size,
                                    rowHeight = dp(rowHeightDp),
                                    availableHeight = availableHeight,
                                    fixedHeight = fixedHeight,
                                )
                            }
                        }
                    }
                }
            },
        )
        list.apply {
            val entries = adapter.itemsSnapshot()
            isVisible = entries.isNotEmpty()
            empty.isVisible = entries.isEmpty()
            updateLayoutParams<LinearLayout.LayoutParams> {
                height = BrowserSheetSizing.listHeight(
                    itemCount = entries.size,
                    rowHeight = dp(rowHeightDp),
                    availableHeight = availableHeight,
                    fixedHeight = fixedHeight,
                )
            }
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
        }
        view.findViewById<LinearLayout>(R.id.sheet_actions).isVisible = showTabActions
        view.findViewById<View>(R.id.new_tab_action).setOnClickListener {
            host.onNewTabRequested()
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = (dialog as? BottomSheetDialog)
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        BottomSheetBehavior.from(bottomSheet).apply {
            maxHeight = (resources.displayMetrics.heightPixels * MAX_HEIGHT_RATIO).toInt()
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? BrowserSheetHost)?.onBrowserSheetDismissed()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MAX_HEIGHT_RATIO = 0.85f
        const val ROW_HEIGHT_DP = 60
        const val FIXED_HEIGHT_DP = 76
        const val FIXED_HEIGHT_WITH_ACTIONS_DP = 132
    }
}

private class BrowserSheetAdapter(
    initialItems: List<BrowserSheetItem>,
    private val rowLayoutRes: Int,
    private val highlightActiveTitle: Boolean,
    private val removalContentDescriptionRes: Int?,
    private val onClick: (String) -> Unit,
    private val onRemove: ((String) -> Unit)?,
) : RecyclerView.Adapter<BrowserSheetAdapter.Holder>() {
    private val items = initialItems.toMutableList()

    fun itemsSnapshot(): List<BrowserSheetItem> = items.toList()

    fun replaceItems(updated: List<BrowserSheetItem>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = items.size
            override fun getNewListSize(): Int = updated.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                items[oldItemPosition].key == updated[newItemPosition].key

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                items[oldItemPosition] == updated[newItemPosition]
        })
        items.clear()
        items.addAll(updated)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(rowLayoutRes, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(
            item = items[position],
            highlightActiveTitle = highlightActiveTitle,
            removalContentDescriptionRes = removalContentDescriptionRes,
            onClick = onClick,
            onRemove = onRemove,
        )
    }

    override fun getItemCount(): Int = items.size

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.list_item_title)
        private val supporting: TextView = view.findViewById(R.id.list_item_supporting)
        private val remove: View? = view.findViewById(R.id.list_item_remove)
        private val selection: RadioButton? = view.findViewById(R.id.list_item_selection)

        fun bind(
            item: BrowserSheetItem,
            highlightActiveTitle: Boolean,
            removalContentDescriptionRes: Int?,
            onClick: (String) -> Unit,
            onRemove: ((String) -> Unit)?,
        ) {
            itemView.isSelected = item.active
            title.text = item.title
            title.setTextColor(
                ContextCompat.getColor(
                    itemView.context,
                    if (item.active && highlightActiveTitle) {
                        R.color.tonnet_accent
                    } else {
                        R.color.tonnet_text
                    },
                ),
            )
            supporting.text = item.supportingText.orEmpty()
            supporting.isVisible = !item.supportingText.isNullOrBlank()
            remove?.isVisible = onRemove != null
            if (removalContentDescriptionRes != null) {
                remove?.contentDescription = itemView.context.getString(
                    removalContentDescriptionRes,
                    item.title,
                )
            }
            remove?.setOnClickListener { onRemove?.invoke(item.key) }
            selection?.isChecked = item.active
            if (selection != null) {
                itemView.contentDescription = itemView.context.getString(
                    if (item.active) {
                        R.string.language_option_selected
                    } else {
                        R.string.language_option_not_selected
                    },
                    item.title,
                )
            }
            itemView.setOnClickListener { onClick(item.key) }
        }
    }
}
