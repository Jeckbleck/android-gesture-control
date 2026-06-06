package com.gesturecontrol.camera

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "camera_prefs")

class CameraPreferenceRepository(private val context: Context) {

    private val KEY_LENS = stringPreferencesKey("lens_facing")

    val lensFacing: Flow<LensFacing> = context.dataStore.data.map { prefs ->
        when (prefs[KEY_LENS]) {
            LensFacing.FRONT.name -> LensFacing.FRONT
            else -> LensFacing.BACK
        }
    }

    suspend fun setLens(facing: LensFacing) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LENS] = facing.name
        }
    }
}
