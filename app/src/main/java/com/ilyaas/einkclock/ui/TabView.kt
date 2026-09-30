package com.ilyaas.einkclock.ui

import android.app.Activity
import android.view.View

/** One tab = one plain view tree. No fragments, no lifecycle library. */
abstract class TabView(protected val host: Activity) {

    protected val ctx: android.content.Context get() = host

    abstract val view: View

    /** Called when the tab becomes visible and on every activity resume. */
    open fun onShown() {}

    /** Called when the tab stops being visible: cancel every periodic callback here. */
    open fun onHidden() {}
}
