package com.github.bitcoinvpn.ui

import android.Manifest
import android.content.*
import android.content.res.ColorStateList
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.bitcoinvpn.AppConfig
import com.github.bitcoinvpn.AppConfig.ANG_PACKAGE
import com.github.bitcoinvpn.BuildConfig
import com.github.bitcoinvpn.R
import com.github.bitcoinvpn.databinding.ActivityMainBinding
import com.github.bitcoinvpn.dto.EConfigType
import com.github.bitcoinvpn.dto.SubscriptionItem
import com.github.bitcoinvpn.extension.toast
import com.github.bitcoinvpn.helper.SimpleItemTouchHelperCallback
import com.github.bitcoinvpn.service.V2RayServiceManager
import com.github.bitcoinvpn.util.*
import com.github.bitcoinvpn.viewmodel.MainViewModel
import com.google.android.ads.nativetemplates.NativeTemplateStyle
import com.google.android.ads.nativetemplates.TemplateView
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import com.tbruyelle.rxpermissions.RxPermissions
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.*
import me.drakeet.support.toast.ToastCompat
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit


class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var binding: ActivityMainBinding

    private val adapter by lazy { MainRecyclerAdapter(this) }
    private val mainStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }
    private var mItemTouchHelper: ItemTouchHelper? = null
    val mainViewModel: MainViewModel by viewModels()

    private val subStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SUB, MMKV.MULTI_PROCESS_MODE) }
    private var mInterstitialAd: InterstitialAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        //admob init
        MobileAds.initialize(this) {}
        //native load
        val adLoader = AdLoader.Builder(this, "ca-app-pub-8241048936065417~2250621522")
                .forNativeAd { ad : NativeAd ->
                    val styles = NativeTemplateStyle.Builder().build()
                    val template = findViewById<TemplateView>(R.id.my_template)
                    template.setStyles(styles)
                    template.setNativeAd(ad)
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        // Handle the failure by logging, altering the UI, and so on.
                        Log.d("Gads", "native fucked: "+adError?.toString())
                        val template = findViewById<TemplateView>(R.id.my_template)
                        template.visibility = View.GONE
                    }
                })
                .withNativeAdOptions(NativeAdOptions.Builder()
                        // Methods in the NativeAdOptions.Builder class can be
                        // used here to specify individual options settings.
                        .build())
                .build()
        adLoader.loadAd(AdRequest.Builder().build())

        //inter load
        var adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this,"ca-app-pub-8241048936065417/2187042342", adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("Gads", "inter fucked: "+adError?.toString())
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d("Gads", "inter Ad was loaded.")
                mInterstitialAd = interstitialAd
            }
        })


        title = getString(R.string.app_name)
        setSupportActionBar(binding.toolbar)

        binding.fab.setOnClickListener {
            try {
            if (mainViewModel.isRunning.value == true) {
                Utils.stopVService(this)
            } else {
                showCircle()
                setTestState("Updating Servers Please Wait!")
                importConfigViaSub()
                //setTestState("Updating Servers Please Wait!")
                Observable.timer(10000, TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        //mainViewModel.testAllTcping()
                        mainViewModel.testAllRealPing()
                        Observable.timer(10000, TimeUnit.MILLISECONDS)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {
                                hideCircle()
                                setTestState("Servers are updated, Connecting....")
                                //show inter
                                MmkvManager.sortByTestResults()
                                mainViewModel.reloadServerList()
                                mainStorage?.encode(
                                    MmkvManager.KEY_SELECTED_SERVER,
                                    mainViewModel.serversCache[0].guid
                                )
                                if (mInterstitialAd != null) {
                                    mInterstitialAd?.show(this)
                                } else {
                                    Log.d("Gads", "The interstitial ad wasn't ready yet.")
                                }
                                if (settingsStorage?.decodeString(AppConfig.PREF_MODE) ?: "VPN" == "VPN") {
                                    val intent = VpnService.prepare(this)
                                    if (intent == null) {
                                        startV2Ray()
                                    } else {
                                        requestVpnPermission.launch(intent)
                                    }
                                } else {
                                    startV2Ray()
                                }
                            }

                    }
            }
            }catch (e: Exception){

            }
        }
        binding.layoutTest.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                setTestState(getString(R.string.connection_test_testing))
                mainViewModel.testCurrentServerRealPing()
            } else {
//                tv_test_state.text = getString(R.string.connection_test_fail)
            }
        }

        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        val callback = SimpleItemTouchHelperCallback(adapter)
        mItemTouchHelper = ItemTouchHelper(callback)
        mItemTouchHelper?.attachToRecyclerView(binding.recyclerView)


        val toggle = ActionBarDrawerToggle(
                this, binding.drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)
        binding.version.text = "v${BuildConfig.VERSION_NAME}"// (${SpeedtestUtil.getLibVersion()})"

        setupViewModel()
        copyAssets()
        migrateLegacy()

        //shit I added for sub----------------------------------------------------------------
        val subItem = SubscriptionItem()
        var subId = "1"
        subItem.remarks = "YellowSocks"
        subItem.url = "https://raw.githubusercontent.com/parvizx3/v2mixer/main/sub"
        subStorage?.encode(subId, Gson().toJson(subItem))

