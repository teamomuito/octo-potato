package io.github.teamomuito.octopotato.ui

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.teamomuito.octopotato.R
import io.github.teamomuito.octopotato.data.Kind
import io.github.teamomuito.octopotato.data.Snippet
import io.github.teamomuito.octopotato.ui.theme.Pastel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/** Potato. Bobs gently unless told to hold still. */
@Composable
fun Potato(modifier: Modifier = Modifier, boxSize: Dp = 120.dp, bob: Boolean = true) {
    val lift = with(LocalDensity.current) { (boxSize * 0.05f).toPx() }
    val wobble = rememberInfiniteTransition(label = "potato")
    val t by wobble.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    Image(
        painter = painterResource(R.drawable.potato),
        contentDescription = null,
        modifier = modifier
            .size(boxSize)
            .graphicsLayer {
                if (bob) {
                    translationY = -lift * t
                    rotationZ = (t - 0.5f) * 5f
                }
            },
    )
}

@Composable
fun KindPill(kind: Kind, modifier: Modifier = Modifier) {
    val (bg, ink, label) = when (kind) {
        Kind.QR -> Triple(Pastel.lavender, Pastel.lavenderInk, "qr code")
        Kind.BOARDING -> Triple(Pastel.sky, Pastel.skyInk, "boarding pass")
        Kind.CODE -> Triple(Pastel.mint, Pastel.mintInk, "login code")
        Kind.NORMAL -> return
    }
    Pill(label, bg, ink, modifier)
}

@Composable
fun Pill(text: String, background: Color, ink: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = ink,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        modifier = modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private object Thumbs {
    private val cache = object : LruCache<Uri, ImageBitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: Uri, value: ImageBitmap) = value.width * value.height * 4
    }

    fun cached(uri: Uri): ImageBitmap? = cache.get(uri)

    fun load(resolver: ContentResolver, uri: Uri): ImageBitmap? = try {
        resolver.loadThumbnail(uri, Size(360, 720), null).asImageBitmap().also { cache.put(uri, it) }
    } catch (e: Exception) {
        null
    }
}

/** A cropped preview of the top of a screenshot, which is usually where the good bit is. */
@Composable
fun Thumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val resolver = LocalContext.current.contentResolver
    val image by produceState(Thumbs.cached(uri), uri) {
        if (value == null) value = withContext(Dispatchers.IO) { Thumbs.load(resolver, uri) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The whole screenshot, sized for the screen. */
@Composable
fun FullImage(uri: Uri, modifier: Modifier = Modifier) {
    val resolver = LocalContext.current.contentResolver
    val maxWidth = (LocalConfiguration.current.screenWidthDp * LocalDensity.current.density).toInt()
    val image by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { decodeForScreen(resolver, uri, maxWidth) }
    }
    val img = image
    if (img == null) {
        Box(modifier.fillMaxWidth().aspectRatio(0.55f).background(MaterialTheme.colorScheme.surfaceVariant))
    } else {
        Image(
            bitmap = img,
            contentDescription = "screenshot",
            contentScale = ContentScale.FillWidth,
            modifier = modifier.fillMaxWidth().aspectRatio(img.width.toFloat() / img.height),
        )
    }
}

private fun decodeForScreen(resolver: ContentResolver, uri: Uri, maxWidth: Int): ImageBitmap? = try {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
        val w = info.size.width.toFloat()
        val h = info.size.height.toFloat()
        // fit the screen width, and keep long scrolling screenshots small enough for the gpu
        var scale = minOf(1f, maxWidth / w, MAX_SIDE / h)
        if (w * h * scale * scale > MAX_PIXELS) scale = sqrt(MAX_PIXELS / (w * h))
        if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
    }.asImageBitmap()
} catch (e: Exception) {
    null
}

private const val MAX_SIDE = 8000f
private const val MAX_PIXELS = 16_000_000f

fun highlighted(snippet: String, hit: SpanStyle): AnnotatedString = buildAnnotatedString {
    for (part in Snippet.parts(snippet)) {
        if (part.hit) withStyle(hit) { append(part.text) } else append(part.text)
    }
}

fun hitStyle(background: Color, ink: Color) =
    SpanStyle(background = background, color = ink, fontWeight = FontWeight.SemiBold)

fun whenTaken(taken: Long): String =
    DateUtils.getRelativeTimeSpanString(taken, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString().lowercase()

fun plural(n: Int, one: String, many: String = one + "s") = if (n == 1) "1 $one" else "$n $many"

fun share(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/*")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}

fun openInGallery(context: Context, uri: Uri) {
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(view) }
}

fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    runCatching { context.startActivity(intent) }
}

fun openMediaManagementSettings(context: Context) {
    if (android.os.Build.VERSION.SDK_INT < 31) return
    val intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.fromParts("package", context.packageName, null))
    runCatching { context.startActivity(intent) }.onFailure { openAppSettings(context) }
}
