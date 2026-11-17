package com.vaultview.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.vaultview.app.data.Downloads
import com.vaultview.app.data.Favorites
import com.vaultview.app.data.Settings
import com.vaultview.app.data.Vimm

class VaultApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Vimm.init(this)
        Favorites.init(this)
        Settings.init(this)
        Downloads.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(Vimm.client)
        .crossfade(true)
        .respectCacheHeaders(false)
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("art")).maxSizeBytes(150L * 1024 * 1024).build() }
        .build()
}
