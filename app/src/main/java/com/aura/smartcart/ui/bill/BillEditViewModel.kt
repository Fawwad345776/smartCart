package com.aura.smartcart.ui.bill

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.smartcart.AppContainer
import com.aura.smartcart.data.repo.BillDraft
import com.aura.smartcart.data.repo.BillDraftItem
import com.aura.smartcart.service.scan.BillScan
import com.aura.smartcart.service.scan.BillScanItem
import com.aura.smartcart.service.scan.ScanOutcome
import com.aura.smartcart.ui.common.resolveInputLanguage
import com.aura.smartcart.ui.editor.ScanProblem
import com.aura.smartcart.util.BillVoiceItem
import com.aura.smartcart.util.Country
import com.aura.smartcart.util.InputLanguage
import com.aura.smartcart.util.Money
import com.aura.smartcart.util.ReceiptParser
import com.aura.smartcart.util.VideoBillCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.IdentityHashMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class BillEditState(
    val loading: Boolean = true,
    val reading: Boolean = false,
    val locating: Boolean = false,
    val saving: Boolean = false,
    val draft: BillDraft = BillDraft(),
    val totalText: String = "",
    /** True once the user typed the total themselves; otherwise it follows the items. */
    val totalManual: Boolean = false,
    val scanProblem: ScanProblem? = null,
    /** Row whose name box should take the keyboard (just added with "Write"). */
    val focusKey: Long? = null
) {
    val itemsSum: Double get() = draft.items.sumOf { Money.parse(it.price) ?: 0.0 }
}

