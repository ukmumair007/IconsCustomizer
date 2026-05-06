package com.ukm.app.iconscustomizer

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Process
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import org.w3c.dom.Element
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import android.R as Resources


class MainHook : XposedModule() {

    companion object {
        const val TAG = "UKMTAG"
    }

    private var isFirstLaunch = true
    private var prefManager: SharedPreferences? = null
    private var launcherContext: Context? = null
    private val resolvedCache = ConcurrentHashMap<String, String>()
    private var iconPackPackageName: String = "none"
    private var themeHomeScreenOnly: Boolean = false
    private var isThemedIconEnabled: Boolean = false
    private var isThemedClockEnabled: Boolean = false
    private var isThemeDockFolderEnabled: Boolean = false
    private var dockFolderBgColor: Int = 0
    private var clockWidgetColor: Int = 0
    private var isDockEnabled: Boolean = false
    private var dockFolderOpacity = 200
    private var dockCornerRadius = 60
    private var iconSize: Int = 180

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        super.onModuleLoaded(param)
        prefManager = getRemotePreferences(MainActivity.PREF_NAME)
    }

    override fun onPackageLoaded(packageParam: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(packageParam)
        when (packageParam.packageName) {
            "com.miui.home" -> {
                isThemedIconEnabled = prefManager?.getBoolean("enable_themed_icons", false) ?: false
                isDockEnabled = prefManager?.getBoolean("enable_dock", false) ?: false
                themeHomeScreenOnly =
                    prefManager?.getBoolean("themed_icons_homescreen_only", false) ?: false
                isThemeDockFolderEnabled =
                    prefManager?.getBoolean("theme_dock_folder", false) ?: false
                isThemedClockEnabled =
                    prefManager?.getBoolean("enable_themed_clock", false) ?: false
                iconPackPackageName = prefManager?.getString("icon_pack", "none") ?: "none"
                dockCornerRadius = prefManager?.getInt("dock_corner_radius", 60) ?: 60
                if (isThemeDockFolderEnabled) {
                    dockFolderBgColor = prefManager?.getInt("monet_folder_dock_bg_color", 0) ?: 0
                    dockFolderOpacity = prefManager?.getInt("dock_folder_opacity", 200) ?: 200
                }
                if (isThemedClockEnabled) {
                    clockWidgetColor = prefManager?.getInt("monet_clock_color", 0) ?: 0
                }

                iconSize = prefManager?.getInt("icon_size", 180) ?: 180
                hookLauncher(packageParam)
            }

            else -> return
        }
    }

    private fun hookLauncher(packageParam: XposedModuleInterface.PackageLoadedParam) {
        val classLoader = packageParam.defaultClassLoader
        iconSizeHook(classLoader)
        setLauncherContext(classLoader)
        if (isThemedClockEnabled) {
            launcherClockHook(classLoader)
        }
        if (isThemeDockFolderEnabled) {
            folderHook(classLoader)
        }
        if (isDockEnabled) {
            dockHook(classLoader)
        }
        if (isThemedIconEnabled && iconPackPackageName != "none") {
            iconPackHook(classLoader)
        }

        wallpaperColorChangedHook(classLoader)

        val deviceConfigsClass = XposedHelpers.findClass(
            "sources/com/miui/home/common/device/DeviceConfigs.java",
            classLoader
        )
        val method =
            XposedHelpers.findMethodExact(
                deviceConfigsClass,
                "updateIsDefaultIcon",
                Context::class.java
            )
        hook(method).intercept { chain ->
            chain.proceed()
            if (isThemedIconEnabled && iconPackPackageName != "none") {
                XposedHelpers.setStaticBooleanField(deviceConfigsClass, "sIsDefaultIcon", false)
            }
        }

    }

    private fun wallpaperColorChangedHook(classLoader: ClassLoader) {
        val baseLauncherClass =
            XposedHelpers.findClass("sources/com/miui/home/launcher/BaseLauncher.java", classLoader)
        val onWallpaperColorChangedMethod =
            XposedHelpers.findMethodExact(baseLauncherClass, "onWallpaperColorChanged")
        hook(onWallpaperColorChangedMethod).intercept { chain ->
            chain.proceed()
            val isRestart = getLocalStatePrefs()?.getBoolean("isRestart", false) == true
            if (!isFirstLaunch && !isRestart) {
                val baseLauncher = chain.thisObject
                if (isThemedIconEnabled && iconPackPackageName != "none") {
                    XposedHelpers.callMethod(baseLauncher, "refreshAllAppsIcon")
                }
                if (isThemedClockEnabled) {
                    refreshMaMlWidgets(baseLauncher)
                    refreshGadgets(baseLauncher)
                }
            }
        }
    }

    private fun launcherClockHook(classLoader: ClassLoader) {
        try {
            val factoryClass =
                XposedHelpers.findClass("com.miui.maml.elements.ScreenElementFactory", classLoader)
            val rootClass = XposedHelpers.findClass("com.miui.maml.ScreenElementRoot", classLoader)
            val createInstanceMethod = XposedHelpers.findMethodExact(
                factoryClass,
                "createInstance",
                Element::class.java,
                rootClass
            )
            hook(createInstanceMethod).intercept { param ->
                val domElement = param.args[0] as? Element ?: return@intercept param.proceed()
                val tagName = domElement.tagName
                try {
                    val clockWidgetFgColor = if (clockWidgetColor != 0) {
                        launcherContext?.getColor(clockWidgetColor)
                    } else {
                        launcherContext?.getColor(Resources.color.system_accent1_200)
                    }
                    if (tagName == "DateTime" || tagName == "Time" || tagName == "Text") {
                        val hexColor = String.format("#%08X", clockWidgetFgColor)
                        domElement.removeAttribute("colorExp")
                        domElement.removeAttribute("colorLight")
                        domElement.removeAttribute("colorDark")
                        domElement.setAttribute("color", hexColor)
                    }
//                    else if (tagName == "Rectangle") {
//                        val bgColor = launcherContext?.getColor(R.color.system_accent1_900)
//                        domElement.setAttribute("fillColor", String.format("#%08X", bgColor))
//                    }
                } catch (e: Exception) {
                    Log.e(TAG, "launcherClockHook: ", e)
                    return@intercept param.proceed()
                }
                return@intercept param.proceed()
            }
        } catch (e: Exception) {
            Log.e("MAML Hook", "Setup Error: ", e)
        }
    }

    fun folderHook(classLoader: ClassLoader) {
        val clazz = XposedHelpers.findClass("com.miui.home.model.core.IconCache", classLoader)
        val getDrawableMethod = XposedHelpers.findMethodExact(clazz, "getDrawable", Int::class.java)

        hook(getDrawableMethod).intercept { chain ->
            val id = chain.args[0] as Int

            if (id == 4097 || id == 4098 || id == 4099 || id == 4100 || id == 4104) {
                val shape = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(
                        if (isThemeDockFolderEnabled && dockFolderBgColor != 0) {
                            ContextCompat.getColor(launcherContext!!, dockFolderBgColor)
                        } else {
                            ContextCompat.getColor(
                                launcherContext!!,
                                Resources.color.system_accent1_600
                            )
                        }
                    )
                    alpha = dockFolderOpacity
                    cornerRadius = 66f
                }
                return@intercept shape
            }

            return@intercept chain.proceed()
        }
    }

    fun dockHook(classLoader: ClassLoader) {
        val hotSeatsClass = XposedHelpers.findClass(
            "com.miui.home.launcher.hotseats.HotSeats",
            classLoader
        )

        val hotSeatsScreenClass = XposedHelpers.findClass(
            "com.miui.home.launcher.hotseats.HotSeatsScreenViewContent",
            classLoader
        )

        val onMeasure = XposedHelpers.findMethodExact(
            hotSeatsClass,
            "onMeasure",
            Int::class.java,
            Int::class.java
        )
        hook(onMeasure).intercept { chain ->
            chain.proceed()
            val hotSeats = chain.thisObject as View
            val lp = hotSeats.layoutParams as? ViewGroup.MarginLayoutParams
            lp?.let {
                val dynamicSide = maxOf(10, 160 - (iconSize / 2))
                if (it.leftMargin != dynamicSide || it.rightMargin != dynamicSide) {
                    it.leftMargin = dynamicSide
                    it.rightMargin = dynamicSide
                    hotSeats.layoutParams = it
                }
            }
            val dynamicVerticalPadding = maxOf(0, 120 - (iconSize / 2))
            if (hotSeats.paddingTop != dynamicVerticalPadding || hotSeats.paddingBottom != dynamicVerticalPadding) {
                hotSeats.setPadding(
                    hotSeats.paddingLeft,
                    dynamicVerticalPadding,
                    hotSeats.paddingRight,
                    dynamicVerticalPadding
                )
            }

            return@intercept null
        }

        val applyPillStyle: Consumer<View?> = Consumer { view ->
            if (view == null) return@Consumer
            val pill = GradientDrawable()
            pill.setShape(GradientDrawable.RECTANGLE)
            changeDockBackground(pill)
            pill.setCornerRadius(dockCornerRadius.toFloat())
            view.background = pill
            view.setClipToOutline(true)
        }

        val onFinishInflate: Method =
            XposedHelpers.findMethodExact(hotSeatsScreenClass, "onFinishInflate")
        hook(onFinishInflate).intercept { chain: XposedInterface.Chain? ->
            chain!!.proceed()
            applyPillStyle.accept(chain.thisObject as View?)
        }

        val onWallpaperColorChanged =
            XposedHelpers.findMethodExact(hotSeatsScreenClass, "onWallpaperColorChanged")
        hook(onWallpaperColorChanged).intercept { chain ->
            chain.proceed()
            val isRestart = getLocalStatePrefs()?.getBoolean("isRestart", false) == true
            if (!isFirstLaunch && !isRestart) {
                applyPillStyle.accept(chain.thisObject as View?)
            }
            isFirstLaunch = false
            getLocalStatePrefs()?.edit {
                putBoolean("isRestart", false)
            }
        }
    }

    private fun refreshGadgets(baseLauncher: Any) {
        try {
            val gadgets = XposedHelpers.getObjectField(baseLauncher, "mGadgets") as? ArrayList<*>
            if (gadgets?.isNotEmpty()!!) {
                gadgets.forEach { gadget ->
                    gadget ?: return@forEach
                    val gadgetInfo = XposedHelpers.callMethod(gadget, "getTag")
                    gadgetInfo?.let {
                        val gadgetId = XposedHelpers.getObjectField(it, "mGadgetId") as? Int
                        gadgetId?.let { id ->
                            try {
                                XposedHelpers.callMethod(
                                    baseLauncher,
                                    "reloadGadget",
                                    arrayOf(Int::class.java),
                                    id
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "refreshGadgets: ", e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log(Log.ERROR, TAG, "Error refreshing gadgets: ${e.message}")
        }
    }

    private fun refreshMaMlWidgets(baseLauncher: Any) {
        try {
            try {
                val mamlUtilsClass = XposedHelpers.findClass(
                    "com.miui.launcher.utils.MamlUtils",
                    baseLauncher.javaClass.classLoader
                )
                XposedHelpers.callStaticMethod(mamlUtilsClass, "clearMamlCache")
            } catch (e: Exception) {
                Log.e(TAG, "Could not clear MAML cache", e)
            }
            val mMaMlViews =
                XposedHelpers.getObjectField(baseLauncher, "mMaMlViews") as? ArrayList<*>
            if (mMaMlViews?.isNotEmpty()!!) {
                mMaMlViews.forEach { widget ->
                    if (widget != null) {
                        try {
                            XposedHelpers.setBooleanField(widget, "mThemeApplied", false)
                            XposedHelpers.callMethod(
                                widget,
                                "onUpgrade",
                                arrayOf(
                                    Int::class.java,
                                    Int::class.java
                                ),
                                0, 0
                            )
                        } catch (innerE: Exception) {
                            Log.e(TAG, "Failed to force rebuild MAML widget", innerE)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching mMaMlViews: ${e.message}")
        }
    }

    fun changeDockBackground(drawable: GradientDrawable) {
        val currentNightMode =
            launcherContext!!.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkModeEnabled = currentNightMode == Configuration.UI_MODE_NIGHT_YES

        if (!isThemeDockFolderEnabled) {
            if (isDarkModeEnabled) {
                drawable.setColor("#40000000".toColorInt())
            } else {
                drawable.setColor("#80FFFFFF".toColorInt())
            }
        } else {
            drawable.setColor(
                if (dockFolderBgColor != 0) {
                    ContextCompat.getColor(launcherContext!!, dockFolderBgColor)
                } else {
                    ContextCompat.getColor(
                        launcherContext!!,
                        Resources.color.system_accent1_600
                    )
                }

            )
            drawable.alpha = dockFolderOpacity
        }
    }

    private fun iconSizeHook(classLoader: ClassLoader) {
        val iconConfigClass = XposedHelpers.findClass(
            $$"com.miui.home.common.gridconfig.GridConfig$IconConfig",
            classLoader
        )
        val getIconSizeMethod = XposedHelpers.findMethodExact(iconConfigClass, "getIconSize")
        hook(getIconSizeMethod).intercept { _ ->
            return@intercept iconSize
        }
    }

    private fun getLocalStatePrefs(): SharedPreferences? {
        return launcherContext?.getSharedPreferences(
            "ukm_module_internal_state",
            Context.MODE_PRIVATE
        )
    }

    private fun setLauncherContext(classLoader: ClassLoader) {
        val appClass = XposedHelpers.findClass("com.miui.home.launcher.Application", classLoader)
        val onCreateMethodApp = XposedHelpers.findMethodExact(appClass, "onCreate")

        hook(onCreateMethodApp).intercept { chain ->
            chain.proceed()
            if (launcherContext == null) {
                try {
                    launcherContext = chain.thisObject as? Context
                    if (launcherContext != null) {
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(ctx: Context, intent: Intent) {
                                if (intent.action == "com.ukm.app.RELOAD_ICONS") {
                                    Log.i(TAG, "Reload Broadcast Received! Restarting Launcher...")
                                    getLocalStatePrefs()?.edit {
                                        putBoolean("isRestart", true)
                                    }
                                    Process.killProcess(Process.myPid())
                                }
                            }
                        }
                        val filter = IntentFilter("com.ukm.app.RELOAD_ICONS")
                        launcherContext!!.registerReceiver(
                            receiver,
                            filter,
                            Context.RECEIVER_EXPORTED
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error:", e)
                }
            }
            return@intercept null
        }
    }

    private fun iconPackHook(classLoader: ClassLoader) {
        val drawableInfoClass =
            XposedHelpers.findClass("com.miui.home.model.core.DrawableInfo", classLoader)
        val itemInfoWithIconAndMessageClass = XposedHelpers.findClass(
            "com.miui.home.model.api.ItemInfoWithIconAndMessage",
            classLoader
        )
        val applyTo =
            XposedHelpers.findMethodExact(
                drawableInfoClass,
                "applyTo",
                itemInfoWithIconAndMessageClass
            )

        hook(applyTo).intercept { chain ->
            try {
                val itemInfoWithIconAndMessage = chain.args[0]
                val iconType =
                    XposedHelpers.callMethod(itemInfoWithIconAndMessage, "getIconType") as? Int
                val isHomeScreenItem = itemInfoWithIconAndMessage.javaClass.name == "com.miui.home.launcher.ShortcutInfo"
                val shouldThemeIcon = !themeHomeScreenOnly || isHomeScreenItem
                if (shouldThemeIcon && iconType != 8) {
                    val originalIcon =
                        XposedHelpers.getObjectField(chain.thisObject, "icon") as Drawable
                    val componentName =
                        XposedHelpers.callMethod(chain.args[0], "getComponentInfo") as ComponentName
                    val customIcon = getCustomIcon(componentName, originalIcon)
                    val iconMask = XposedHelpers.getObjectField(chain.thisObject, "enableIconMask")
                    try {
                        val setIconDrawable =
                            XposedHelpers.findMethodExact(
                                itemInfoWithIconAndMessage::class.java, "setIconDrawable",
                                Drawable::class.java
                            )
                        val setEnableIconMask = XposedHelpers.findMethodExact(
                            itemInfoWithIconAndMessage::class.java, "setEnableIconMask",
                            Int::class.java
                        )
                        setIconDrawable.invoke(itemInfoWithIconAndMessage, customIcon)
                        setEnableIconMask.invoke(itemInfoWithIconAndMessage, iconMask)
                    } catch (_: Exception) {
                        return@intercept chain.proceed()
                    }
                    return@intercept null
                }
                return@intercept chain.proceed()
            } catch (e: Exception) {
                Log.e(TAG, "hookLauncher: ", e)
                return@intercept chain.proceed()
            }
        }
    }

    private fun getCustomIcon(
        componentName: ComponentName,
        originalIcon: Drawable
    ): Drawable {
        if (launcherContext != null) {
            val exactComponentString =
                "ComponentInfo{${componentName.packageName}/${componentName.className}}"
            val manualOverrideKey = "custom_icon_${iconPackPackageName}_$exactComponentString"
            val manualDrawableName = prefManager?.getString(manualOverrideKey, null)

            if (!manualDrawableName.isNullOrEmpty()) {
                val manualCustomDrawable = IconPackHelper.loadIcon(
                    launcherContext!!,
                    iconPackPackageName,
                    manualDrawableName
                )
                if (manualCustomDrawable != null) {
                    manualCustomDrawable.bounds = originalIcon.bounds
                    return getCustomColoredDrawableIcon(manualCustomDrawable)
                }
            }

            val appFilterMap =
                IconPackHelper.getAppFilterMap(launcherContext!!, iconPackPackageName)
            if (appFilterMap.isEmpty()) return originalIcon

            val drawableName = getBestMatchDrawable(componentName, appFilterMap)
            if (drawableName != null) {
                val customDrawable =
                    IconPackHelper.loadIcon(launcherContext!!, iconPackPackageName, drawableName)
                if (customDrawable != null) {
                    customDrawable.bounds = originalIcon.bounds
                    return getCustomColoredDrawableIcon(customDrawable)
                }
            }
        }
        return originalIcon
    }

    private fun getCustomColoredDrawableIcon(icon: Drawable): Drawable {
        val isMonetEnabled = prefManager?.getBoolean("enable_monet_colors", false) ?: false
        try {
            if (isMonetEnabled && launcherContext != null) {
                val savedBgResId = prefManager?.getInt("monet_bg_color", 0) ?: 0
                val savedFgResId = prefManager?.getInt("monet_fg_color", 0) ?: 0

                var monetBackground: Int? = null
                var monetForeground: Int? = null

                if (savedBgResId != 0) monetBackground =
                    ContextCompat.getColor(launcherContext!!, savedBgResId)
                if (savedFgResId != 0) {
                    monetForeground = ContextCompat.getColor(launcherContext!!, savedFgResId)
                }

                val bakedIcon = IconPackHelper.putColorIntoDrawable(
                    launcherContext!!,
                    icon,
                    monetBackground,
                    monetForeground
                ) ?: icon
                return bakedIcon
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to theme custom icon", e)
            return icon
        }
        return icon
    }

    private fun getBestMatchDrawable(
        component: ComponentName,
        appFilterMap: Map<String, String>
    ): String? {
        val exactString = "ComponentInfo{${component.packageName}/${component.className}}"

        val cachedMatch = resolvedCache[exactString]
        if (cachedMatch != null) {
            return cachedMatch.ifEmpty { null }
        }

        var match = appFilterMap[exactString]
        if (match == null) {
            val packagePrefix = "ComponentInfo{${component.packageName}/"
            for ((key, value) in appFilterMap) {
                if (key.startsWith(packagePrefix)) {
                    match = value
                    break
                }
            }
        }
        resolvedCache[exactString] = match ?: ""
        return match
    }
}