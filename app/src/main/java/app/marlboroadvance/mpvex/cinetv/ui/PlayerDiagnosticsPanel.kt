package app.marlboroadvance.mpvex.cinetv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Drop this overlay into your PlayerActivity's compose view to debug the Black Screen issues.
 * Pass the real-time stats from your MPV instance into this composable.
 */
@Composable
fun PlayerDiagnosticsPanel(
    resolvedUrl: String,
    mpvState: String,
    videoCodec: String,
    audioCodec: String,
    bufferingPercent: Int,
    currentError: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(16.dp)
    ) {
        Text("DEVELOPER DIAGNOSTICS", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        
        DiagnosticRow("State", mpvState)
        DiagnosticRow("Error", currentError.ifBlank { "None" })
        DiagnosticRow("Buffer", "$bufferingPercent%")
        DiagnosticRow("V-Codec", videoCodec.ifBlank { "Unknown" })
        DiagnosticRow("A-Codec", audioCodec.ifBlank { "Unknown" })
        DiagnosticRow("URL", resolvedUrl, maxLines = 3)
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, maxLines: Int = 1) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label: ", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(60.dp))
        Text(value, color = Color.Green, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = maxLines)
    }
}