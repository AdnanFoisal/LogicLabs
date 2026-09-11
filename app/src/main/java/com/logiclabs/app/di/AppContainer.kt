package com.logiclabs.app.di

import android.content.Context
import com.logiclabs.core.data.persistence.ProjectsRepository
import com.logiclabs.core.data.progress.ProgressRepository
import com.logiclabs.core.data.session.SessionRepository
import com.logiclabs.core.data.settings.SettingsRepository

/**
 * The app's whole dependency graph.
 *
 * Each repository owns its own IO scope and is safe to hold for the process lifetime.
 * Composition: nothing depends on anything else here, so there is no ordering to get
 * wrong and no cycle to document.
 */
class AppContainer(context: Context) {
    val settings: SettingsRepository = SettingsRepository(context)
    val session: SessionRepository = SessionRepository(context)
    val progress: ProgressRepository = ProgressRepository(context)
    val projects: ProjectsRepository = ProjectsRepository(context)
}
