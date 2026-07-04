package com.vocatim.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/** Single DataStore instance shared by all preference classes. */
internal val Context.prefsDataStore by preferencesDataStore(name = "vocatim_prefs")
