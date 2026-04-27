package com.ukm.app.iconscustomizer

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IconPickerActivity : AppCompatActivity() {

    private lateinit var iconPackPackage: String
    private lateinit var appName: String
    private lateinit var currentIconImage: ImageView
    private lateinit var componentString: String
    private lateinit var adapter: IconGridAdapter
    private val allAvailableIcons = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_icon_picker)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val materialToolbar = findViewById<MaterialToolbar>(R.id.materialToolbar)
        setSupportActionBar(materialToolbar)
        iconPackPackage = intent.getStringExtra("EXTRA_ICON_PACK") ?: return finish()
        appName = intent.getStringExtra("EXTRA_APP_NAME") ?: "App"
        componentString = intent.getStringExtra("EXTRA_COMPONENT_STRING") ?: ""

        currentIconImage = findViewById(R.id.currentIconImage)
        val appNameTextView = findViewById<TextView>(R.id.themingAppName)
        val recyclerView = findViewById<RecyclerView>(R.id.iconRecyclerView)
        val spinner = findViewById<ProgressBar>(R.id.loadingSpinner)
        val searchBox = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.searchIconEditText)
        appNameTextView.text = appName

        lifecycleScope.launch {
            loadCurrentIcon(currentIconImage)
        }

        recyclerView.layoutManager = GridLayoutManager(this, 4)
        adapter = IconGridAdapter(emptyList())
        recyclerView.adapter = adapter

        lifecycleScope.launch {
            val icons = withContext(Dispatchers.IO) {
                IconPackHelper.getAllIconsFromPack(this@IconPickerActivity, iconPackPackage)
            }
            allAvailableIcons.addAll(icons)
            spinner.visibility = View.GONE
            adapter.updateData(allAvailableIcons)
        }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s.toString())
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.icon_picker_menu, menu)
        return true
    }

    private suspend fun loadCurrentIcon(imageView: ImageView) {
        val prefs = App.mService?.getRemotePreferences(MainActivity.PREF_NAME)
            ?: getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE)
        val manualIcon = prefs.getString("custom_icon_${iconPackPackage}_$componentString", null)
        var targetDrawableName = manualIcon
        if (targetDrawableName == null) {
            val appFilterMap = withContext(Dispatchers.IO) {
                IconPackHelper.getAppFilterMap(this@IconPickerActivity, iconPackPackage)
            }
            targetDrawableName = appFilterMap[componentString]
            if (targetDrawableName == null) {
                val pkgName = componentString.substringAfter("{").substringBefore("/")
                val packagePrefix = "ComponentInfo{$pkgName/"
                targetDrawableName =
                    appFilterMap.entries.firstOrNull { it.key.startsWith(packagePrefix) }?.value
            }
        }
        if (targetDrawableName != null) {
            val loadedDrawable = withContext(Dispatchers.IO) {
                IconPackHelper.loadIcon(
                    this@IconPickerActivity,
                    iconPackPackage,
                    targetDrawableName
                )
            }
            if (loadedDrawable != null) {
                imageView.setImageDrawable(loadedDrawable)
            } else {
                loadStockAppIcon(imageView)
            }
        } else {
            loadStockAppIcon(imageView)
        }
    }

    private fun loadStockAppIcon(imageView: ImageView) {
        try {
            val pkgName = componentString.substringAfter("{").substringBefore("/")
            val icon = packageManager.getApplicationIcon(pkgName)
            imageView.setImageDrawable(icon)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menuResetDefault -> {
                resetIconToDefault()
                UIHelpers.restartLauncher(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun resetIconToDefault() {
        val remotePrefs = App.mService?.getRemotePreferences(MainActivity.PREF_NAME)
            ?: getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE)
        val prefKey = "custom_icon_${iconPackPackage}_$componentString"
        remotePrefs.edit {
            remove(prefKey)
        }
        val localPrefs = getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE)
        localPrefs.edit(commit = true) {
            remove(prefKey)
        }
        Toast.makeText(this, "Icon reset to default!", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::adapter.isInitialized) {
            adapter.cleanUp()
        }
    }

    private fun saveIconChoice(chosenDrawableName: String) {
        val manualOverrideKey = "custom_icon_${iconPackPackage}_$componentString"
        UIHelpers.pushLocalPref(this, manualOverrideKey, chosenDrawableName)
        UIHelpers.pushRemotePref(manualOverrideKey, chosenDrawableName)
        finish()
    }

    inner class IconGridAdapter(private var displayedIcons: List<String>) :
        RecyclerView.Adapter<IconGridAdapter.IconViewHolder>() {

        private val adapterScope = CoroutineScope(Dispatchers.Main + Job())
        private val memoryCache = LruCache<String, Drawable>(200)

        inner class IconViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val iconImage: ImageView = view.findViewById(R.id.singleIconImage)
            var currentJob: Job? = null // Track the specific job for this cell!
        }

        override fun onViewRecycled(holder: IconViewHolder) {
            super.onViewRecycled(holder)
            holder.currentJob?.cancel()
            holder.iconImage.setImageDrawable(null)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
            val view =
                LayoutInflater.from(parent.context).inflate(R.layout.item_icon, parent, false)
            return IconViewHolder(view)
        }

        override fun getItemCount(): Int = displayedIcons.size

        override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
            val drawableName = displayedIcons[position]

            holder.currentJob?.cancel()

            holder.iconImage.tag = drawableName
            holder.iconImage.setImageDrawable(null)

            val cachedIcon = memoryCache.get(drawableName)
            if (cachedIcon != null) {
                holder.iconImage.setImageDrawable(cachedIcon)
            } else {
                holder.currentJob = adapterScope.launch {
                    val loadedDrawable = withContext(Dispatchers.IO) {
                        IconPackHelper.loadIcon(applicationContext, iconPackPackage, drawableName)
                    }
                    if (loadedDrawable != null) {
                        memoryCache.put(drawableName, loadedDrawable)
                        if (holder.iconImage.tag == drawableName) {
                            holder.iconImage.setImageDrawable(loadedDrawable)
                        }
                    }
                }
            }
            holder.itemView.setOnClickListener {
                saveIconChoice(drawableName)
                UIHelpers.restartLauncher(this@IconPickerActivity)
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        fun updateData(newIcons: List<String>) {
            displayedIcons = newIcons
            notifyDataSetChanged()
        }

        @SuppressLint("NotifyDataSetChanged")
        fun filter(query: String) {
            displayedIcons = if (query.isEmpty()) {
                allAvailableIcons
            } else {
                allAvailableIcons.filter { it.contains(query, ignoreCase = true) }
            }
            notifyDataSetChanged()
        }

        fun cleanUp() {
            adapterScope.cancel()
            memoryCache.evictAll()
        }
    }
}