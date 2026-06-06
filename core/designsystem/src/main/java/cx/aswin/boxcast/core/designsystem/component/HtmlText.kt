package cx.aswin.boxcast.core.designsystem.component

import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat

private class LinkTextView(context: android.content.Context) : TextView(context) {
    var linkClickListener: ((String) -> Boolean)? = null

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        val textVal = this.text
        if (textVal is android.text.Spanned) {
            val action = event.action
            if (action == android.view.MotionEvent.ACTION_DOWN ||
                action == android.view.MotionEvent.ACTION_UP ||
                action == android.view.MotionEvent.ACTION_MOVE
            ) {
                var x = event.x.toInt()
                var y = event.y.toInt()

                x -= totalPaddingLeft
                y -= totalPaddingTop

                x += scrollX
                y += scrollY

                val layout = layout
                if (layout != null) {
                    val line = layout.getLineForVertical(y)
                    val off = layout.getOffsetForHorizontal(line, x.toFloat())

                    val link = textVal.getSpans(off, off, android.text.style.ClickableSpan::class.java)

                    if (link.isNotEmpty()) {
                        if (action == android.view.MotionEvent.ACTION_UP) {
                            val clickable = link[0]
                            var handled = false
                            if (clickable is android.text.style.URLSpan) {
                                handled = linkClickListener?.invoke(clickable.url) == true
                            }
                            if (!handled) {
                                clickable.onClick(this)
                            }
                        }
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }
}

/**
 * A Text component that renders HTML content using a native TextView.
 * Handles styling (bold, italic, links) natively.
 */
@Composable
fun HtmlText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    maxLines: Int = Int.MAX_VALUE,
    onClick: (() -> Unit)? = null,
    onLinkClicked: ((String) -> Boolean)? = null
) {
    val context = LocalContext.current
    val linkTextColor = remember(linkColor) { linkColor.toArgb() }
    val textColor = remember(color) { color.toArgb() }
    
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LinkTextView(ctx).apply {
                params(this, style, textColor, linkTextColor, maxLines)
                linkClickListener = onLinkClicked
                setOnClickListener {
                    onClick?.invoke()
                }
            }
        },
        update = { textView ->
            val linkTextView = textView as LinkTextView
            params(linkTextView, style, textColor, linkTextColor, maxLines)
            linkTextView.linkClickListener = onLinkClicked
            
            val htmlSpanned = HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_COMPACT)
            val spannable = android.text.SpannableString(htmlSpanned)
            val urls = spannable.getSpans(0, spannable.length, android.text.style.URLSpan::class.java)
            for (urlSpan in urls) {
                val start = spannable.getSpanStart(urlSpan)
                val end = spannable.getSpanEnd(urlSpan)
                val flags = spannable.getSpanFlags(urlSpan)
                
                val customSpan = object : android.text.style.URLSpan(urlSpan.url) {
                    override fun updateDrawState(ds: android.text.TextPaint) {
                        super.updateDrawState(ds)
                        ds.isUnderlineText = false
                        ds.typeface = android.graphics.Typeface.create(ds.typeface, android.graphics.Typeface.BOLD)
                    }
                }
                spannable.removeSpan(urlSpan)
                spannable.setSpan(customSpan, start, end, flags)
            }
            
            linkTextView.text = spannable
            linkTextView.setOnClickListener {
                onClick?.invoke()
            }
        }
    )
}

private fun params(
    textView: TextView,
    style: TextStyle,
    textColor: Int,
    linkTextColor: Int,
    maxLines: Int
) {
    textView.apply {
        setTextColor(textColor)
        setLinkTextColor(linkTextColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSize.value)
        
        // Basic line height / spacing
        // Note: fully bridging Compose TextStyle to TextView is complex (font family etc)
        // For now relying on default system font or maybe simplified handling.
        // Ideally we'd map style.fontFamily to Typeface but M3 typography uses custom fonts potentially.
        
        this.maxLines = maxLines
        ellipsize = android.text.TextUtils.TruncateAt.END
        
        // Line spacing
        if (style.lineHeight.isSp) {
            // approximate
            // setLineSpacing(style.lineHeight.value - style.fontSize.value, 1f) 
            // Simple way for basic readability:
            val spacingExtra = 4f // dp/sp
             setLineSpacing(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, resources.displayMetrics), 1.0f)
        }
    }
}
