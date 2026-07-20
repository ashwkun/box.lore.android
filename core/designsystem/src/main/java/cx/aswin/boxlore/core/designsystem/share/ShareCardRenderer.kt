@file:Suppress("LongParameterList", "TooManyFunctions", "kotlin:S107")

package cx.aswin.boxlore.core.designsystem.share

import android.content.Context
import cx.aswin.boxlore.core.model.ShareTarget

/** Bitmap card composition for [ShareManager] share sheets / Instagram stories. */
internal object ShareCardRenderer {
    fun createShareCard(
        context: Context,
        artwork: android.graphics.Bitmap,
        title: String,
        subtitle: String,
        target: ShareTarget
    ): android.graphics.Bitmap {
        val isStory = target == ShareTarget.INSTAGRAM_STORY
        val width = if (isStory) 1080 else 1200
        val height = if (isStory) 1920 else 1200
        val output = android.graphics.Bitmap.createBitmap(
            width,
            height,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(output)
        canvas.drawColor(android.graphics.Color.parseColor("#2E2DD0"))
        drawShareCardShapes(
            canvas = canvas,
            width = width.toFloat(),
            height = height.toFloat(),
            isStory = isStory
        )

        val artworkSize = if (isStory) 760f else 680f
        val artworkTop = if (isStory) 290f else 55f
        val artworkRect = android.graphics.RectF(
            (width - artworkSize) / 2f,
            artworkTop,
            (width + artworkSize) / 2f,
            artworkTop + artworkSize
        )
        drawRoundedArtwork(
            canvas = canvas,
            artwork = artwork,
            destination = artworkRect,
            cornerRadius = 72f
        )

        if (isStory) {
            val textBottom = drawShareText(
                context = context,
                canvas = canvas,
                canvasWidth = width,
                title = title,
                subtitle = subtitle,
                titleTop = 1_110f,
                titleWidth = 860,
                titleSize = 60f,
                subtitleSize = 44f,
                blockSpacing = 13f
            )
            drawShareBranding(
                context = context,
                canvas = canvas,
                canvasWidth = width,
                brandingTop = textBottom + 44f,
                brandingLabelSize = 34f,
                logoWidth = 480,
                listenNowGap = 22f,
                listenNowSize = 32f
            )
        } else {
            val textBottom = drawShareText(
                context = context,
                canvas = canvas,
                canvasWidth = width,
                title = title,
                subtitle = subtitle,
                titleTop = 785f,
                titleWidth = 960,
                titleSize = 48f,
                subtitleSize = 36f,
                blockSpacing = 8f
            )
            drawShareBranding(
                context = context,
                canvas = canvas,
                canvasWidth = width,
                brandingTop = textBottom + 30f,
                brandingLabelSize = 28f,
                logoWidth = 400,
                listenNowGap = 17f,
                listenNowSize = 28f
            )
        }

        return output
    }

    private fun drawShareCardShapes(
        canvas: android.graphics.Canvas,
        width: Float,
        height: Float,
        isStory: Boolean
    ) {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#5B5BDF")
            alpha = 185
            style = android.graphics.Paint.Style.FILL
        }

        drawExpressiveShape(
            canvas = canvas,
            type = cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.DecorativeType.Puffy,
            left = width - if (isStory) 345f else 315f,
            top = if (isStory) -145f else -165f,
            size = if (isStory) 500f else 455f,
            rotation = 14f,
            paint = paint
        )
        drawExpressiveShape(
            canvas = canvas,
            type = cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.DecorativeType.PuffyDiamond,
            left = if (isStory) -195f else -155f,
            top = if (isStory) 710f else height * 0.35f,
            size = if (isStory) 390f else 320f,
            rotation = -18f,
            paint = paint.apply { alpha = 155 }
        )
        drawExpressiveShape(
            canvas = canvas,
            type = cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.DecorativeType.Cookie4,
            left = if (isStory) -120f else -100f,
            top = if (isStory) 35f else -20f,
            size = if (isStory) 390f else 325f,
            rotation = -24f,
            paint = paint.apply { alpha = 130 }
        )
        drawExpressiveShape(
            canvas = canvas,
            type = cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.DecorativeType.SoftBurst,
            left = width - if (isStory) 230f else 195f,
            top = if (isStory) 710f else 530f,
            size = if (isStory) 430f else 370f,
            rotation = 20f,
            paint = paint.apply { alpha = 180 }
        )
    }

    private fun drawExpressiveShape(
        canvas: android.graphics.Canvas,
        type: cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.DecorativeType,
        left: Float,
        top: Float,
        size: Float,
        rotation: Float,
        paint: android.graphics.Paint
    ) {
        val path = cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.androidPath(
            type = type,
            width = size,
            height = size
        )
        canvas.save()
        canvas.translate(left, top)
        canvas.rotate(rotation, size / 2f, size / 2f)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawShareBranding(
        context: Context,
        canvas: android.graphics.Canvas,
        canvasWidth: Int,
        brandingTop: Float,
        brandingLabelSize: Float,
        logoWidth: Int,
        listenNowGap: Float,
        listenNowSize: Float
    ) {
        val brandingLeft = (canvasWidth - logoWidth) / 2f
        val brandingRight = brandingLeft + logoWidth
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = brandingLabelSize
            typeface = googleSansTypeface(context, android.graphics.Typeface.BOLD)
        }
        val label = "is better on"
        val labelBaseline = brandingTop - textPaint.fontMetrics.ascent
        canvas.drawText(label, brandingLeft, labelBaseline, textPaint)

        val waveStart = brandingLeft + textPaint.measureText(label) + 20f
        val waveCenter = brandingTop + (
            textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent
            ) / 2f
        if (brandingRight > waveStart) {
            val path = android.graphics.Path()
            val amplitude = 9f
            val wavelength = 72f
            var x = waveStart
            path.moveTo(x, waveCenter)
            while (x < brandingRight) {
                x = (x + 3f).coerceAtMost(brandingRight)
                val y = waveCenter + amplitude * kotlin.math.sin(
                    ((x - waveStart) / wavelength) * 2f * kotlin.math.PI.toFloat()
                )
                path.lineTo(x, y)
            }
            canvas.drawPath(
                path,
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 4f
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                }
            )
        }

