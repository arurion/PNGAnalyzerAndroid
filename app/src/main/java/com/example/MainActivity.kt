package com.example

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                MainScreen(
                    isDarkTheme = isDarkTheme,
                    onThemeToggle = { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }
}

// 1. Data Model representing image analysis results
data class ImageAnalysisResult(
    val filename: String,
    val format: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val detailFormat: String,
    val detailSub: String,
    val exifCamera: String?,
    val exifSettings: String?,
    val exifDateTime: String?,
    val compressionRatio: Double,
    val compressionMultiplier: Double,
    val memoryBytes: Long,
    val isTooLarge: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var analysisResult by remember { mutableStateOf<ImageAnalysisResult?>(null) }
    var uniqueColorsResult by remember { mutableStateOf<String>("-") }
    var isCalculatingColors by remember { mutableStateOf(false) }

    // Checkboxes configuration for report exporting
    val checkedItems = remember {
        mutableStateMapOf(
            "filename" to true,
            "filesize" to true,
            "dimension" to true,
            "details" to true,
            "exif" to true,
            "colors" to true,
            "compression" to true
        )
    }

    // Modal dialog controls for image export preview
    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    // Image Picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            uniqueColorsResult = "-"
            isCalculatingColors = false
            
            // Perform basic metadata analysis
            val result = analyzeImage(context, uri)
            analysisResult = result
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Image Analyzer",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Theme Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = !isDarkTheme,
                            onCheckedChange = { onThemeToggle() },
                            modifier = Modifier.testTag("theme_switch")
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Image Picker / Upload Area
            item {
                if (selectedImageUri == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { imagePickerLauncher.launch("image/*") }
                            .testTag("upload_area"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Upload Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "画像を選択してください",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "JPEG, PNG, GIF, WebP に対応",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Preview Image",
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            IconButton(
                                onClick = {
                                    selectedImageUri = null
                                    analysisResult = null
                                    uniqueColorsResult = "-"
                                    isCalculatingColors = false
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .size(36.dp)
                                    .testTag("reset_image_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear image",
                                    tint = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("reselect_button")
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("別の画像を選択", fontSize = 14.sp)
                        }
                    }
                }
            }

            // Alert for extremely large images
            analysisResult?.let { result ->
                if (result.isTooLarge) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "非常に大きな画像です",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "ピクセル数が非常に多いため、デバイスのメモリを保護するため色数の計算が制限される場合があります。",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Analysis Results Table
            analysisResult?.let { result ->
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        // 1. Filename / Format Row
                        ResultRow(
                            checked = checkedItems["filename"] ?: true,
                            onCheckedChange = { checkedItems["filename"] = it },
                            title = "ファイル名 / 形式",
                            primaryValue = "${result.filename}  [${result.format}]",
                            testTag = "row_filename"
                        )

                        // 2. Filesize Row
                        ResultRow(
                            checked = checkedItems["filesize"] ?: true,
                            onCheckedChange = { checkedItems["filesize"] = it },
                            title = "ファイルサイズ",
                            primaryValue = "${formatBytes(result.sizeBytes)} (${result.sizeBytes.toLocaleString()} Bytes)",
                            testTag = "row_filesize"
                        )

                        // 3. Dimension Row
                        ResultRow(
                            checked = checkedItems["dimension"] ?: true,
                            onCheckedChange = { checkedItems["dimension"] = it },
                            title = "画像サイズ",
                            primaryValue = "${result.width.toLocaleString()} × ${result.height.toLocaleString()} px  (${getAspectRatio(result.width, result.height)})",
                            testTag = "row_dimension"
                        )

                        // 4. Details Row
                        ResultRow(
                            checked = checkedItems["details"] ?: true,
                            onCheckedChange = { checkedItems["details"] = it },
                            title = "フォーマット詳細",
                            primaryValue = result.detailFormat,
                            secondaryValue = result.detailSub.ifEmpty { null },
                            testTag = "row_details"
                        )

                        // 5. EXIF Row (only shown if EXIF data is available)
                        if (result.exifCamera != null) {
                            ResultRow(
                                checked = checkedItems["exif"] ?: true,
                                onCheckedChange = { checkedItems["exif"] = it },
                                title = "Exif メタデータ",
                                primaryValue = result.exifCamera,
                                secondaryValue = result.exifSettings,
                                testTag = "row_exif"
                            )
                        }

                        // 6. Color Count Row
                        ResultRow(
                            checked = checkedItems["colors"] ?: true,
                            onCheckedChange = { checkedItems["colors"] = it },
                            title = "使用色数",
                            primaryValue = uniqueColorsResult,
                            testTag = "row_colors",
                            actionContent = {
                                if (uniqueColorsResult == "-") {
                                    if (isCalculatingColors) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("計算中...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    isCalculatingColors = true
                                                    val colors = countUniqueColors(context, selectedImageUri!!)
                                                    uniqueColorsResult = if (colors > 0) colors.toLocaleString() else "計測不可"
                                                    isCalculatingColors = false
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.Transparent,
                                                contentColor = MaterialTheme.colorScheme.onBackground
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .height(32.dp)
                                                .testTag("btn_calc_colors")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Calculate,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("計算", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        )

                        // 7. Compression / Memory Row
                        ResultRow(
                            checked = checkedItems["compression"] ?: true,
                            onCheckedChange = { checkedItems["compression"] = it },
                            title = "圧縮率 / 展開後メモリ",
                            primaryValue = "${if (result.compressionRatio < 0.001) String.format("%.2e", result.compressionRatio) else String.format("%.4f", result.compressionRatio)} %  (1:${result.compressionMultiplier.toLocaleStringMax0()})",
                            secondaryValue = "${formatBytes(result.memoryBytes)} (${result.memoryBytes.toLocaleString()} Bytes)",
                            testTag = "row_compression"
                        )
                    }
                }

                // Generate Report Button
                item {
                    Button(
                        onClick = {
                            generatedBitmap = generateLogImage(
                                context = context,
                                result = result,
                                uniqueColors = uniqueColorsResult,
                                checkedItems = checkedItems,
                                isDarkTheme = isDarkTheme
                            )
                            showExportDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_generate")
                    ) {
                        Text("ログ画像を出力 (レポート生成)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Modal Dialog to display the generated PNG and share / save
    if (showExportDialog && generatedBitmap != null) {
        Dialog(
            onDismissRequest = { showExportDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f)),
                color = Color.Black.copy(alpha = 0.9f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Report image preview
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = generatedBitmap,
                            contentDescription = "Generated Report Image",
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Controls in modal dialog
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Close button
                        Button(
                            onClick = { showExportDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF222222),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_close_dialog")
                        ) {
                            Text("閉じる", fontSize = 14.sp)
                        }

                        // Save to gallery button
                        Button(
                            onClick = {
                                val uri = saveBitmapToGallery(context, generatedBitmap!!)
                                if (uri != null) {
                                    Toast.makeText(context, "ギャラリーに保存しました", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "保存に失敗しました", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_save_dialog")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("保存", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        // Share button
                        IconButton(
                            onClick = { shareBitmap(context, generatedBitmap!!) },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .testTag("btn_share_dialog")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share Report", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResultRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    primaryValue: String,
    secondaryValue: String? = null,
    testTag: String = "",
    actionContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            )
            .padding(12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .padding(end = 8.dp)
                .size(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = primaryValue,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (secondaryValue != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = secondaryValue,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }

        if (actionContent != null) {
            Box(
                modifier = Modifier.padding(start = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                actionContent()
            }
        }
    }
}

// 2. Heavy processing routines & analysis engines
fun detectFormatFromBytes(context: Context, uri: Uri): Pair<String, List<String>> {
    var format = "Unknown"
    val details = mutableListOf<String>()
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = ByteArray(32)
            val read = input.read(bytes)
            if (read >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                format = "JPEG"
                details.add("Lossy")
                details.add("8-bit")
            } else if (read >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte() &&
                bytes[4] == 0x0D.toByte() &&
                bytes[5] == 0x0A.toByte() &&
                bytes[6] == 0x1A.toByte() &&
                bytes[7] == 0x0A.toByte()
            ) {
                format = "PNG"
                details.add("Lossless")
            } else if (read >= 3 && bytes[0] == 'G'.toByte() && bytes[1] == 'I'.toByte() && bytes[2] == 'F'.toByte()) {
                format = "GIF"
                details.add("Lossless")
                details.add("Indexed")
            } else if (read >= 12 &&
                bytes[0] == 'R'.toByte() && bytes[1] == 'I'.toByte() && bytes[2] == 'F'.toByte() && bytes[3] == 'F'.toByte() &&
                bytes[8] == 'W'.toByte() && bytes[9] == 'E'.toByte() && bytes[10] == 'B'.toByte() && bytes[11] == 'P'.toByte()
            ) {
                format = "WebP"
                details.add("Lossless/Lossy")
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return Pair(format, details)
}

fun analyzeImage(context: Context, uri: Uri): ImageAnalysisResult? {
    try {
        val contentResolver = context.contentResolver

        var filename = "Unknown"
        var sizeBytes: Long = 0
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) filename = it.getString(nameIndex)
                val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex != -1) sizeBytes = it.getLong(sizeIndex)
            }
        }

        if (sizeBytes == 0L) {
            try {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                    sizeBytes = it.length
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Get dimensions without loading pixel data (memory efficient)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        val width = options.outWidth
        val height = options.outHeight

        if (width <= 0 || height <= 0) return null

        val isTooLarge = (width > 32768 || height > 32768 || (width.toLong() * height.toLong()) > 50_000_000L)

        // Read binary headers
        val (detectedFmt, detailsList) = detectFormatFromBytes(context, uri)
        val format = if (detectedFmt != "Unknown") detectedFmt else {
            val mime = contentResolver.getType(uri) ?: ""
            when {
                mime.contains("jpeg") || mime.contains("jpg") -> "JPEG"
                mime.contains("png") -> "PNG"
                mime.contains("gif") -> "GIF"
                mime.contains("webp") -> "WebP"
                else -> "Unknown"
            }
        }

        // EXIF Parser using modern ExifInterface
        var exifCamera: String? = null
        var exifSettings: String? = null
        var exifDateTime: String? = null

        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val exifInterface = androidx.exifinterface.media.ExifInterface(input)
                val make = exifInterface.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MAKE)
                val model = exifInterface.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_MODEL)

                val camera = when {
                    make != null && model != null -> {
                        if (model.startsWith(make)) model else "$make $model"
                    }
                    model != null -> model
                    make != null -> make
                    else -> null
                }
                exifCamera = camera

                val exposureTime = exifInterface.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_EXPOSURE_TIME)
                val fNumber = exifInterface.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_F_NUMBER)
                val iso = exifInterface.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_ISO_SPEED_RATINGS)
                val focalLength = exifInterface.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_FOCAL_LENGTH)
                val dateTime = exifInterface.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exifInterface.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME)

                val settingsList = mutableListOf<String>()
                if (exposureTime != null) {
                    val expDouble = exposureTime.toDoubleOrNull()
                    if (expDouble != null) {
                        if (expDouble < 1.0) {
                            settingsList.add("1/${Math.round(1.0 / expDouble)}s")
                        } else {
                            settingsList.add("${expDouble}s")
                        }
                    } else {
                        settingsList.add("${exposureTime}s")
                    }
                }
                if (fNumber != null) {
                    val fDouble = fNumber.toDoubleOrNull()
                    if (fDouble != null) settingsList.add("f/${String.format("%.1f", fDouble)}") else settingsList.add("f/$fNumber")
                }
                if (iso != null) settingsList.add("ISO $iso")
                if (focalLength != null) {
                    val focalDouble = focalLength.toDoubleOrNull()
                    if (focalDouble != null) settingsList.add("${String.format("%.1f", focalDouble)}mm") else settingsList.add("${focalLength}mm")
                }

                if (settingsList.isNotEmpty()) exifSettings = settingsList.joinToString(" | ")
                exifDateTime = dateTime
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val memoryBytes = width.toLong() * height.toLong() * 4L
        val compressionRatio = (sizeBytes.toDouble() / memoryBytes.toDouble()) * 100.0
        val compressionMultiplier = memoryBytes.toDouble() / sizeBytes.toDouble()

        val finalDetailFormat = if (format == "PNG") {
            detailsList.getOrNull(0) ?: "Lossless"
        } else {
            detailsList.getOrNull(0) ?: "Lossless/Lossy"
        }
        val finalDetailSub = detailsList.drop(1).joinToString(", ")

        return ImageAnalysisResult(
            filename = filename,
            format = format,
            sizeBytes = sizeBytes,
            width = width,
            height = height,
            detailFormat = finalDetailFormat,
            detailSub = finalDetailSub,
            exifCamera = exifCamera,
            exifSettings = exifSettings,
            exifDateTime = exifDateTime,
            compressionRatio = compressionRatio,
            compressionMultiplier = compressionMultiplier,
            memoryBytes = memoryBytes,
            isTooLarge = isTooLarge
        )
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

// Memory efficient row-by-row color counting to avoid OutOfMemoryError on Android heap
suspend fun countUniqueColors(context: Context, uri: Uri): Int = withContext(Dispatchers.Default) {
    var uniqueCount = 0
    try {
        val contentResolver = context.contentResolver
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val bitmap = contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: return@withContext 0

        val w = bitmap.width
        val h = bitmap.height

        // Initialize set with reasonable starting size
        val colorSet = HashSet<Int>(Math.min(w * h, 1_000_000))
        val rowPixels = IntArray(w)

        for (y in 0 until h) {
            bitmap.getPixels(rowPixels, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                colorSet.add(rowPixels[x])
            }
            if (y % 100 == 0) {
                yield() // Cooperatively support cancellation and avoid blocking the execution thread
            }
        }
        uniqueCount = colorSet.size
        bitmap.recycle()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    uniqueCount
}

// 3. PNG Generation Canvas Engine
fun generateLogImage(
    context: Context,
    result: ImageAnalysisResult,
    uniqueColors: String,
    checkedItems: Map<String, Boolean>,
    isDarkTheme: Boolean
): Bitmap {
    val itemsToDraw = mutableListOf<Triple<String, String, List<String>>>()

    if (checkedItems["filename"] == true) {
        itemsToDraw.add(Triple("ファイル名 / 形式", "${result.filename}  [${result.format}]", emptyList()))
    }
    if (checkedItems["filesize"] == true) {
        itemsToDraw.add(Triple("ファイルサイズ", formatBytes(result.sizeBytes) + " (${result.sizeBytes.toLocaleString()} Bytes)", emptyList()))
    }
    if (checkedItems["dimension"] == true) {
        itemsToDraw.add(Triple("画像サイズ", "${result.width.toLocaleString()} × ${result.height.toLocaleString()} px  (${getAspectRatio(result.width, result.height)})", emptyList()))
    }
    if (checkedItems["details"] == true) {
        val subs = mutableListOf<String>()
        if (result.detailSub.isNotEmpty()) subs.add(result.detailSub)
        itemsToDraw.add(Triple("フォーマット詳細", result.detailFormat, subs))
    }
    if (checkedItems["exif"] == true && result.exifCamera != null) {
        val subs = mutableListOf<String>()
        if (result.exifSettings != null) subs.add(result.exifSettings)
        if (result.exifDateTime != null) subs.add(result.exifDateTime)
        itemsToDraw.add(Triple("Exif メタデータ", result.exifCamera, subs))
    }
    if (checkedItems["colors"] == true) {
        itemsToDraw.add(Triple("使用色数", uniqueColors, emptyList()))
    }
    if (checkedItems["compression"] == true) {
        val ratioText = if (result.compressionRatio < 0.001) String.format("%.2e", result.compressionRatio) else String.format("%.4f", result.compressionRatio)
        val multText = "1 : " + result.compressionMultiplier.toLocaleStringMax0()
        itemsToDraw.add(Triple("圧縮率 / 展開後メモリ", "$ratioText %  ($multText)", listOf(formatBytes(result.memoryBytes) + " (${result.memoryBytes.toLocaleString()} Bytes)")))
    }

    // Set colors based on chosen theme
    val bgColor = if (isDarkTheme) 0xFF0D1117.toInt() else 0xFFF6F8FA.toInt()
    val textColor = if (isDarkTheme) 0xFFC9D1D9.toInt() else 0xFF24292F.toInt()
    val borderColor = if (isDarkTheme) 0xFF30363D.toInt() else 0xFFD0D7DE.toInt()
    val accentColor = if (isDarkTheme) 0xFF58A6FF.toInt() else 0xFF0969DA.toInt()

    // Calculate report dynamic height
    var totalHeight = 120
    itemsToDraw.forEach { item ->
        totalHeight += 120 + (item.third.size * 45)
    }

    val bitmap = Bitmap.createBitmap(1200, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(bgColor)

    val paint = Paint().apply {
        isAntiAlias = true
    }

    var currentY = 60f
    itemsToDraw.forEachIndexed { index, item ->
        val itemHeight = 120 + (item.third.size * 45)
        val lineH = 50f + (item.third.size * 45)

        // Accent indicator
        paint.color = accentColor
        canvas.drawRect(80f, currentY, 86f, currentY + lineH, paint)

        // Draw title
        paint.color = textColor
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        canvas.drawText(item.first, 110f, currentY + 18f, paint)

        // Draw main value
        paint.textSize = 36f
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        canvas.drawText(item.second, 110f, currentY + 62f, paint)

        // Draw secondary detail lists
        var nextY = currentY + 62f
        if (item.third.isNotEmpty()) {
            paint.textSize = 28f
            item.third.forEach { sub ->
                nextY += 45f
                canvas.drawText(sub, 110f, nextY, paint)
            }
        }

        // Horizontal card separator line
        if (index < itemsToDraw.size - 1) {
            paint.color = borderColor
            canvas.drawRect(80f, currentY + itemHeight - 20f, 1120f, currentY + itemHeight - 18f, paint)
        }

        currentY += itemHeight
    }

    return bitmap
}

// MediaStore gallery saver
fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
    val filename = "analyzer_log_${System.currentTimeMillis()}.png"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ImageAnalyzer")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    uri?.let {
        try {
            resolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    return uri
}

// Shared Intent builder using FileProvider
fun shareBitmap(context: Context, bitmap: Bitmap) {
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "analyzer_log.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()

        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(contentUri, context.contentResolver.getType(contentUri))
            putExtra(Intent.EXTRA_STREAM, contentUri)
            type = "image/png"
        }
        context.startActivity(Intent.createChooser(shareIntent, "ログ画像を共有"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// String formatting utility helpers
fun Long.toLocaleString(): String {
    return NumberFormat.getIntegerInstance().format(this)
}

fun Int.toLocaleString(): String {
    return NumberFormat.getIntegerInstance().format(this)
}

fun Double.toLocaleStringMax0(): String {
    val formatter = NumberFormat.getNumberInstance()
    formatter.maximumFractionDigits = 0
    return formatter.format(this)
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 Bytes"
    val k = 1024.0
    val sizes = arrayOf("Bytes", "KiB", "MiB", "GiB", "TiB")
    val i = Math.floor(Math.log(bytes.toDouble()) / Math.log(k)).toInt()
    val r = bytes.toDouble() / Math.pow(k, i.toDouble())
    return String.format("%.2f %s", r, sizes.getOrElse(i) { "Bytes" })
}

fun getAspectRatio(w: Int, h: Int): String {
    fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    val r = gcd(w, h)
    return if (w / r > 100 || h / r > 100) {
        String.format("%.2f:1", w.toDouble() / h.toDouble())
    } else {
        "${w / r}:${h / r}"
    }
}
