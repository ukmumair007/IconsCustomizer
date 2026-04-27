package com.ukm.app.iconscustomizer

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.ukm.app.iconscustomizer.MainActivity.Companion.PREF_NAME

class ColorPickerActivity : AppCompatActivity() {

    private lateinit var targetPreferenceKey: String
    private lateinit var colorTypeKey: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_color_picker)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.materialToolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        val dialogTitle = intent.getStringExtra("EXTRA_TITLE") ?: "Choose Color"
        targetPreferenceKey = intent.getStringExtra("EXTRA_TARGET_KEY") ?: return finish()
        colorTypeKey = intent.getStringExtra("EXTRA_TYPE_KEY") ?: return finish()
        supportActionBar?.title = dialogTitle
        setupColorGrid()
        setupClearButton()
    }

    private fun setupClearButton() {
        val btnClear = findViewById<MaterialButton>(R.id.btnClearColor)
        btnClear.setOnClickListener {
            UIHelpers.pushRemotePref(targetPreferenceKey, 0)
            UIHelpers.pushLocalPref(this, targetPreferenceKey, 0)
            UIHelpers.pushRemotePref(colorTypeKey, "default")
            UIHelpers.pushLocalPref(this, colorTypeKey, "default")
            UIHelpers.restartLauncher(this)
            Toast.makeText(this, "Color reset to default", Toast.LENGTH_SHORT).show()
            finishWithResult(0)
        }
    }

    private fun setupColorGrid() {
        val gridContainer = findViewById<FrameLayout>(R.id.gridContainer)
        val tvCurrentName = findViewById<TextView>(R.id.tvCurrentColorName)
        val dialogSelectedColorName = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
            .getString(colorTypeKey, "default")
        tvCurrentName.text =
            if (dialogSelectedColorName == "default") "Default" else dialogSelectedColorName
        val density = resources.displayMetrics.density
        fun dpToPx(dp: Int): Int = (dp * density).toInt()
        val gridLayout = GridLayout(this).apply {
            columnCount = 4
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(16))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        val categories = listOf("accent1", "accent2", "accent3", "neutral1")
        val displayNames = listOf("Accent 1", "Accent 2", "Accent 3", "Neutral 1")
        val shades = listOf(0, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)

        val cellWidth = dpToPx(62)
        val cellHeight = dpToPx(50)
        val cellMargin = dpToPx(8)

        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)

        for (name in displayNames) {
            val headerText = TextView(this).apply {
                text = name
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(16), 0, dpToPx(16))
            }
            gridLayout.addView(headerText)
        }

        for (shade in shades) {
            for (category in categories) {
                val resourceName = "system_${category}_$shade"
                val colorResId = resources.getIdentifier(resourceName, "color", "android")
                val colorType = "${category}_$shade"

                val cellContainer = FrameLayout(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = cellWidth
                        height = cellHeight
                        setMargins(cellMargin, cellMargin, cellMargin, cellMargin)
                    }
                    isClickable = true
                    isFocusable = true
                    foreground = ContextCompat.getDrawable(context, outValue.resourceId)
                }

                val backgroundShape = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(12).toFloat()
                    if (colorResId != 0) {
                        setColor(ContextCompat.getColor(this@ColorPickerActivity, colorResId))
                        if (colorType == dialogSelectedColorName) {
                            setStroke(
                                dpToPx(3),
                                ContextCompat.getColor(
                                    this@ColorPickerActivity,
                                    android.R.color.tab_indicator_text
                                )
                            )
                        }
                    } else {
                        setColor(Color.TRANSPARENT)
                    }
                }
                cellContainer.background = backgroundShape

                val textView = TextView(this).apply {
                    gravity = Gravity.CENTER
                    textSize = 12f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    if (colorResId != 0) {
                        text = shade.toString()
                        setTextColor(if (shade <= 400) "#DE000000".toColorInt() else Color.WHITE)
                    } else {
                        text = "-"
                        setTextColor(Color.DKGRAY)
                    }
                }
                cellContainer.addView(textView)

                if (colorResId != 0) {
                    cellContainer.setOnClickListener {
                        UIHelpers.pushLocalPref(this, targetPreferenceKey, colorResId)
                        UIHelpers.pushLocalPref(this, colorTypeKey, colorType)
                        UIHelpers.pushRemotePref(targetPreferenceKey, colorResId)
                        UIHelpers.pushRemotePref(colorTypeKey, colorType)

                        UIHelpers.restartLauncher(this)
                        Toast.makeText(this, "Color applied!", Toast.LENGTH_SHORT).show()

                        finishWithResult(colorResId)
                    }
                }
                gridLayout.addView(cellContainer)
            }
        }
        gridContainer.addView(gridLayout)
    }

    private fun finishWithResult(savedColorId: Int) {
        val resultIntent = Intent().apply {
            putExtra("RETURNED_COLOR_ID", savedColorId)
            putExtra("RETURNED_TARGET_KEY", targetPreferenceKey)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}