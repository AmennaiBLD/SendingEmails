package com.example.checkinoutapp.di

import com.example.checkinoutapp.ui.platform.BrowserFileSaver
import com.example.checkinoutapp.ui.platform.BrowserScanFeedback
import com.example.checkinoutapp.ui.platform.FileSaver
import com.example.checkinoutapp.ui.platform.ScanFeedback
import org.koin.core.module.Module
import org.koin.dsl.module

actual val targetModule: Module = module {
    single<FileSaver> { BrowserFileSaver() }
    single<ScanFeedback> { BrowserScanFeedback() }
}
