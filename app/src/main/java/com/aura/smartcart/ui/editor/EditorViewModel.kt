package com.aura.smartcart.ui.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.smartcart.AppContainer
import com.aura.smartcart.data.db.ListWithItems
import com.aura.smartcart.data.db.RemainingItemEntity
import com.aura.smartcart.data.repo.DraftItem
import com.aura.smartcart.service.notify.ReminderScheduler
import com.aura.smartcart.service.scan.ScanOutcome
import com.aura.smartcart.ui.common.resolveInputLanguage
import com.aura.smartcart.util.InputLanguage
import com.aura.smartcart.util.ItemTextParser
import com.aura.smartcart.util.ParsedItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

enum class ScanProblem { UNREADABLE, NEEDS_INTERNET }

data class EditorState(
    val loading: Boolean = true,
    val listId: Long? = null,
    val name: String = "",
    val shoppingAt: Long? = null,
    val items: List<DraftItem> = emptyList(),
    val usedRemainingIds: List<Long> = emptyList(),
    val scanning: Boolean = false,
    val scanned: List<ParsedItem>? = null,
    val scanProblem: ScanProblem? = null,
    val saving: Boolean = false
)

class EditorViewModel(
    private val container: AppContainer,
    private val listId: Long?,
    private val copyFrom: Long?
) : ViewModel() {

    private val keys = AtomicLong(1)
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    val inputLanguage: StateFlow<InputLanguage> = container.prefs.inputLanguage
        .map { resolveInputLanguage(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, resolveInputLanguage(null))

    /** Not-found items from earlier trips, minus the ones already pulled into this list. */
    val remaining: StateFlow<List<RemainingItemEntity>> = container.lists.observeRemaining()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _oldLists = MutableStateFlow<List<ListWithItems>>(emptyList())
    val oldLists: StateFlow<List<ListWithItems>> = _oldLists.asStateFlow()

    private var lastScanUri: Uri? = null

    init {
        viewModelScope.launch {
            when {
                listId != null -> container.lists.getList(listId)?.let { lw ->
                    _state.update {
                        it.copy(
                            loading = false,
                            listId = lw.list.id,
                            name = lw.list.name,
                            shoppingAt = lw.list.shoppingAt,
                            items = lw.items.sortedBy { i -> i.position }.map { i ->
                                DraftItem(keys.getAndIncrement(), i.name, i.quantity, i.checked, i.checkedAt)
                            }
                        )
                    }
                } ?: _state.update { it.copy(loading = false) }

                copyFrom != null -> container.lists.getList(copyFrom)?.let { lw ->
                    _state.update {
                        it.copy(
                            loading = false,
                            name = lw.list.name,
                            items = lw.items.sortedBy { i -> i.position }.map { i -> DraftItem(keys.getAndIncrement(), i.name, i.quantity) }
                        )
                    }
                } ?: _state.update { it.copy(loading = false) }

                else -> _state.update { it.copy(loading = false) }
            }
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name) }
    fun setShoppingAt(millis: Long?) = _state.update { it.copy(shoppingAt = millis) }

    fun setInputLanguage(language: InputLanguage) {
        viewModelScope.launch { container.prefs.setInputLanguage(language) }
    }

    /** Typed input: "2 kg rice", "aadha pao dahi" or "milk, eggs" all work. */
    fun addTyped(text: String, quantity: String) {
        val parsed = if (quantity.isNotBlank()) {
            val name = ItemTextParser.cleanName(text)
            if (name.isBlank()) emptyList() else listOf(ParsedItem(name, quantity.trim()))
        } else {
            ItemTextParser.parse(text)
        }
        addParsed(parsed)
    }

    /**
     * Adds items, skipping names already on the list (a repeated name with a new quantity
     * updates that quantity instead). Returns the items that were actually added or updated.
     */
    fun addParsed(items: List<ParsedItem>): List<ParsedItem> {
        if (items.isEmpty()) return emptyList()
        val changed = mutableListOf<ParsedItem>()
        _state.update { s ->
            val list = s.items.toMutableList()
            for (item in items) {
                val idx = list.indexOfFirst { it.name.equals(item.name, ignoreCase = true) }
                when {
                    idx < 0 -> {
                        list += DraftItem(keys.getAndIncrement(), item.name, item.quantity)
                        changed += item
                    }
                    item.quantity.isNotBlank() && list[idx].quantity != item.quantity -> {
                        list[idx] = list[idx].copy(quantity = item.quantity)
                        changed += item
                    }
                }
            }
            s.copy(items = list)
        }
        return changed
    }

    /** Undo for a voice-added item (the ✕ on its chip). Removes the newest matching item. */
    fun removeParsed(item: ParsedItem) = _state.update { s ->
        val idx = s.items.indexOfLast { it.name.equals(item.name, ignoreCase = true) }
        if (idx < 0) s else s.copy(items = s.items.filterIndexed { i, _ -> i != idx })
    }

    fun updateItem(key: Long, name: String, quantity: String) = _state.update { s ->
        s.copy(items = s.items.map { if (it.key == key) it.copy(name = name, quantity = quantity) else it })
    }

    fun removeItem(key: Long) = _state.update { s -> s.copy(items = s.items.filterNot { it.key == key }) }

    fun addRemaining(ids: Set<Long>) {
        val picked = remaining.value.filter { it.id in ids }
        addParsed(picked.map { ParsedItem(it.name, it.quantity) })
        _state.update { it.copy(usedRemainingIds = (it.usedRemainingIds + picked.map { p -> p.id }).distinct()) }
    }

    fun loadOldLists() {
        viewModelScope.launch {
            _oldLists.value = container.lists.getAllLists().filter { it.list.id != listId && it.items.isNotEmpty() }
        }
    }

    fun addFromOldList(id: Long, itemIndexes: Set<Long>? = null) {
        val list = _oldLists.value.firstOrNull { it.list.id == id } ?: return
        val items = list.items.filter { itemIndexes == null || it.id in itemIndexes }
        addParsed(items.map { ParsedItem(it.name, it.quantity) })
        if (_state.value.name.isBlank()) setName(list.list.name)
    }

    fun scanList(uri: Uri) {
        lastScanUri = uri
        runScan(uri, inputLanguage.value)
    }

    /** Re-reads the last scanned page in another language. */
    fun rescanIn(language: InputLanguage) {
        setInputLanguage(language)
        lastScanUri?.let { runScan(it, language) }
    }

    private fun runScan(uri: Uri, language: InputLanguage) {
        _state.update { it.copy(scanning = true, scanned = null, scanProblem = null) }
        viewModelScope.launch {
            when (val result = container.scanner.readList(uri, language)) {
                is ScanOutcome.Success -> _state.update {
                    it.copy(scanning = false, scanned = ItemTextParser.parseLines(result.value))
                }
                ScanOutcome.NeedsInternet -> _state.update { it.copy(scanning = false, scanned = emptyList(), scanProblem = ScanProblem.NEEDS_INTERNET) }
                ScanOutcome.Unreadable -> _state.update { it.copy(scanning = false, scanned = emptyList(), scanProblem = ScanProblem.UNREADABLE) }
            }
        }
    }

    fun clearScan() = _state.update { it.copy(scanned = null, scanProblem = null) }

    fun save(defaultName: String, onSaved: (Long) -> Unit) {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = container.lists.saveList(
                id = s.listId,
                name = s.name.ifBlank { defaultName },
                shoppingAt = s.shoppingAt,
                items = s.items.filter { it.name.isNotBlank() },
                usedRemainingIds = s.usedRemainingIds
            )
            ReminderScheduler.scheduleTrip(container.appContext, id, s.shoppingAt)
            _state.update { it.copy(saving = false, listId = id) }
            onSaved(id)
        }
    }
}
