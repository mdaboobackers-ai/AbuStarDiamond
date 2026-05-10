package com.goldsmith.billing.ui.ocr

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.goldsmith.billing.ui.components.GlassCard
import com.goldsmith.billing.ui.components.GoldButton
import com.goldsmith.billing.ui.theme.AuraColors
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HallmarkScannerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var resultText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Select a hallmark photo to scan.") }

    fun scan(uri: Uri) {
        imageUri = uri
        status = "Scanning..."
        resultText = ""
        val image = InputImage.fromFilePath(context, uri)
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(image)
            .addOnSuccessListener { text ->
                resultText = text.text
                status = detectHallmarkSummary(text.text)
            }
            .addOnFailureListener { error ->
                status = error.message ?: "Unable to scan image"
            }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(::scan)
    }

    Scaffold(
        containerColor = AuraColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("HALLMARK OCR", style = MaterialTheme.typography.labelSmall, color = AuraColors.PrimaryContainer) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = AuraColors.PrimaryContainer) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AuraColors.SurfaceContainerLowest.copy(alpha = 0.9f))
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GoldButton(
                    text = "Pick Hallmark Photo",
                    onClick = { picker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Default.PhotoLibrary, null) }
                )
            }
            imageUri?.let { uri ->
                item {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth(), goldBorder = true) {
                    Column(Modifier.padding(18.dp)) {
                        Icon(Icons.Default.DocumentScanner, null, tint = AuraColors.PrimaryContainer)
                        Spacer(Modifier.height(10.dp))
                        Text("Scan Result", style = MaterialTheme.typography.headlineSmall, color = AuraColors.OnSurface)
                        Text(status, style = MaterialTheme.typography.bodyLarge, color = AuraColors.PrimaryContainer, fontWeight = FontWeight.SemiBold)
                        if (resultText.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(resultText, style = MaterialTheme.typography.bodyMedium, color = AuraColors.OnSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun detectHallmarkSummary(text: String): String {
    val normalized = text.uppercase()
    val karat = when {
        "916" in normalized || "22K" in normalized -> "22K / 916"
        "750" in normalized || "18K" in normalized -> "18K / 750"
        "585" in normalized || "14K" in normalized -> "14K / 585"
        "999" in normalized || "24K" in normalized -> "24K / 999"
        else -> "Hallmark text captured"
    }
    val hasBis = "BIS" in normalized || "HUID" in normalized
    return if (hasBis) "$karat, BIS/HUID detected" else karat
}
