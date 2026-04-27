package com.ukm.app.iconscustomizer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import org.xmlpull.v1.XmlPullParser

object IconPackHelper {

    private const val TAG = "UKMTAG"
    @Volatile
    private var cachedAppFilterMap: Map<String, String>? = null
    @Volatile
    private var currentIconPack: String? = null

    @SuppressLint("QueryPermissionsNeeded", "DiscouragedApi")
    fun getAppFilterMap(context: Context, iconPackPackageName: String): Map<String, String> {
        synchronized(this) {
            if (iconPackPackageName == currentIconPack && cachedAppFilterMap != null) {
                return cachedAppFilterMap!!
            }

            val iconMap = mutableMapOf<String, String>()
            try {
                val iconPackContext =
                    context.createPackageContext(
                        iconPackPackageName,
                        Context.CONTEXT_IGNORE_SECURITY
                    )
                val resId =
                    iconPackContext.resources.getIdentifier("appfilter", "xml", iconPackPackageName)

                if (resId == 0) {
                    Log.e(TAG, "appfilter.xml not found in $iconPackPackageName")
                    return iconMap
                }

                iconPackContext.resources.getXml(resId).use { parser ->
                    var eventType = parser.eventType

                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                            val component = parser.getAttributeValue(null, "component")?.trim()
                            val drawable = parser.getAttributeValue(null, "drawable")?.trim()

                            if (!component.isNullOrEmpty() && !drawable.isNullOrEmpty()) {
                                iconMap[component] = drawable
                            }
                        }
                        eventType = parser.next()
                    }
                }

                cachedAppFilterMap = iconMap
                currentIconPack = iconPackPackageName

            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse appfilter: ${e.message}")
            }
            return iconMap
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables", "DiscouragedApi")
    fun loadIcon(context: Context, iconPackPackageName: String, drawableName: String): Drawable? {
        return try {
            val iconPackContext =
                context.createPackageContext(iconPackPackageName, Context.CONTEXT_IGNORE_SECURITY)
            val resId = iconPackContext.resources.getIdentifier(
                drawableName,
                "drawable",
                iconPackPackageName
            )
            if (resId != 0) {
                iconPackContext.getDrawable(resId)?.mutate()
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load icon $drawableName: ${e.message}")
            null
        }
    }


    @SuppressLint("DiscouragedApi")
    fun getAllIconsFromPack(context: Context, iconPackPackageName: String): List<String> {
        val icons = mutableSetOf<String>()
        try {
            val iconPackContext =
                context.createPackageContext(iconPackPackageName, Context.CONTEXT_IGNORE_SECURITY)
            val resId =
                iconPackContext.resources.getIdentifier("drawable", "xml", iconPackPackageName)

            if (resId != 0) {
                iconPackContext.resources.getXml(resId).use { parser ->
                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                            val drawable = parser.getAttributeValue(null, "drawable")
                            if (drawable != null) icons.add(drawable)
                        }
                        eventType = parser.next()
                    }
                }
            }

            if (icons.isEmpty()) {
                val map = getAppFilterMap(context, iconPackPackageName)
                icons.addAll(map.values)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all icons: ${e.message}")
        }

        return icons.toList().sorted()
    }

    private fun applyCustomColors(icon: Drawable?, bgColorInt: Int?, fgColorInt: Int?): Drawable? {
        if (icon == null) return null
        val mutatedIcon = icon.mutate()

        if (mutatedIcon is AdaptiveIconDrawable) {
            if (bgColorInt != null) {
                val background = mutatedIcon.background?.mutate()
                background?.colorFilter =
                    BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                        bgColorInt,
                        BlendModeCompat.SRC_ATOP
                    )
            }
            if (fgColorInt != null) {
                val foreground = mutatedIcon.foreground?.mutate()
                foreground?.colorFilter =
                    BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                        fgColorInt,
                        BlendModeCompat.SRC_ATOP
                    )
            }
            return mutatedIcon
        }

        if (mutatedIcon is LayerDrawable) {
            if (bgColorInt != null && mutatedIcon.numberOfLayers > 0) {
                val bgLayer = mutatedIcon.getDrawable(0).mutate()
                bgLayer.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                    bgColorInt,
                    BlendModeCompat.SRC_ATOP
                )
                mutatedIcon.setDrawable(0, bgLayer)
            }
            if (fgColorInt != null && mutatedIcon.numberOfLayers > 1) {
                val fgLayer = mutatedIcon.getDrawable(1).mutate()
                fgLayer.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                    fgColorInt,
                    BlendModeCompat.SRC_ATOP
                )
                mutatedIcon.setDrawable(1, fgLayer)
            }
            return mutatedIcon
        }

        if (fgColorInt != null) {
            mutatedIcon.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                fgColorInt,
                BlendModeCompat.SRC_ATOP
            )
        }

        return mutatedIcon
    }


    fun putColorIntoDrawable(
        context: Context,
        icon: Drawable?,
        bgColorInt: Int?,
        fgColorInt: Int?
    ): Drawable? {
        if (icon == null) return null
        val tintedDrawable = applyCustomColors(icon, bgColorInt, fgColorInt) ?: return null
        val width = if (tintedDrawable.intrinsicWidth > 0) tintedDrawable.intrinsicWidth else 192
        val height = if (tintedDrawable.intrinsicHeight > 0) tintedDrawable.intrinsicHeight else 192
        tintedDrawable.setBounds(0, 0, width, height)
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        tintedDrawable.draw(canvas)
        return bitmap.toDrawable(context.resources)
    }
}