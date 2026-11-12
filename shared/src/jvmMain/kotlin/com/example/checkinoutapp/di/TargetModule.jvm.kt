package com.example.checkinoutapp.di

import com.example.checkinoutapp.ui.platform.DesktopFileSaver
import com.example.checkinoutapp.ui.platform.DesktopScanFeedback
import com.example.checkinoutapp.ui.platform.FileSaver
import com.example.checkinoutapp.ui.platform.ScanFeedback
import org.koin.core.module.Module
import org.koin.dsl.module

actual val targetModule: Module = module {
    single<FileSaver> { DesktopFileSaver() }
    single<ScanFeedback> { DesktopScanFeedback() }
}
