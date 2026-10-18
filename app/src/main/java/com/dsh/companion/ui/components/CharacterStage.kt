package com.dsh.companion.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dsh.companion.data.Character
import com.dsh.companion.data.CharacterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CharacterStage(
    character: Character?,
    clip: String,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val repository = remember { CharacterRepository(context) }

    val drawable by produceState<Drawable?>(null, character?.id, clip) {
        value = withContext(Dispatchers.IO) {
            character?.let { repository.loadClip(it, clip) }
        }
    }

    val viewRef = remember { mutableStateOf<ImageView?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            viewRef.value?.let { imageView ->
                (imageView.drawable as? AnimatedImageDrawable)?.stop()
                imageView.setImageDrawable(null)
            }
        }
    }

    Box(
        modifier = modifier.pointerInput(character?.id, clip) {
            detectTapGestures(
                onTap = { onTap?.invoke() },
                onDoubleTap = { onDoubleTap?.invoke() },
                onLongPress = { onLongPress?.invoke() },
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(Color.TRANSPARENT)
                    viewRef.value = this
                }
            },
            update = { imageView ->
                if (imageView.drawable !== drawable) {
                    imageView.setImageDrawable(drawable)
                    (drawable as? AnimatedImageDrawable)?.start()
                }
            },
        )
    }
}

@Composable
fun CharacterThumb(character: Character?, size: Dp = 56.dp) {
    val context = LocalContext.current
    val repository = remember { CharacterRepository(context) }
    val bitmap by produceState<Bitmap?>(null, character?.id) {
        value = withContext(Dispatchers.IO) {
            character?.let { repository.previewBitmap(it) }
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = character?.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = character?.name?.take(1) ?: "?",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
