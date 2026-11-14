package com.icoordinator.ui.notes

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.icoordinator.R
import com.icoordinator.accounting.AccountingFormat
import com.icoordinator.personal.BlockType
import com.icoordinator.personal.NoteBlock
import com.icoordinator.personal.NoteBlocks
import com.icoordinator.personal.SpanKind
import com.icoordinator.security.LockState
import com.icoordinator.ui.components.CheckCircle
import com.icoordinator.ui.components.ConfirmDeleteDialog
import com.icoordinator.ui.components.PageHeader
import com.icoordinator.ui.components.TbIcon
import kotlinx.coroutines.launch

// Palettes (index -> colour); the indices are what NoteDocument stores in TextSpan.value.
private val HighlightColors = listOf(Color(0xFFFFF59D), Color(0xFFC5E1A5), Color(0xFF90CAF9), Color(0xFFF48FB1))
private val TextColors = listOf(
    Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFF43A047),
    Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFF757575),
)

/** Dark text on highlighted text so it stays readable in the dark theme. */
private val HighlightedTextColor = Color(0xFF1B1B1D)

private fun sizeFactor(step: Int): Double? = when (step) {
    -1 -> 0.85
    1 -> 1.25
    2 -> 1.5
    else -> null
}

private fun styledText(block: NoteBlock): AnnotatedString = buildAnnotatedString {
    append(block.text)
    val len = block.text.length
    // highlight first, so a text colour span added later wins over the dark highlight text colour
    for (sp in block.spans) {
        if (sp.kind != SpanKind.HIGHLIGHT) continue
        val bg = HighlightColors.getOrNull(sp.value) ?: continue
        val s = sp.start.coerceIn(0, len)
        val e = sp.end.coerceIn(0, len)
        if (e > s) addStyle(SpanStyle(background = bg, color = HighlightedTextColor), s, e)
    }
    for (sp in block.spans) {
        val s = sp.start.coerceIn(0, len)
        val e = sp.end.coerceIn(0, len)
        if (e <= s) continue
        val style: SpanStyle? = when (sp.kind) {
            SpanKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            SpanKind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            SpanKind.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
            SpanKind.COLOR -> TextColors.getOrNull(sp.value)?.let { SpanStyle(color = it) }
            SpanKind.SIZE -> sizeFactor(sp.value)?.let { SpanStyle(fontSize = it.em) }
            SpanKind.HIGHLIGHT -> null
        }
        if (style != null) addStyle(style, s, e)
    }
}

