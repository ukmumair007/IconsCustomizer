package com.ukm.app.iconscustomizer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.ukm.app.iconscustomizer.MainActivity.Companion.PREF_NAME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AllAppsFragment : Fragment(R.layout.fragment_all_apps) {

    private lateinit var iconPackPackage: String
    private lateinit var adapter: AppAdapter
    private var allApps = listOf<AppInfo>()
    private var appFilterMap: Map<String, String> = emptyMap()
    private var showThemedIcons = true

    private fun getPrefs(): SharedPreferences {
        return requireContext().getSharedPreferences(PREF_NAME, MODE_PRIVATE)
    }

    data class AppInfo(
        val name: String,
        val componentString: String,
        val stockIcon: Drawable,
        val packageName: String
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val toolbar = requireActivity().findViewById<MaterialToolbar>(R.id.materialToolbar)
        toolbar.title = "Apply Custom Icon"
        val searchEditText =
            view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.searchAppEditText)
        val recyclerView = view.findViewById<RecyclerView>(R.id.appRecyclerView)
        iconPackPackage = arguments?.getString("EXTRA_ICON_PACK")
            ?: getPrefs().getString("icon_pack", "none").toString()
        showThemedIcons = getPrefs().getBoolean("preview_themed_icons", true)
        recyclerView.layoutManager =
            LinearLayoutManager(requireContext())
        adapter = AppAdapter(emptyList(), requireContext())
        recyclerView.adapter = adapter

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                val toggleItem = menu.add(
                    Menu.NONE,
                    101,
                    Menu.NONE,
                    "Show Themed Icons"
                )
                toggleItem.isCheckable = true
                toggleItem.isChecked = showThemedIcons
                toggleItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == 101) {
                    showThemedIcons = !showThemedIcons
                    menuItem.isChecked = showThemedIcons
                    UIHelpers.pushLocalPref(
                        requireContext(),
                        "preview_themed_icons",
                        showThemedIcons
                    )
                    adapter.toggleThemedMode(showThemedIcons)
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        viewLifecycleOwner.lifecycleScope.launch {
            appFilterMap = withContext(Dispatchers.IO) {
                IconPackHelper.getAppFilterMap(requireContext(), iconPackPackage)
            }
            allApps = withContext(Dispatchers.IO) { loadInstalledApps() }
            adapter.updateData(allApps)
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s.toString())
            }
        })
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::adapter.isInitialized) {
            adapter.cleanUp()
        }
    }

    private fun loadInstalledApps(): List<AppInfo> {
        val pm = requireContext().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)

        val appsList = mutableListOf<AppInfo>()
        for (resolveInfo in activities) {
            val pkg = resolveInfo.activityInfo.packageName
            val cls = resolveInfo.activityInfo.name
            val name = resolveInfo.loadLabel(pm).toString()
            val icon = resolveInfo.loadIcon(pm)
            val componentString = "ComponentInfo{$pkg/$cls}"
            appsList.add(AppInfo(name, componentString, icon, pkg))
        }
        return appsList.sortedBy { it.name.lowercase() }
    }

    inner class AppAdapter(
        private var filteredApps: List<AppInfo>,
        private val adapterContext: Context
    ) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

        private val prefs = getPrefs()
        private val isMonetEnabled = prefs.getBoolean("enable_monet_colors", false)
        private val monetBgColor: Int? =
            if (prefs.getInt("monet_bg_color", 0) != 0) ContextCompat.getColor(
                adapterContext,
                prefs.getInt("monet_bg_color", 0)
            ) else null
        private val monetFgColor: Int? =
            if (prefs.getInt("monet_fg_color", 0) != 0) ContextCompat.getColor(
                adapterContext,
                prefs.getInt("monet_fg_color", 0)
            ) else null

        private val adapterScope = CoroutineScope(Dispatchers.Main + Job())
        private val memoryCache = LruCache<String, Drawable>(150)
        private var isThemedMode = showThemedIcons

        inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val appIcon: ImageView = view.findViewById(R.id.appIcon)
            val appName: TextView = view.findViewById(R.id.appName)
            val appAssignedIcon: TextView = view.findViewById(R.id.appAssignedIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_installed_app, parent, false)
            return AppViewHolder(view)
        }

        override fun getItemCount(): Int = filteredApps.size

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val app = filteredApps[position]
            holder.appName.text = app.name

            val manualIcon = prefs.getString(
                "custom_icon_${iconPackPackage}_${app.componentString}",
                null
            )

            var targetDrawableName = manualIcon

            if (targetDrawableName == null) {
                targetDrawableName = appFilterMap[app.componentString]
                if (targetDrawableName == null) {
                    val packagePrefix = "ComponentInfo{${app.packageName}/"
                    targetDrawableName =
                        appFilterMap.entries.firstOrNull { it.key.startsWith(packagePrefix) }?.value
                }
            }

            if (manualIcon != null) {
                holder.appAssignedIcon.text = "Custom override: $manualIcon"
                holder.appAssignedIcon.setTextColor("#4CAF50".toColorInt())
            } else if (targetDrawableName != null) {
                holder.appAssignedIcon.text = "Using default pack icon"
                holder.appAssignedIcon.setTextColor("#888888".toColorInt())
            } else {
                holder.appAssignedIcon.text = "Unthemed / Stock"
                holder.appAssignedIcon.setTextColor("#E53935".toColorInt())
            }

            if (!isThemedMode) {
                holder.appIcon.tag = "stock_${app.componentString}"
                holder.appIcon.setImageDrawable(app.stockIcon)
            } else {
                if (targetDrawableName != null) {
                    holder.appIcon.tag = targetDrawableName
                    val cachedIcon = memoryCache.get(targetDrawableName)

                    if (cachedIcon != null) {
                        holder.appIcon.setImageDrawable(cachedIcon)
                    } else {
                        holder.appIcon.setImageDrawable(app.stockIcon)

                        adapterScope.launch {
                            val finalDrawable = withContext(Dispatchers.IO) {
                                val rawDrawable = IconPackHelper.loadIcon(
                                    adapterContext,
                                    iconPackPackage,
                                    targetDrawableName
                                )

                                if (rawDrawable != null && isMonetEnabled) {
                                    applyCustomizedColorToIcon(rawDrawable)
                                } else {
                                    rawDrawable
                                }
                            }

                            if (finalDrawable != null) {
                                memoryCache.put(targetDrawableName, finalDrawable)

                                if (holder.appIcon.tag == targetDrawableName) {
                                    holder.appIcon.setImageDrawable(finalDrawable)
                                }
                            }
                        }
                    }
                } else {
                    holder.appIcon.tag = app.componentString
                    holder.appIcon.setImageDrawable(app.stockIcon)
                }
            }

            holder.itemView.setOnClickListener { openIconPicker(app) }
        }

        fun applyCustomizedColorToIcon(drawable: Drawable): Drawable {
            if (monetFgColor == 0 && monetBgColor == 0) return drawable

            return IconPackHelper.putColorIntoDrawable(
                adapterContext,
                drawable,
                monetBgColor,
                monetFgColor
            )!!
        }

        private fun openIconPicker(app: AppInfo) {
            val pickIntent = Intent(adapterContext, IconPickerActivity::class.java).apply {
                putExtra("EXTRA_ICON_PACK", iconPackPackage)
                putExtra("EXTRA_APP_NAME", app.name)
                putExtra("EXTRA_COMPONENT_STRING", app.componentString)
            }
            startActivity(pickIntent)
        }

        @SuppressLint("NotifyDataSetChanged")
        fun updateData(newApps: List<AppInfo>) {
            filteredApps = newApps
            notifyDataSetChanged()
        }

        @SuppressLint("NotifyDataSetChanged")
        fun filter(query: String) {
            filteredApps = if (query.isEmpty()) {
                allApps
            } else {
                allApps.filter { it.name.contains(query, ignoreCase = true) }
            }
            notifyDataSetChanged()
        }

        @SuppressLint("NotifyDataSetChanged")
        fun toggleThemedMode(isThemed: Boolean) {
            isThemedMode = isThemed
            notifyDataSetChanged()
        }

        fun cleanUp() {
            adapterScope.cancel()
            memoryCache.evictAll()
        }
    }
}