/*        val subItem2 = SubscriptionItem()
        var subId2 = "2"
        subItem2.remarks = "YellowSocksIntranet"
        subItem2.url = "https://giteahub.ir/uSwVjZB49e/nigg/raw/branch/main/a"
        subStorage?.encode(subId2, Gson().toJson(subItem2))*/

    }

    private fun setupViewModel() {
        mainViewModel.updateListAction.observe(this) { index ->
            if (index >= 0) {
                adapter.notifyItemChanged(index)
            } else {
                adapter.notifyDataSetChanged()
            }
        }
        mainViewModel.updateTestResultAction.observe(this) { setTestState(it) }
        mainViewModel.isRunning.observe(this) { isRunning ->
            adapter.isRunning = isRunning
            if (isRunning) {
                binding.fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorSelected))
                setTestState(getString(R.string.connection_connected))
                binding.layoutTest.isFocusable = true
            } else {
                binding.fab.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.colorUnselected))
                setTestState(getString(R.string.connection_not_connected))
                binding.layoutTest.isFocusable = false
            }
            hideCircle()
        }
        mainViewModel.startListenBroadcast()
    }

    private fun copyAssets() {
        val extFolder = Utils.userAssetPath(this)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geo = arrayOf("geosite.dat", "geoip.dat")
                assets.list("")
                        ?.filter { geo.contains(it) }
                        ?.filter { !File(extFolder, it).exists() }
                        ?.forEach {
                            val target = File(extFolder, it)
                            assets.open(it).use { input ->
                                FileOutputStream(target).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Log.i(ANG_PACKAGE, "Copied from apk assets folder to ${target.absolutePath}")
                        }
            } catch (e: Exception) {
                Log.e(ANG_PACKAGE, "asset copy failed", e)
            }
        }
    }

    private fun migrateLegacy() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.migrateLegacyConfig(this@MainActivity)
            if (result != null) {
                launch(Dispatchers.Main) {
                    if (result) {
                        toast(getString(R.string.migration_success))
                        mainViewModel.reloadServerList()
                    } else {
                        toast(getString(R.string.migration_fail))
                    }
                }
            }
        }
    }

    fun startV2Ray() {
        if (mainStorage?.decodeString(MmkvManager.KEY_SELECTED_SERVER).isNullOrEmpty()) {
            return
        }
        showCircle()
//        toast(R.string.toast_services_start)
        V2RayServiceManager.startV2Ray(this)
        hideCircle()
    }

    fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            Utils.stopVService(this)
        }
        Observable.timer(500, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    startV2Ray()
                }
    }

    public override fun onResume() {
        super.onResume()
        mainViewModel.reloadServerList()
    }

    public override fun onPause() {
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.import_clipboard -> {
            importClipboard()
            true
        }
        R.id.import_manually_vmess -> {
            importManually(EConfigType.VMESS.value)
            true
        }
        R.id.import_manually_vless -> {
            importManually(EConfigType.VLESS.value)
            true
        }
        R.id.import_manually_ss -> {
            importManually(EConfigType.SHADOWSOCKS.value)
            true
        }
        R.id.import_manually_socks -> {
            importManually(EConfigType.SOCKS.value)
            true
        }
        R.id.import_manually_trojan -> {
            importManually(EConfigType.TROJAN.value)
            true
        }
        R.id.import_config_custom_clipboard -> {
            importConfigCustomClipboard()
            true
        }
        R.id.import_config_custom_local -> {
            importConfigCustomLocal()
            true
        }
        R.id.import_config_custom_url -> {
            importConfigCustomUrlClipboard()
            true
        }
        R.id.import_config_custom_url_scan -> {
            importQRcode(false)
            true
        }

//        R.id.sub_setting -> {
//            startActivity<SubSettingActivity>()
//            true
//        }

        R.id.sub_update -> {
            importConfigViaSub()
            true
        }

        R.id.export_all -> {
            if (AngConfigManager.shareNonCustomConfigsToClipboard(this, mainViewModel.serverList) == 0) {
                toast(R.string.toast_success)
            } else {
                toast(R.string.toast_failure)
            }
            true
        }

        R.id.ping_all -> {
            mainViewModel.testAllTcping()
            true
        }

        R.id.real_ping_all -> {
            mainViewModel.testAllRealPing()
            true
        }

        R.id.service_restart -> {
            restartV2Ray()
            true
        }

        R.id.del_all_config -> {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        MmkvManager.removeAllServer()
                        mainViewModel.reloadServerList()
                    }
                    .show()
            true
        }

        R.id.del_invalid_config -> {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    MmkvManager.removeInvalidServer()
                    mainViewModel.reloadServerList()
                }
                .show()
            true
        }
        R.id.sort_by_test_results -> {
            MmkvManager.sortByTestResults()
            mainViewModel.reloadServerList()
            true
        }
        R.id.filter_config -> {
            mainViewModel.filterConfig(this)
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun importManually(createConfigType : Int) {
        startActivity(
            Intent()
                .putExtra("createConfigType", createConfigType)
                .putExtra("subscriptionId", mainViewModel.subscriptionId)
                .setClass(this, ServerActivity::class.java)
        )
    }

    /**
     * import config from qrcode
     */
    fun importQRcode(forConfig: Boolean): Boolean {
//        try {
//            startActivityForResult(Intent("com.google.zxing.client.android.SCAN")
//                    .addCategory(Intent.CATEGORY_DEFAULT)
//                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), requestCode)
//        } catch (e: Exception) {
        RxPermissions(this)
                .request(Manifest.permission.CAMERA)
                .subscribe {
                    if (it)
                        if (forConfig)
                            scanQRCodeForConfig.launch(Intent(this, ScannerActivity::class.java))
                        else
                            scanQRCodeForUrlToCustomConfig.launch(Intent(this, ScannerActivity::class.java))
                    else
                        toast(R.string.toast_permission_denied)
                }
//        }
        return true
    }

    private val scanQRCodeForConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importBatchConfig(it.data?.getStringExtra("SCAN_RESULT"))
        }
    }

    private val scanQRCodeForUrlToCustomConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            importConfigCustomUrl(it.data?.getStringExtra("SCAN_RESULT"))
        }
    }

    /**
     * import config from clipboard
     */
    fun importClipboard()
            : Boolean {
        try {
            val clipboard = Utils.getClipboard(this)
            importBatchConfig(clipboard)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun importBatchConfig(server: String?, subid: String = "") {
        val subid2 = if(subid.isNullOrEmpty()){
            mainViewModel.subscriptionId
        }else{
            subid
        }
        val append = subid.isNullOrEmpty()

        var count = AngConfigManager.importBatchConfig(server, subid2, append)
        if (count <= 0) {
            count = AngConfigManager.importBatchConfig(Utils.decode(server!!), subid2, append)
        }
        if (count > 0) {
            toast(R.string.toast_success)
            mainViewModel.reloadServerList()
        } else {
            toast(R.string.toast_failure)
        }
    }

    fun importConfigCustomClipboard()
            : Boolean {
        try {
            val configText = Utils.getClipboard(this)
            if (TextUtils.isEmpty(configText)) {
                toast(R.string.toast_none_data_clipboard)
                return false
            }
            importCustomizeConfig(configText)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * import config from local config file
     */
    fun importConfigCustomLocal(): Boolean {
        try {
            showFileChooser()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    fun importConfigCustomUrlClipboard()
            : Boolean {
        try {
            val url = Utils.getClipboard(this)
            if (TextUtils.isEmpty(url)) {
                toast(R.string.toast_none_data_clipboard)
                return false
            }
            return importConfigCustomUrl(url)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * import config from url
     */
    fun importConfigCustomUrl(url: String?): Boolean {
        try {
            if (!Utils.isValidUrl(url)) {
                toast(R.string.toast_invalid_url)
                return false
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val configText = try {
                    Utils.getUrlContentWithCustomUserAgent(url)
                } catch (e: Exception) {
                    e.printStackTrace()
                    ""
                }
                launch(Dispatchers.Main) {
                    importCustomizeConfig(configText)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * import config from sub
     */
    fun importConfigViaSub()
            : Boolean {
        try {
            toast(R.string.title_sub_update)
            MmkvManager.decodeSubscriptions().forEach {
                if (TextUtils.isEmpty(it.first)
                        || TextUtils.isEmpty(it.second.remarks)
                        || TextUtils.isEmpty(it.second.url)
                ) {
                    return@forEach
                }
                if (!it.second.enabled) {
                    return@forEach
                }
                val url = it.second.url
                if (!Utils.isValidUrl(url)) {
                    return@forEach
                }
                Log.d(ANG_PACKAGE, url)
                lifecycleScope.launch(Dispatchers.IO) {
                    val configText = try {
                        Utils.getUrlContentWithCustomUserAgent(url)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        launch(Dispatchers.Main) {
                            toast("\"" + it.second.remarks + "\" " + getString(R.string.toast_failure))
                        }
                        return@launch
                    }
                    launch(Dispatchers.Main) {
                        importBatchConfig(configText, it.first)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * show file chooser
     */
    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        try {
            chooseFileForCustomConfig.launch(Intent.createChooser(intent, getString(R.string.title_file_chooser)))
        } catch (ex: ActivityNotFoundException) {
            toast(R.string.toast_require_file_manager)
        }
    }

    private val chooseFileForCustomConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = it.data?.data
        if (it.resultCode == RESULT_OK && uri != null) {
            readContentFromUri(uri)
        }
    }

    /**
     * read content from uri
     */
    private fun readContentFromUri(uri: Uri) {
        RxPermissions(this)
                .request(Manifest.permission.READ_EXTERNAL_STORAGE)
                .subscribe {
                    if (it) {
                        try {
                            contentResolver.openInputStream(uri).use { input ->
                                importCustomizeConfig(input?.bufferedReader()?.readText())
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else
                        toast(R.string.toast_permission_denied)
                }
    }

    /**
     * import customize config
     */
    fun importCustomizeConfig(server: String?) {
        try {
            if (server == null || TextUtils.isEmpty(server)) {
                toast(R.string.toast_none_data)
                return
            }
            mainViewModel.appendCustomConfigServer(server)
            mainViewModel.reloadServerList()
            toast(R.string.toast_success)
            //adapter.notifyItemInserted(mainViewModel.serverList.lastIndex)
        } catch (e: Exception) {
            ToastCompat.makeText(this, "${getString(R.string.toast_malformed_josn)} ${e.cause?.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            return
        }
    }

    fun setTestState(content: String?) {
        binding.tvTestState.text = content
    }

//    val mConnection = object : ServiceConnection {
//        override fun onServiceDisconnected(name: ComponentName?) {
//        }
//
//        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
//            sendMsg(AppConfig.MSG_REGISTER_CLIENT, "")
//        }
//    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun showCircle() {
        binding.fabProgressCircle.show()
    }

    fun hideCircle() {
        try {
            Observable.timer(300, TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        try {
                            if (binding.fabProgressCircle.isShown) {
                                binding.fabProgressCircle.hide()
                            }
                        } catch (e: Exception) {
                            Log.w(ANG_PACKAGE, e)
                        }
                    }
        } catch (e: Exception) {
            Log.d(ANG_PACKAGE, e.toString())
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            //R.id.server_profile -> activityClass = MainActivity::class.java
            R.id.sub_setting -> {
                startActivity(Intent(this, SubSettingActivity::class.java))
            }
            R.id.settings -> {
                startActivity(Intent(this, SettingsActivity::class.java)
                        .putExtra("isRunning", mainViewModel.isRunning.value == true))
            }
            R.id.user_asset_setting -> {
                startActivity(Intent(this, UserAssetActivity::class.java))
            }
            R.id.feedback -> {
                Utils.openUri(this, "https://t.me/YellowSupport")
            }
            R.id.telegram -> {
                Utils.openUri(this, "https://t.me/YellowSocks_VPN")
            }
            R.id.logcat -> {
                startActivity(Intent(this, LogcatActivity::class.java))
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}