class BillEditViewModel(
    private val container: AppContainer,
    private val billId: Long?,
    private val listId: Long?,
    private val imagePath: String?
) : ViewModel() {

    private val keys = AtomicLong(1)
    private val _state = MutableStateFlow(BillEditState())
    val state: StateFlow<BillEditState> = _state.asStateFlow()

    val inputLanguage: StateFlow<InputLanguage> = container.prefs.inputLanguage
        .map { resolveInputLanguage(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, resolveInputLanguage(null))

    /** How to undo each voice-added line (the ✕ on its chip): the row it made or what it changed. */
    private class VoiceUndo(val key: Long, val previous: BillDraftItem?)
    private val voiceUndo = IdentityHashMap<BillVoiceItem, VoiceUndo>()

    init {
        viewModelScope.launch {
            if (billId != null) {
                val saved = container.bills.getBill(billId)
                if (saved != null) {
                    val b = saved.bill
                    _state.update {
                        it.copy(
                            loading = false,
                            draft = BillDraft(
                                id = b.id, remoteId = b.remoteId, listId = b.listId, listName = b.listName,
                                storeName = b.storeName, address = b.address, lat = b.lat, lng = b.lng,
                                total = b.total, currency = b.currency, imagePath = b.imagePath,
                                purchasedAt = b.purchasedAt,
                                items = saved.items.map { i ->
                                    BillDraftItem(keys.getAndIncrement(), i.name, i.quantity, plain(i.price))
                                }
                            ),
                            totalText = plain(b.total),
                            totalManual = kotlin.math.abs(b.total - saved.items.sumOf { i -> i.price }) > 0.009
                        )
                    }
                    return@launch
                }
            }
            val country = container.prefs.countryOnce() ?: Country.detect(container.appContext)
            val list = listId?.let { container.lists.getList(it) }
            // Start from what was ticked on the list; the user adds prices by writing, voice or scan.
            val listItems = list?.items.orEmpty().filter { it.checked }.sortedBy { it.position }.map {
                BillDraftItem(keys.getAndIncrement(), it.name, it.quantity, "")
            }
            _state.update {
                it.copy(
                    loading = false,
                    draft = BillDraft(
                        listId = listId,
                        listName = list?.list?.name.orEmpty(),
                        currency = Country.currencyCode(country),
                        imagePath = imagePath,
                        items = listItems
                    )
                )
            }
            if (imagePath != null) read(inputLanguage.value)
        }
    }

    // ---------- items ----------

    private fun updateItems(focus: Long? = _state.value.focusKey, change: (List<BillDraftItem>) -> List<BillDraftItem>) =
        _state.update { s ->
            val items = change(s.draft.items)
            val sum = items.sumOf { Money.parse(it.price) ?: 0.0 }
            s.copy(
                draft = s.draft.copy(items = items),
                totalText = if (s.totalManual) s.totalText else plain(sum),
                focusKey = focus
            )
        }

    fun updateItem(key: Long, name: String, qty: String, price: String) = updateItems { items ->
        items.map { if (it.key == key) it.copy(name = name, quantity = qty, price = price) else it }
    }

    /** "Write": a new empty row with the keyboard on its name. */
    fun addItem() {
        val key = keys.getAndIncrement()
        updateItems(focus = key) { it + BillDraftItem(key, "", "", "") }
    }

    fun focusHandled() = _state.update { it.copy(focusKey = null) }

    fun removeItem(key: Long) = updateItems { items -> items.filterNot { it.key == key } }

    // ---------- voice ----------

    /**
     * Puts spoken lines on the bill. A line whose name matches a row already there (e.g. from the
     * shopping list) fills that row's quantity and price; others become new rows.
     */
    fun addVoice(heard: List<BillVoiceItem>): List<BillVoiceItem> {
        heard.forEach { v ->
            val rows = _state.value.draft.items
            val match = rows.firstOrNull { it.name.equals(v.name, ignoreCase = true) }
                ?: rows.firstOrNull { it.price.isBlank() && sameItem(it.name, v.name) }
            if (match != null) {
                voiceUndo[v] = VoiceUndo(match.key, match)
                updateItems { items ->
                    items.map {
                        if (it.key == match.key) it.copy(
                            quantity = v.quantity.ifBlank { it.quantity },
                            price = v.price.ifBlank { it.price }
                        ) else it
                    }
                }
            } else {
                val key = keys.getAndIncrement()
                voiceUndo[v] = VoiceUndo(key, null)
                updateItems { it + BillDraftItem(key, v.name, v.quantity, v.price) }
            }
        }
        return heard
    }

    /** ✕ on a voice chip: removes the row it added, or puts back the row it filled in. */
    fun removeVoice(v: BillVoiceItem) {
        val undo = voiceUndo.remove(v) ?: return
        updateItems { items ->
            if (undo.previous == null) items.filterNot { it.key == undo.key }
            else items.map { if (it.key == undo.key) undo.previous else it }
        }
    }

    private fun sameItem(a: String, b: String): Boolean {
        val x = a.lowercase(Locale.getDefault()).trim()
        val y = b.lowercase(Locale.getDefault()).trim()
        return x.isNotEmpty() && y.isNotEmpty() && (x.contains(y) || y.contains(x))
    }

    // ---------- scan ----------

    /** A bill photo from the scanner (already copied into app storage). */
    fun scanImage(path: String) {
        _state.update { it.copy(draft = it.draft.copy(imagePath = path)) }
        read(inputLanguage.value)
    }

    private fun read(language: InputLanguage) {
        val path = _state.value.draft.imagePath ?: return
        _state.update { it.copy(reading = true, scanProblem = null) }
        viewModelScope.launch {
            when (val r = container.scanner.readBill(Uri.fromFile(File(path)), language)) {
                is ScanOutcome.Success -> applyScan(r.value)
                ScanOutcome.NeedsInternet -> _state.update { it.copy(reading = false, scanProblem = ScanProblem.NEEDS_INTERNET) }
                ScanOutcome.Unreadable -> _state.update { it.copy(reading = false, scanProblem = ScanProblem.UNREADABLE) }
            }
        }
    }

    private fun applyScan(scan: BillScan) {
        val scanned = scan.items.map { BillDraftItem(keys.getAndIncrement(), it.name, it.quantity, plain(it.price)) }
        _state.update { s ->
            val d = s.draft
            // Rows without prices came from the shopping list: the printed bill replaces them.
            // If the user already priced rows, the scanned lines are added below.
            val items = when {
                scanned.isEmpty() -> d.items
                d.items.all { it.price.isBlank() } -> scanned
                else -> d.items + scanned
            }
            val sum = items.sumOf { Money.parse(it.price) ?: 0.0 }
            val printedTotal = scan.total.takeIf { it > 0 && d.items.all { i -> i.price.isBlank() } }
            s.copy(
                reading = false,
                draft = d.copy(
                    storeName = d.storeName.ifBlank { scan.store },
                    address = d.address.ifBlank { scan.address },
                    currency = scan.currency.takeIf { it.length == 3 } ?: d.currency,
                    purchasedAt = parseDate(scan.date) ?: d.purchasedAt,
                    items = items
                ),
                totalText = plain(printedTotal ?: sum),
                totalManual = printedTotal != null && kotlin.math.abs(printedTotal - sum) > 0.009
            )
        }
    }

    // ---------- video scan (long bills) ----------

    private val video = VideoBillCollector()
    private val _videoFound = MutableStateFlow(0)
    /** Items confirmed so far by the video scanner. */
    val videoFound: StateFlow<Int> = _videoFound.asStateFlow()

    fun startVideo() {
        video.clear()
        _videoFound.value = 0
    }

    /** Reads one camera frame (off the main thread) and adds its printed lines to the video bill. */
    suspend fun readVideoFrame(bitmap: Bitmap) {
        val rows = container.scanner.readBillRows(bitmap, inputLanguage.value)
        if (rows.isEmpty()) return
        val frame = ReceiptParser.parse(rows)
        withContext(Dispatchers.Main.immediate) {
            video.add(frame)
            _videoFound.value = video.confirmed().size
        }
    }

    /** Done in the video scanner: puts the collected lines into the bill. False if nothing was read. */
    fun finishVideo(): Boolean {
        val r = video.result()
        startVideo()
        if (r.items.isEmpty()) return false
        applyScan(
            BillScan(
                store = r.store,
                address = "",
                date = r.date,
                currency = "",
                items = r.items.map { BillScanItem(it.name, it.quantity, it.price) },
                total = r.total
            )
        )
        return true
    }

    fun rescanIn(language: InputLanguage) {
        viewModelScope.launch { container.prefs.setInputLanguage(language) }
        read(language)
    }

    fun setInputLanguage(language: InputLanguage) {
        viewModelScope.launch { container.prefs.setInputLanguage(language) }
    }

    // ---------- other fields ----------

    /** Fills the shop location (only for new bills, unless asked again). */
    fun fetchLocation(force: Boolean = false) {
        val d = _state.value.draft
        if (!force && (d.id != null || d.lat != null)) return
        if (!container.location.hasPermission()) return
        _state.update { it.copy(locating = true) }
        viewModelScope.launch {
            val place = container.location.current()
            _state.update { s ->
                s.copy(
                    locating = false,
                    draft = if (place == null) s.draft else s.draft.copy(
                        lat = place.lat, lng = place.lng,
                        address = place.address.ifBlank { s.draft.address }
                    )
                )
            }
        }
    }

    fun setStore(v: String) = _state.update { it.copy(draft = it.draft.copy(storeName = v)) }
    fun setAddress(v: String) = _state.update { it.copy(draft = it.draft.copy(address = v)) }
    fun setDate(millis: Long) = _state.update { it.copy(draft = it.draft.copy(purchasedAt = millis)) }
    fun setTotal(v: String) = _state.update { it.copy(totalText = v, totalManual = true) }
    fun useItemsSum() = _state.update { it.copy(totalText = plain(it.itemsSum), totalManual = false) }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val total = Money.parse(s.totalText) ?: s.itemsSum
            container.bills.save(s.draft.copy(total = total))
            _state.update { it.copy(saving = false) }
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = _state.value.draft.id ?: return onDeleted()
        viewModelScope.launch {
            container.bills.delete(id)
            onDeleted()
        }
    }

    private fun parseDate(text: String): Long? = try {
        val date = LocalDate.parse(text)
        val time = if (date == LocalDate.now()) LocalTime.now() else LocalTime.NOON
        date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: Exception) {
        null
    }

    private fun plain(v: Double): String =
        if (v == 0.0) "" else if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(Locale.US, v)
}
