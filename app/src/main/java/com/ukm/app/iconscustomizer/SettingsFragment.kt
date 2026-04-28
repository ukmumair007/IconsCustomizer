package com.ukm.app.iconscustomizer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.ukm.app.iconscustomizer.MainActivity.Companion.PREF_NAME
import io.github.libxposed.service.XposedService

class SettingsFragment : Fragment(), App.ServiceStateListener {

    private var mService: XposedService? = null
    private var isUpdatingUI = false
    data class IconPackInfo(val name: String, val packageName: String)
    private var installedIconPacks = listOf<IconPackInfo>()
    lateinit var switchEnableTheming: MaterialSwitch
    lateinit var switchHomescreenOnly: MaterialSwitch
    lateinit var rowIconPack: LinearLayout
    lateinit var rowApplyCustom: LinearLayout
    lateinit var sliderIconSize: Slider
    lateinit var switchMonet: MaterialSwitch
    lateinit var rowMonetFg: LinearLayout
    lateinit var rowMonetBg: LinearLayout
    lateinit var switchEnableDock: MaterialSwitch
    lateinit var switchEnableMonetDockFolder: MaterialSwitch
    lateinit var rowDockBg: LinearLayout
    lateinit var sliderDockOpacity: Slider
    lateinit var switchMonetClock: MaterialSwitch
    lateinit var rowClockColor: LinearLayout

    private val colorPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val colorId = result.data?.getIntExtra("RETURNED_COLOR_ID", 0) ?: 0
            val targetKey = result.data?.getStringExtra("RETURNED_TARGET_KEY")

            view?.let { v ->
                when (targetKey) {
                    "monet_fg_color" -> updateColorPreview(v, R.id.img_preview_fg, colorId)
                    "monet_bg_color" -> updateColorPreview(v, R.id.img_preview_bg, colorId)
                    "monet_folder_dock_bg_color" -> updateColorPreview(v, R.id.img_preview_dock_bg, colorId)
                    "monet_clock_color" -> updateColorPreview(v, R.id.img_preview_clock, colorId)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = requireActivity().findViewById<MaterialToolbar>(R.id.materialToolbar)
        toolbar.title = "Home"
        installedIconPacks = getInstalledIconPacks(requireContext())
        setupInteractions(view)
    }

    override fun onStart() {
        super.onStart()
        App.addServiceStateListener(this)
    }

    override fun onStop() {
        App.removeServiceStateListener(this)
        super.onStop()
    }

    override fun onServiceStateChanged(service: XposedService?) {
        Log.i(MainHook.TAG, "Service State Changed")
        this.mService = service
        requireActivity().runOnUiThread {
            applyServiceStateToUI(view)
        }
    }

    private fun setupInteractions(view: View) {
        switchEnableTheming = view.findViewById(R.id.switch_enable_theming)
        switchHomescreenOnly = view.findViewById(R.id.switch_homescreen_only)
        rowIconPack = view.findViewById(R.id.row_icon_pack)
        rowApplyCustom = view.findViewById(R.id.row_apply_custom)
        sliderIconSize = view.findViewById(R.id.slider_icon_size)

        switchEnableTheming.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUI) return@setOnCheckedChangeListener
            if (mService != null) {
                UIHelpers.pushRemotePref("enable_themed_icons", isChecked)
                applyServiceStateToUI(view)
                UIHelpers.restartLauncher(requireContext())
            }
        }