@Composable
fun NoteEditorScreen(noteId: Long?, folderId: Long?, onBack: () -> Unit) {
    val vm: NoteEditorViewModel = viewModel(
        key = "note_editor_" + (noteId ?: "new"),
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NoteEditorViewModel(noteId, folderId) as T
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val goBack: () -> Unit = {
        scope.launch {
            vm.saveNow()
            onBack()
        }
    }
    BackHandler(onBack = goBack)

    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.saveInBackground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PageHeader(title = "یادداشت", onBack = goBack)
        if (state.loaded) {
            EditorBody(vm = vm, state = state)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.EditorBody(vm: NoteEditorViewModel, state: EditorState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    var titleValue by remember { mutableStateOf(TextFieldValue(state.title)) }
    var imageToDelete by remember { mutableStateOf<Long?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        LockState.skipNextLock = false
        if (uri != null) {
            scope.launch {
                val name = NoteImages.import(context, uri)
                if (name != null) {
                    vm.insertImage(name)
                } else {
                    Toast.makeText(context, "افزودن عکس ممکن نشد", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // bring the block that should get the focus into the list (it may be scrolled out)
    val request = state.focusRequest
    LaunchedEffect(request?.token) {
        if (request != null) {
            val index = state.blocks.indexOfFirst { it.id == request.blockId }
            val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == index }
            if (index >= 0 && !visible) listState.scrollToItem(index)
        }
    }

    BasicTextField(
        value = titleValue,
        onValueChange = {
            titleValue = it
            vm.onTitleChange(it.text)
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .onFocusChanged { if (it.isFocused) vm.onTitleFocused() },
        decorationBox = { inner ->
            Box {
                if (titleValue.text.isEmpty()) {
                    Text(
                        text = "عنوان",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            }
        },
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(state.blocks, key = { _, block -> block.id }) { index, block ->
            if (block.type == BlockType.IMAGE) {
                ImageBlock(block = block, onLongPress = { imageToDelete = block.id })
            } else {
                TextBlock(
                    block = block,
                    number = NoteBlocks.numberFor(state.blocks, index),
                    state = state,
                    vm = vm,
                )
            }
        }
    }

    EditorToolbar(
        state = state,
        vm = vm,
        onPickImage = {
            LockState.skipNextLock = true
            pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
    )

    val deleting = imageToDelete
    if (deleting != null) {
        ConfirmDeleteDialog(
            itemName = "عکس",
            onConfirm = {
                imageToDelete = null
                vm.deleteImage(deleting)
            },
            onDismiss = { imageToDelete = null },
        )
    }
}

@Composable
private fun TextBlock(block: NoteBlock, number: Int, state: EditorState, vm: NoteEditorViewModel) {
    val scheme = MaterialTheme.colorScheme
    val requester = remember { FocusRequester() }
    val styled = remember(block.text, block.spans) { styledText(block) }
    var tfv by remember(block.id) { mutableStateOf(TextFieldValue(styled, TextRange(block.text.length))) }

    // The model changed this block's text by itself (Enter split, merge): take its text. Typing keeps both equal.
    LaunchedEffect(block.text, state.structRevision) {
        val latest = vm.state.value.blocks.firstOrNull { it.id == block.id }
        if (latest != null && latest.text != tfv.text) {
            val cursor = tfv.selection.start.coerceIn(0, latest.text.length)
            tfv = TextFieldValue(styledText(latest), TextRange(cursor))
        }
    }
    val request = state.focusRequest
    LaunchedEffect(request?.token) {
        if (request != null && request.blockId == block.id) {
            requester.requestFocus()
            if (request.cursor >= 0) {
                val c = request.cursor.coerceIn(0, tfv.text.length)
                tfv = tfv.copy(selection = TextRange(c))
                vm.onBlockSelection(block.id, c, c)
            }
            vm.onFocusRequestHandled(request.token)
        }
    }

    // while the model is one frame behind the field, show the field's own value untouched
    val shown = if (styled.text == tfv.text && tfv.annotatedString != styled) tfv.copy(annotatedString = styled) else tfv

    val baseStyle: TextStyle = when {
        block.type == BlockType.HEADING -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = scheme.onSurface)
        block.type == BlockType.CHECK && block.checked -> MaterialTheme.typography.bodyLarge.copy(
            color = scheme.onSurfaceVariant,
            textDecoration = TextDecoration.LineThrough,
        )
        else -> MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface)
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        when (block.type) {
            BlockType.BULLET -> MarkerText("•")
            BlockType.NUMBERED -> MarkerText(AccountingFormat.digits("$number."))
            BlockType.CHECK -> CheckCircle(
                checked = block.checked,
                onCheckedChange = { vm.setChecked(block.id, it) },
                modifier = Modifier.size(40.dp),
            )
            else -> Unit
        }
        BasicTextField(
            value = shown,
            onValueChange = { new ->
                tfv = new
                vm.onBlockValueChange(block.id, new.text, new.selection.start, new.selection.end)
            },
            textStyle = baseStyle,
            cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .focusRequester(requester)
                .onFocusChanged { focus ->
                    if (focus.isFocused) {
                        vm.onBlockFocused(block.id)
                        vm.onBlockSelection(block.id, tfv.selection.start, tfv.selection.end)
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace &&
                        tfv.selection.collapsed && tfv.selection.start == 0
                    ) {
                        vm.onBackspaceAtStart(block.id)
                    } else {
                        false
                    }
                },
        )
    }
}

@Composable
private fun MarkerText(text: String) {
    Box(modifier = Modifier.width(32.dp).padding(top = 8.dp), contentAlignment = Alignment.TopStart) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageBlock(block: NoteBlock, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val name = block.imageName
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, name) {
        value = if (name == null) null else NoteImages.load(context, name, 1200)
    }
    val shape = RoundedCornerShape(16.dp)
    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = "عکس",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .combinedClickable(onClick = {}, onLongClick = onLongPress),
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .combinedClickable(onClick = {}, onLongClick = onLongPress),
            contentAlignment = Alignment.Center,
        ) {
            TbIcon(R.drawable.ic_tb_photo, "عکس", size = 40.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditorToolbar(state: EditorState, vm: NoteEditorViewModel, onPickImage: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val focusedType = state.blocks.firstOrNull { it.id == state.focusedBlockId }?.type
    var highlightMenu by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        HorizontalDivider(color = scheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarButton(R.drawable.ic_tb_bold, "پررنگ", state.isStyleActive(SpanKind.BOLD)) {
                vm.toggleStyle(SpanKind.BOLD)
            }
            ToolbarButton(R.drawable.ic_tb_italic, "کج", state.isStyleActive(SpanKind.ITALIC)) {
                vm.toggleStyle(SpanKind.ITALIC)
            }
            ToolbarButton(R.drawable.ic_tb_underline, "زیرخط", state.isStyleActive(SpanKind.UNDERLINE)) {
                vm.toggleStyle(SpanKind.UNDERLINE)
            }
            Box {
                ToolbarButton(R.drawable.ic_tb_highlight, "هایلایت", state.styleValue(SpanKind.HIGHLIGHT) != null) {
                    highlightMenu = true
                }
                DropdownMenu(expanded = highlightMenu, onDismissRequest = { highlightMenu = false }) {
                    SwatchRow(
                        colors = HighlightColors,
                        selected = state.styleValue(SpanKind.HIGHLIGHT),
                        onPick = {
                            highlightMenu = false
                            vm.setStyleValue(SpanKind.HIGHLIGHT, it)
                            vm.restoreFocus()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("بدون") },
                        onClick = {
                            highlightMenu = false
                            vm.setStyleValue(SpanKind.HIGHLIGHT, null)
                            vm.restoreFocus()
                        },
                    )
                }
            }
            Box {
                ToolbarButton(R.drawable.ic_tb_palette, "رنگ متن", state.styleValue(SpanKind.COLOR) != null) {
                    colorMenu = true
                }
                DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                    SwatchRow(
                        colors = TextColors,
                        selected = state.styleValue(SpanKind.COLOR),
                        onPick = {
                            colorMenu = false
                            vm.setStyleValue(SpanKind.COLOR, it)
                            vm.restoreFocus()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("پیش‌فرض") },
                        onClick = {
                            colorMenu = false
                            vm.setStyleValue(SpanKind.COLOR, null)
                            vm.restoreFocus()
                        },
                    )
                }
            }
            Box {
                ToolbarButton(R.drawable.ic_tb_text_size, "اندازه‌ی متن", state.styleValue(SpanKind.SIZE) != null) {
                    sizeMenu = true
                }
                DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                    val current = state.styleValue(SpanKind.SIZE)
                    listOf("کوچک" to -1, "عادی" to null, "بزرگ" to 1, "خیلی بزرگ" to 2).forEach { (label, step) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    fontWeight = if (current == step) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                sizeMenu = false
                                vm.setStyleValue(SpanKind.SIZE, step)
                                vm.restoreFocus()
                            },
                        )
                    }
                }
            }
            ToolbarButton(R.drawable.ic_tb_heading, "عنوان", focusedType == BlockType.HEADING) {
                vm.toggleBlockType(BlockType.HEADING)
            }
            ToolbarButton(R.drawable.ic_tb_list, "فهرست نقطه‌ای", focusedType == BlockType.BULLET) {
                vm.toggleBlockType(BlockType.BULLET)
            }
            ToolbarButton(R.drawable.ic_tb_list_numbers, "فهرست شماره‌دار", focusedType == BlockType.NUMBERED) {
                vm.toggleBlockType(BlockType.NUMBERED)
            }
            ToolbarButton(R.drawable.ic_tb_square_check, "فهرست کارها", focusedType == BlockType.CHECK) {
                vm.toggleBlockType(BlockType.CHECK)
            }
            ToolbarButton(R.drawable.ic_tb_photo, "افزودن عکس", false, onPickImage)
        }
    }
}

@Composable
private fun ToolbarButton(@DrawableRes icon: Int, description: String, active: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) scheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TbIcon(icon, description, size = 24.dp, tint = if (active) scheme.primary else scheme.onSurface)
    }
}

@Composable
private fun SwatchRow(colors: List<Color>, selected: Int?, onPick: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        colors.forEachIndexed { index, color ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(if (selected == index) 2.dp else 1.dp, if (selected == index) scheme.primary else scheme.outlineVariant, CircleShape)
                    .clickable { onPick(index) },
            )
        }
    }
}
