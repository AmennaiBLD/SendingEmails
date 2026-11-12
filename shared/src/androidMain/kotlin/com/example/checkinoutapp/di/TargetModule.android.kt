package com.example.checkinoutapp.di

import com.example.checkinoutapp.ui.platform.AndroidFileSaver
import com.example.checkinoutapp.ui.platform.AndroidScanFeedback
import com.example.checkinoutapp.ui.platform.FileSaver
import com.example.checkinoutapp.ui.platform.ScanFeedback
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val targetModule: Module = module {
    single<FileSaver> { AndroidFileSaver(androidContext()) }
    single<ScanFeedback> { AndroidScanFeedback(androidContext()) }
}
