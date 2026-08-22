package com.something.keystrokes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.something.keystrokes.config.ConfigRepository
import com.something.keystrokes.config.KeyStrokesConfig

@Composable
fun ConfigPage(
    repository: ConfigRepository,
    modifier: Modifier = Modifier
) {
    var configs by remember {
        mutableStateOf(repository.createDefaultListIfNeeded())
    }

    var activeId by remember {
        mutableStateOf(repository.getActiveConfigId())
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<KeyStrokesConfig?>(null) }
    var deleteConfig by remember { mutableStateOf<KeyStrokesConfig?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    fun save(updated: List<KeyStrokesConfig>) {
        configs = updated
        repository.saveConfigs(updated)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "配置",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    "管理 KeyStrokes 的样式与布局配置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledTonalButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 2.dp))
                Text("新建")
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "当前配置",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                val active = configs.firstOrNull { it.id == activeId }
                if (active != null) {
                    ConfigCard(
                        config = active,
                        selected = true,
                        onSelect = {},
                        onEdit = { editingConfig = active },
                        onCopy = {
                            val copy = repository.createFrom(
                                active,
                                "${active.name} 副本",
                                active.description
                            )
                            save(configs + copy)
                            activeId = copy.id
                            repository.setActiveConfig(copy.id)
                        },
                        onDelete = null,
                        onReset = if (active.builtIn) {
                            { showResetDialog = true }
                        } else null
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "所有配置",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(configs, key = { it.id }) { config ->
                ConfigCard(
                    config = config,
                    selected = config.id == activeId,
                    onSelect = {
                        activeId = config.id
                        repository.setActiveConfig(config.id)
                    },
                    onEdit = { editingConfig = config },
                    onCopy = {
                        val copy = repository.createFrom(
                            config,
                            "${config.name} 副本",
                            config.description
                        )
                        save(configs + copy)
                        activeId = copy.id
                        repository.setActiveConfig(copy.id)
                    },
                    onDelete = if (config.builtIn) null else {
                        { deleteConfig = config }
                    },
                    onReset = if (config.builtIn) {
                        { showResetDialog = true }
                    } else null
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "可以自定义 KeyStrokes 的悬浮窗 UI ，高度自定义\n修改正在使用的配置后关闭悬浮窗再打开即可生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showCreateDialog) {
        ConfigNameDialog(
            title = "新建配置",
            confirmText = "创建",
            initialName = "",
            initialDescription = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, description ->
                val source = configs.firstOrNull { it.id == activeId }
                    ?: configs.first()
                val created = repository.createFrom(source, name, description)
                save(configs + created)
                activeId = created.id
                repository.setActiveConfig(created.id)
                showCreateDialog = false
            }
        )
    }

    editingConfig?.let { config ->
        ConfigEditDialog(
            config = config,
            allowNameEdit = !config.builtIn,
            onDismiss = { editingConfig = null },
            onConfirm = { updated ->
                save(configs.map {
                    if (it.id == config.id) updated else it
                })
                editingConfig = null
            }
        )
    }

    deleteConfig?.let { config ->
        AlertDialog(
            onDismissRequest = { deleteConfig = null },
            title = { Text("删除配置") },
            text = { Text("确定要删除“${config.name}”吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val remaining = configs.filterNot { it.id == config.id }
                    save(remaining)
                    if (activeId == config.id) {
                        activeId = ConfigRepository.DEFAULT_ID
                        repository.setActiveConfig(activeId)
                    }
                    deleteConfig = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfig = null }) { Text("取消") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("恢复 Default") },
            text = { Text("这会把 Default 恢复为 KeyStrokes 内置的初始配置，其他配置不会受到影响。") },
            confirmButton = {
                TextButton(onClick = {
                    val reset = repository.resetDefault()
                    save(configs.map {
                        if (it.id == ConfigRepository.DEFAULT_ID) reset else it
                    })
                    showResetDialog = false
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ConfigCard(
    config: KeyStrokesConfig,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: (() -> Unit)?,
    onReset: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (selected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        } else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        config.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (selected) {
                        Text(
                            "  当前使用",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (config.description.isNotBlank()) {
                    Text(
                        config.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "UI ${config.uiScalePercent}% · 透明度 ${config.opacity}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!selected) {
                OutlinedButton(onClick = onSelect) {
                    Text("使用")
                }
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑")
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "复制")
            }
            if (onReset != null) {
                IconButton(onClick = onReset) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = "恢复默认")
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

@Composable
private fun ConfigNameDialog(
    title: String,
    confirmText: String,
    initialName: String,
    initialDescription: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = {
                    onConfirm(name.trim(), description.trim())
                }
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ConfigEditDialog(
    config: KeyStrokesConfig,
    allowNameEdit: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (KeyStrokesConfig) -> Unit
) {
    var name by remember(config.id) { mutableStateOf(config.name) }
    var description by remember(config.id) { mutableStateOf(config.description) }
    var uiScale by remember(config.id) { mutableStateOf(config.uiScalePercent.toFloat()) }
    var textScale by remember(config.id) { mutableStateOf(config.textScalePercent.toFloat()) }
    var opacity by remember(config.id) { mutableStateOf(config.opacity.toFloat()) }
    var animationEnabled by remember(config.id) { mutableStateOf(config.animationEnabled) }
    var cornerRadiusEnabled by remember(config.id) { mutableStateOf(config.cornerRadiusEnabled) }
    var cornerRadius by remember(config.id) { mutableStateOf(config.cornerRadius) }
    var shiftKeyEnabled by remember(config.id) { mutableStateOf(config.shiftKeyEnabled) }
    var replaceSpaceDisplay by remember(config.id) { mutableStateOf(config.replaceSpaceDisplay) }
    var mouseButtonsEnabled by remember(config.id) { mutableStateOf(config.mouseButtonsEnabled) }
    var mouseCpsEnabled by remember(config.id) { mutableStateOf(config.mouseCpsEnabled) }
    var mouseCpsMode by remember(config.id) { mutableStateOf(config.mouseCpsMode.coerceIn(1, 3)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑配置") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    enabled = allowNameEdit,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    "悬浮窗外观",
                    style = MaterialTheme.typography.titleSmall
                )

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "UI 大小",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${uiScale.toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "整体缩放，100% 为默认大小",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = uiScale,
                        onValueChange = { uiScale = it },
                        valueRange = 50f..200f,
                        steps = 14,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "字符缩放",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${textScale.toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "相对于 UI 大小调整按键字符大小，100% 为默认大小",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = textScale,
                        onValueChange = { textScale = it },
                        valueRange = 50f..150f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "透明度",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${opacity.toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "控制整个悬浮窗 UI 的可见程度",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        valueRange = 20f..100f,
                        steps = 15,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "动画效果",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "按键按下和释放时使用约 0.1 秒的平滑过渡",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = animationEnabled,
                        onCheckedChange = { animationEnabled = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "边缘圆角",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "将按键矩形变为平滑圆角，关闭后使用直角",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = cornerRadiusEnabled,
                        onCheckedChange = { cornerRadiusEnabled = it }
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "圆角程度",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (cornerRadiusEnabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${cornerRadius.toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (cornerRadiusEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                    Text(
                        "0% 为直角，50% 为最大圆角",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (cornerRadiusEnabled) 1f else 0.38f
                        )
                    )
                    Slider(
                        value = cornerRadius,
                        onValueChange = { cornerRadius = it },
                        valueRange = 0f..50f,
                        steps = 9,
                        enabled = cornerRadiusEnabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    "按键布局",
                    style = MaterialTheme.typography.titleSmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "启用 SHIFT 键",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "显示 SHIFT 按键",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = shiftKeyEnabled,
                        onCheckedChange = { shiftKeyEnabled = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "替换 SPACE 键显示",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "使用 KeyStrokes Mod 风格的长横线代替 SPACE 字样",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = replaceSpaceDisplay,
                        onCheckedChange = { replaceSpaceDisplay = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "启用鼠标左右键",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "显示 LMB / RMB 按键",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = mouseButtonsEnabled,
                        onCheckedChange = { mouseButtonsEnabled = it }
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "鼠标 CPS 显示",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "显示最近 1 秒内的鼠标点击次数",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = mouseCpsEnabled,
                            onCheckedChange = { mouseCpsEnabled = it },
                            enabled = mouseButtonsEnabled
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(1, 2, 3).forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = mouseCpsMode == mode,
                                onClick = { mouseCpsMode = mode },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = 3
                                ),
                                enabled = mouseButtonsEnabled && mouseCpsEnabled
                            ) {
                                Text("Mode $mode")
                            }
                        }
                    }
                }

            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = {
                    onConfirm(
                        config.copy(
                            name = name.trim(),
                            description = description.trim(),
                            uiScalePercent = uiScale.toInt().coerceIn(50, 200),
                            textScalePercent = textScale.toInt().coerceIn(50, 150),
                            opacity = opacity.toInt().coerceIn(20, 100),
                            animationEnabled = animationEnabled,
                            cornerRadiusEnabled = cornerRadiusEnabled,
                            cornerRadius = cornerRadius.coerceIn(0f, 50f),
                            shiftKeyEnabled = shiftKeyEnabled,
                            replaceSpaceDisplay = replaceSpaceDisplay,
                            mouseButtonsEnabled = mouseButtonsEnabled,
                            mouseCpsEnabled = mouseCpsEnabled,
                            mouseCpsMode = mouseCpsMode.coerceIn(1, 3)
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
