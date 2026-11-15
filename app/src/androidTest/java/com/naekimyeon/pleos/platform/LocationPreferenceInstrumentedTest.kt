package com.naekimyeon.pleos.platform

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationPreferenceInstrumentedTest {
    @Test fun explicitPreferenceDefaultsOffAndOnlyOneBooleanSurvivesStoreRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "foreground_location_preference_instrumented"
        val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val first = LocationPreferenceStore(context, name)
            assertFalse(first.isEnabled())
            first.setEnabled(true)
            assertTrue(LocationPreferenceStore(context, name).isEnabled())
            assertEquals(setOf("use_on_launch"), preferences.all.keys)
            assertTrue(preferences.all.values.single() is Boolean)
            first.setEnabled(false)
            assertFalse(LocationPreferenceStore(context, name).isEnabled())
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
