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
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import org.w3c.dom.Element
import java.util.concurrent.ConcurrentHashMap
import android.R as Resources


class MainHook : XposedModule() {

    companion object {
        const val TAG = "UKMTAG"
    }

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

        val clazz = XposedHelpers.findClass(
            "sources/com/miui/home/common/device/DeviceConfigs.java",
            classLoader
        )
        val method =
            XposedHelpers.findMethodExact(clazz, "updateIsDefaultIcon", Context::class.java)
        hook(method).intercept { chain ->
            chain.proceed()
            if (isThemedIconEnabled && iconPackPackageName != "none") {
                XposedHelpers.setStaticBooleanField(clazz, "sIsDefaultIcon", false)
            }
//            val sIsDefaultIcon = XposedHelpers.getStaticBooleanField(clazz, "sIsDefaultIcon")
//            Log.i(TAG, "updateIsDefaultIcon Called $sIsDefaultIcon")
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
        hook(getDrawableMethod).intercept { _ ->
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
    }

    fun dockHook(classLoader: ClassLoader) {
        val hotSeatsClass =
            XposedHelpers.findClass("com.miui.home.launcher.hotseats.HotSeats", classLoader)
        val initContentMethod = XposedHelpers.findMethodExact(hotSeatsClass, "initContent")
        hook(initContentMethod).intercept { chain ->
//            Log.i(TAG, "Dock Init Executed")
            val hotSeatsView = chain.thisObject as? FrameLayout
            if (hotSeatsView != null) {
                try {
                    hotSeatsView.removeAllViews()
                    val context = hotSeatsView.context
                    val inflater = LayoutInflater.from(context)
                    val deviceTypeUtilsClass = XposedHelpers.findClass(
                        "com.miui.home.common.utils.DeviceTypeUtils",
                        classLoader
                    )
                    val isFoldDevice = XposedHelpers.callStaticMethod(
                        deviceTypeUtilsClass,
                        "isFoldDevice"
                    ) as Boolean

                    if (isFoldDevice) {
                        val listLayoutId = context.resources.getIdentifier(
                            "hotseats_content_list",
                            "layout",
                            "com.miui.home"
                        )

                        val hotSeatsListContent =
                            inflater.inflate(listLayoutId, null) as RecyclerView
                        XposedHelpers.setObjectField(
                            hotSeatsView,
                            "mListContent",
                            hotSeatsListContent
                        )
                        XposedHelpers.callMethod(
                            hotSeatsListContent,
                            "setupViews",
                            chain.thisObject
                        )
                    }

                    val screenLayoutId = context.resources.getIdentifier(
                        "hotseats_content_screen",
                        "layout",
                        "com.miui.home"
                    )
                    val hotSeatsScreenContent = inflater.inflate(screenLayoutId, null) as ViewGroup
                    hotSeatsScreenContent.setBackgroundColor(launcherContext!!.getColor(Resources.color.system_accent1_600))
                    hotSeatsScreenContent.clipToPadding = true
                    hotSeatsScreenContent.clipChildren = true

                    XposedHelpers.setObjectField(
                        hotSeatsView,
                        "mScreenViewContent",
                        hotSeatsScreenContent
                    )
                    XposedHelpers.callMethod(
                        hotSeatsScreenContent,
                        "setupViews",
                        chain.thisObject
                    )

                    XposedHelpers.callMethod(chain.thisObject, "updateContent")
                    val dynamicMargin = maxOf(10, 160 - (iconSize / 2))
                    val params = hotSeatsScreenContent.layoutParams as? ViewGroup.MarginLayoutParams
                    params?.width = ViewGroup.LayoutParams.MATCH_PARENT
                    params?.height = iconSize + 80
                    params?.leftMargin = dynamicMargin
                    params?.rightMargin = dynamicMargin
                    params?.bottomMargin = dynamicMargin
                    hotSeatsScreenContent.setPadding(10, 10, 10, 10)
                    changeDockBackground(hotSeatsScreenContent)
                    hotSeatsScreenContent.layoutParams = params
                    return@intercept null
                } catch (e: Exception) {
                    Log.e(TAG, "Error recreating initContent", e)
                    return@intercept chain.proceed()
                }
            }
            return@intercept chain.proceed()
        }
    }

    fun changeDockBackground(viewGroup: ViewGroup) {
        val currentNightMode =
            viewGroup.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkModeEnabled = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        val roundedBackground = GradientDrawable()
        roundedBackground.apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 66f
        }
        if (!isThemeDockFolderEnabled) {
            if (isDarkModeEnabled) {
                roundedBackground.setColor("#66000000".toColorInt())
            } else {
                roundedBackground.setColor("#80FFFFFF".toColorInt())
            }
        } else {
            roundedBackground.setColor(
                if (dockFolderBgColor != 0) {
                    ContextCompat.getColor(launcherContext!!, dockFolderBgColor)
                } else {
                    ContextCompat.getColor(
                        launcherContext!!,
                        Resources.color.system_accent1_600
                    )
                }

            )
            roundedBackground.alpha = dockFolderOpacity

        }

        viewGroup.background = roundedBackground
        viewGroup.clipToOutline = true
    }

    private fun iconSizeHook(classLoader: ClassLoader) {
        val iconConfigClass = XposedHelpers.findClass(
            $$"com.miui.home.common.gridconfig.GridConfig$IconConfig",
            classLoader
        )
        val getIconSizeMethod = XposedHelpers.findMethodExact(iconConfigClass, "getIconSize")
        hook(getIconSizeMethod).intercept { _ ->
//            Log.i(TAG, "iconSizeHook Hook Called")
            return@intercept iconSize
        }
    }

    private fun setLauncherContext(classLoader: ClassLoader) {
        val appClass = XposedHelpers.findClass("com.miui.home.launcher.Application", classLoader)
        val onCreateMethodApp = XposedHelpers.findMethodExact(appClass, "onCreate")

        hook(onCreateMethodApp).intercept { chain ->
//            Log.i(TAG, "setLauncherContext Hook Called")
            chain.proceed()
            if (launcherContext == null) {
                try {
                    launcherContext = chain.thisObject as? Context
                    if (launcherContext != null) {

                        // 1. Manual Reload Receiver
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(ctx: Context, intent: Intent) {
                                if (intent.action == "com.ukm.app.RELOAD_ICONS") {
                                    Log.i(TAG, "Reload Broadcast Received! Restarting Launcher...")
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
                val iconType = XposedHelpers.callMethod(chain.args[0], "getIconType") as? Int
                val isHomeScreenItem =
                    chain.args[0].javaClass.name == "com.miui.home.launcher.ShortcutInfo"
                val shouldThemeIcon = !themeHomeScreenOnly || isHomeScreenItem
                if (shouldThemeIcon && iconType != 8) {
                    val originalIcon =
                        XposedHelpers.getObjectField(chain.thisObject, "icon") as Drawable
                    val componentName =
                        XposedHelpers.callMethod(chain.args[0], "getComponentInfo") as ComponentName
                    val customIcon = getCustomIcon(componentName, originalIcon)
                    val itemInfoWithIconAndMessage = chain.args[0]
                    try {
                        XposedHelpers.callMethod(
                            itemInfoWithIconAndMessage, "setIconDrawable", customIcon
                        )
                        XposedHelpers.callMethod(
                            itemInfoWithIconAndMessage,
                            "setEnableIconMask",
                            XposedHelpers.getObjectField(chain.thisObject, "enableIconMask")
                        )
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