        val labelHeight = textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent
        val logoTop = (brandingTop + labelHeight + 18f).toInt()
        val logoHeight = (logoWidth * 110f / 805f).toInt()
        val logoLeft = (canvasWidth - logoWidth) / 2
        androidx.core.content.ContextCompat.getDrawable(
            context,
            cx.aswin.boxlore.core.designsystem.R.drawable.ic_boxlore_logo
        )?.mutate()?.apply {
            setTint(android.graphics.Color.WHITE)
            setBounds(
                logoLeft,
                logoTop,
                logoLeft + logoWidth,
                logoTop + logoHeight
            )
            draw(canvas)
        }
        val listenNowTop = logoTop + logoHeight + listenNowGap
        drawCenteredTextBlock(
            canvas = canvas,
            canvasWidth = canvasWidth,
            text = "listen now",
            top = listenNowTop,
            width = logoWidth,
            textSize = listenNowSize,
            maxLines = 1,
            color = android.graphics.Color.parseColor("#DCDCF8"),
            typeface = googleSansTypeface(context, android.graphics.Typeface.NORMAL)
        )
    }

    private fun drawShareText(
        context: Context,
        canvas: android.graphics.Canvas,
        canvasWidth: Int,
        title: String,
        subtitle: String,
        titleTop: Float,
        titleWidth: Int,
        titleSize: Float,
        subtitleSize: Float,
        blockSpacing: Float
    ): Float {
        val titleHeight = drawCenteredTextBlock(
            canvas = canvas,
            canvasWidth = canvasWidth,
            text = title,
            top = titleTop,
            width = titleWidth,
            textSize = titleSize,
            maxLines = 2,
            color = android.graphics.Color.WHITE,
            typeface = googleSansTypeface(context, android.graphics.Typeface.BOLD)
        )
        var nextTop = titleTop + titleHeight
        if (subtitle.isNotBlank()) {
            val bySize = subtitleSize * 0.78f
            val byTop = nextTop + blockSpacing
            val byHeight = drawCenteredTextBlock(
                canvas = canvas,
                canvasWidth = canvasWidth,
                text = "by",
                top = byTop,
                width = titleWidth,
                textSize = bySize,
                maxLines = 1,
                color = android.graphics.Color.parseColor("#BEBEE8"),
                typeface = googleSansTypeface(context, android.graphics.Typeface.NORMAL)
            )
            val subtitleTop = byTop + byHeight + blockSpacing * 0.5f
            val subtitleHeight = drawCenteredTextBlock(
                canvas = canvas,
                canvasWidth = canvasWidth,
                text = subtitle,
                top = subtitleTop,
                width = titleWidth,
                textSize = subtitleSize,
                maxLines = 1,
                color = android.graphics.Color.parseColor("#DCDCF8"),
                typeface = googleSansTypeface(context, android.graphics.Typeface.NORMAL)
            )
            nextTop = subtitleTop + subtitleHeight
        }
        return nextTop
    }

    private fun drawCenteredTextBlock(
        canvas: android.graphics.Canvas,
        canvasWidth: Int,
        text: String,
        top: Float,
        width: Int,
        textSize: Float,
        maxLines: Int,
        color: Int,
        typeface: android.graphics.Typeface
    ): Int {
        val textPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = textSize
            this.typeface = typeface
        }
        val layout = android.text.StaticLayout.Builder.obtain(
            text,
            0,
            text.length,
            textPaint,
            width
        )
            .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.02f)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()

        canvas.save()
        canvas.translate((canvasWidth - width) / 2f, top)
        layout.draw(canvas)
        canvas.restore()
        return layout.height
    }

    private fun googleSansTypeface(
        context: Context,
        style: Int
    ): android.graphics.Typeface {
        val font = androidx.core.content.res.ResourcesCompat.getFont(
            context,
            cx.aswin.boxlore.core.designsystem.R.font.google_sans_variable
        )
        return android.graphics.Typeface.create(font, style)
    }

    private fun drawRoundedArtwork(
        canvas: android.graphics.Canvas,
        artwork: android.graphics.Bitmap,
        destination: android.graphics.RectF,
        cornerRadius: Float
    ) {
        val sourceSide = minOf(artwork.width, artwork.height)
        val sourceLeft = (artwork.width - sourceSide) / 2
        val sourceTop = (artwork.height - sourceSide) / 2
        val source = android.graphics.Rect(
            sourceLeft,
            sourceTop,
            sourceLeft + sourceSide,
            sourceTop + sourceSide
        )
        val clipPath = android.graphics.Path().apply {
            addRoundRect(
                destination,
                cornerRadius,
                cornerRadius,
                android.graphics.Path.Direction.CW
            )
        }
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(
            artwork,
            source,
            destination,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
            }
        )
        canvas.restore()

        canvas.drawRoundRect(
            destination,
            cornerRadius,
            cornerRadius,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(52, 255, 255, 255)
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2f
            }
        )
    }
}
