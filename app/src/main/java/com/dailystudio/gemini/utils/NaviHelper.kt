package com.dailystudio.gemini.utils

import android.app.Activity
import android.view.View
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment

private fun Fragment.getAppCompatActivity(): AppCompatActivity? {
    val activity = activity ?: return null
    if (activity !is AppCompatActivity) {
        return null
    }

    return (activity)
}

fun Fragment.findActionBar(): ActionBar? {
    return getAppCompatActivity()?.supportActionBar
}

fun Fragment.changeTitle(title: CharSequence?) {
    val actionBar = findActionBar() ?: return

    actionBar.title = title
}

fun Fragment.changeTitle(labelResId: Int) {
    changeTitle(if (labelResId > 0) {
        getString(labelResId)
    } else {
        null
    })
}

fun Activity.registerActionBar(fragmentView: View, actionResId: Int) {
    if (this is AppCompatActivity) {
        fragmentView.findViewById<Toolbar>(actionResId)?.let {
            setSupportActionBar(it)
        }
    }
}