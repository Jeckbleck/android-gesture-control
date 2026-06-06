package com.gesturecontrol.camera

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraPreferenceRepositoryTest {

    private class FakeRepo {
        private val _lensFacing = MutableStateFlow(LensFacing.BACK)
        val lensFacing: Flow<LensFacing> = _lensFacing
        suspend fun setLens(facing: LensFacing) { _lensFacing.value = facing }
    }

    @Test
    fun `default lens is BACK`() = runTest {
        val repo = FakeRepo()
        assertEquals(LensFacing.BACK, repo.lensFacing.first())
    }

    @Test
    fun `setLens FRONT updates flow`() = runTest {
        val repo = FakeRepo()
        repo.setLens(LensFacing.FRONT)
        assertEquals(LensFacing.FRONT, repo.lensFacing.first())
    }

    @Test
    fun `setLens BACK after FRONT reverts`() = runTest {
        val repo = FakeRepo()
        repo.setLens(LensFacing.FRONT)
        repo.setLens(LensFacing.BACK)
        assertEquals(LensFacing.BACK, repo.lensFacing.first())
    }
}