        switchHomescreenOnly.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUI) return@setOnCheckedChangeListener
            if (mService != null) {
                UIHelpers.pushRemotePref("themed_icons_homescreen_only", isChecked)
                UIHelpers.restartLauncher(requireContext())
            }
        }

        rowIconPack.setOnClickListener {
            showIconPackPicker(view)
        }

        rowApplyCustom.setOnClickListener {
            val prefs = mService?.getRemotePreferences(PREF_NAME)
            val selectedPack = prefs?.getString("icon_pack", "none")
            if (selectedPack != null && selectedPack != "none") {
                val fragment = AllAppsFragment().apply {
                    arguments = Bundle().apply {
                        putString("EXTRA_ICON_PACK", selectedPack)
                    }
                }
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        }

        sliderIconSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser && mService != null) {
                UIHelpers.pushRemotePref("icon_size", value.toInt())
            }
        }
        sliderIconSize.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                UIHelpers.restartLauncher(requireContext())
            }
        })

        switchMonet = view.findViewById(R.id.switch_enable_monet)
        rowMonetFg = view.findViewById(R.id.row_monet_fg)
        rowMonetBg = view.findViewById(R.id.row_monet_bg)

        switchMonet.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUI) return@setOnCheckedChangeListener
            if (mService != null) {
                UIHelpers.pushRemotePref("enable_monet_colors", isChecked)
                applyServiceStateToUI(view)
                UIHelpers.restartLauncher(requireContext())
            }
        }

        rowMonetFg.setOnClickListener {
            val intent = Intent(requireContext(), ColorPickerActivity::class.java).apply {
                putExtra("EXTRA_TITLE", "Pick Foreground Color")
                putExtra("EXTRA_TARGET_KEY", "monet_fg_color")
                putExtra("EXTRA_TYPE_KEY", "selected_fg_color_name")
            }
            colorPickerLauncher.launch(intent)
        }

        rowMonetBg.setOnClickListener {
            val intent = Intent(requireContext(), ColorPickerActivity::class.java).apply {
                putExtra("EXTRA_TITLE", "Pick Background Color")
                putExtra("EXTRA_TARGET_KEY", "monet_bg_color")
                putExtra("EXTRA_TYPE_KEY", "selected_bg_color_name")
            }
            colorPickerLauncher.launch(intent)
        }

        switchEnableDock = view.findViewById(R.id.switch_enable_dock)
        switchEnableMonetDockFolder = view.findViewById(R.id.switch_monet_dock_folder)
        rowDockBg = view.findViewById(R.id.row_dock_bg)
        sliderDockOpacity = view.findViewById(R.id.slider_dock_opacity)

        switchEnableDock.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUI) return@setOnCheckedChangeListener
            if (mService != null) {
                UIHelpers.pushRemotePref("enable_dock", isChecked)
                UIHelpers.restartLauncher(requireContext())
            }
        }

        switchEnableMonetDockFolder.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUI) return@setOnCheckedChangeListener
            if (mService != null) {
                UIHelpers.pushRemotePref("theme_dock_folder", isChecked)
                applyServiceStateToUI(view)
                UIHelpers.restartLauncher(requireContext())
            }
        }

        rowDockBg.setOnClickListener {
            val intent = Intent(requireContext(), ColorPickerActivity::class.java).apply {
                putExtra("EXTRA_TITLE", "Pick Dock Background")
                putExtra("EXTRA_TARGET_KEY", "monet_folder_dock_bg_color")
                putExtra("EXTRA_TYPE_KEY", "selected_monet_folder_dock_bg_color")
            }
            colorPickerLauncher.launch(intent)
        }

        sliderDockOpacity.addOnChangeListener { _, value, fromUser ->
            if (fromUser && mService != null) {
                UIHelpers.pushRemotePref("dock_folder_opacity", value.toInt())
            }
        }
        sliderDockOpacity.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                UIHelpers.restartLauncher(requireContext())
            }
        })

        switchMonetClock = view.findViewById(R.id.switch_monet_clock)
        rowClockColor = view.findViewById(R.id.row_clock_color)

        switchMonetClock.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingUI) return@setOnCheckedChangeListener
            if (mService != null) {
                UIHelpers.pushRemotePref("enable_themed_clock", isChecked)
                applyServiceStateToUI(view)
                UIHelpers.restartLauncher(requireContext())
            }
        }

        rowClockColor.setOnClickListener {
            val intent = Intent(requireContext(), ColorPickerActivity::class.java).apply {
                putExtra("EXTRA_TITLE", "Pick Clock Color")
                putExtra("EXTRA_TARGET_KEY", "monet_clock_color")
                putExtra("EXTRA_TYPE_KEY", "selected_monet_clock_color")
            }
            colorPickerLauncher.launch(intent)
        }

        view.findViewById<MaterialButton>(R.id.btn_restart_launcher).setOnClickListener {
            if (mService != null) {
                val success = UIHelpers.restartLauncher(requireContext())
                Toast.makeText(context, if (success) "Launcher Restarted" else "Error Restarting", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyServiceStateToUI(view: View?) {
        if (view == null) return

        if (mService == null) {
            context?.let {
                Toast.makeText(it, "XposedService Not Started", Toast.LENGTH_SHORT).show()
            }
            setUiEnabled(view, false)
            return
        }
        setUiEnabled(view, true)

        val prefs = mService!!.getRemotePreferences(PREF_NAME)

        val isThemingEnabled = prefs.getBoolean("enable_themed_icons", false)
        val isMonetCustomization = prefs.getBoolean("enable_monet_colors", false)
        val isThemeDockFolder = prefs.getBoolean("theme_dock_folder", false)
        val isThemeClockWidget = prefs.getBoolean("enable_themed_clock", false)
        val selectedPackId = prefs.getString("icon_pack", "none") ?: "none"

        val iconSize = prefs.getInt("icon_size", 180).toFloat()
        val dockOpacity = prefs.getInt("dock_folder_opacity", 200).toFloat()

        isUpdatingUI = true

        switchEnableTheming.isChecked = isThemingEnabled
        switchHomescreenOnly.isChecked = prefs.getBoolean("themed_icons_homescreen_only", false)
        switchMonet.isChecked = isMonetCustomization
        switchEnableDock.isChecked = prefs.getBoolean("enable_dock", false)
        switchEnableMonetDockFolder.isChecked = isThemeDockFolder
        switchMonetClock.isChecked = isThemeClockWidget
        sliderIconSize.value = iconSize
        sliderDockOpacity.value = dockOpacity

        isUpdatingUI = false

        val selectedPackName = if (selectedPackId == "none") "None" else installedIconPacks.find { it.packageName == selectedPackId }?.name ?: "Unknown"
        view.findViewById<TextView>(R.id.tv_selected_icon_pack).text = selectedPackName

        updateColorPreview(view, R.id.img_preview_fg, prefs.getInt("monet_fg_color", 0))
        updateColorPreview(view, R.id.img_preview_bg, prefs.getInt("monet_bg_color", 0))
        updateColorPreview(view, R.id.img_preview_dock_bg, prefs.getInt("monet_folder_dock_bg_color", 0))
        updateColorPreview(view, R.id.img_preview_clock, prefs.getInt("monet_clock_color", 0))

        val vTheming = if (isThemingEnabled) View.VISIBLE else View.GONE
        switchHomescreenOnly.visibility = vTheming
        view.findViewById<View>(R.id.div_homescreen_only).visibility = vTheming
        view.findViewById<View>(R.id.row_icon_pack).visibility = vTheming
        view.findViewById<View>(R.id.div_icon_pack).visibility = vTheming
        view.findViewById<View>(R.id.title_monet_colors).visibility = vTheming
        view.findViewById<View>(R.id.card_monet_colors).visibility = vTheming

        val vApplyCustom = if (isThemingEnabled && selectedPackId != "none") View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.row_apply_custom).visibility = vApplyCustom
        view.findViewById<View>(R.id.div_apply_custom).visibility = vApplyCustom

        val vMonetColors = if (isMonetCustomization) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.row_monet_fg).visibility = vMonetColors
        view.findViewById<View>(R.id.div_monet_colors).visibility = vMonetColors
        view.findViewById<View>(R.id.row_monet_bg).visibility = vMonetColors
        view.findViewById<View>(R.id.div_monet_bg).visibility = vMonetColors

        val vDockMonet = if (isThemeDockFolder) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.row_dock_bg).visibility = vDockMonet
        view.findViewById<View>(R.id.div_dock_bg).visibility = vDockMonet

        val vDockFolder = if (isThemeDockFolder) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.row_dock_opacity).visibility = vDockFolder
        view.findViewById<View>(R.id.div_dock_folder).visibility = vDockFolder

        val vClock = if (isThemeClockWidget) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.row_clock_color).visibility = vClock
        view.findViewById<View>(R.id.div_clock_color).visibility = vClock
    }

    private fun showIconPackPicker(view: View) {
        val displayNames = arrayOf("None") + installedIconPacks.map { it.name }.toTypedArray()
        val packageNames = arrayOf("none") + installedIconPacks.map { it.packageName }.toTypedArray()

        val prefs = mService?.getRemotePreferences(PREF_NAME)
        val currentSelection = prefs?.getString("icon_pack", "none")
        val currentIndex = packageNames.indexOf(currentSelection).takeIf { it >= 0 } ?: 0

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Choose Icon Pack")
            .setSingleChoiceItems(displayNames, currentIndex) { dialog, which ->
                val selectedPkg = packageNames[which]
                UIHelpers.pushRemotePref("icon_pack", selectedPkg)
                applyServiceStateToUI(view)
                UIHelpers.restartLauncher(requireContext())
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateColorPreview(view: View, imageViewId: Int, savedColor: Int) {
        val imageView = view.findViewById<ImageView>(imageViewId) ?: return
        val resolvedColor = try {
            if (savedColor != 0) ContextCompat.getColor(requireContext(), savedColor) else Color.TRANSPARENT
        } catch (_: Exception) {
            Color.TRANSPARENT
        }
        val colorIcon = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(12f)
            setColor(resolvedColor)
            setStroke(dpToPx(1f).toInt(), "#33000000".toColorInt())
        }
        imageView.setImageDrawable(colorIcon)
    }

    private fun setUiEnabled(view: View, isEnabled: Boolean) {
        switchEnableTheming.parent?.requestLayout()
        view.isEnabled = isEnabled
    }

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

    private fun getInstalledIconPacks(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val iconPacks = mutableListOf<IconPackInfo>()
        val actions = arrayOf("com.novalauncher.THEME", "org.adw.launcher.THEMES")
        for (action in actions) {
            val intent = Intent(action)
            val resolveInfos = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
            for (info in resolveInfos) {
                val packageName = info.activityInfo.packageName
                val label = info.loadLabel(pm).toString()
                if (iconPacks.none { it.packageName == packageName }) {
                    iconPacks.add(IconPackInfo(label, packageName))
                }
            }
        }
        return iconPacks.sortedBy { it.name }
    }
}