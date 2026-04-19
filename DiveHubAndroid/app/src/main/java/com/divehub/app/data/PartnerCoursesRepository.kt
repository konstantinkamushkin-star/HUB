package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.AdminCourseLocal
import com.divehub.app.data.remote.dto.CourseListItemDto
import com.google.gson.reflect.TypeToken
import java.time.Instant

class PartnerCoursesRepository(private val graph: AppGraph) {
    private val gson get() = graph.gson

    suspend fun loadLocal(centerId: String): List<AdminCourseLocal> {
        val raw = graph.tokenStore.getPartnerCoursesJson().orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<AdminCourseLocal>>() {}.type
            val all = gson.fromJson<List<AdminCourseLocal>>(raw, type).orEmpty()
            all.filter { it.diveCenterId == centerId }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun mergeWithRemote(
        centerId: String,
        remoteCourses: List<CourseListItemDto>,
    ): List<AdminCourseLocal> {
        val local = loadLocal(centerId)
        val localById = local.associateBy { it.id }
        val merged = remoteCourses.map { remote ->
            val patch = localById[remote.id]
            AdminCourseLocal(
                id = remote.id,
                diveCenterId = centerId,
                name = patch?.name ?: remote.name,
                level = patch?.level ?: remote.level,
                description = patch?.description ?: remote.description,
                status = patch?.status ?: "active",
                updatedAt = patch?.updatedAt ?: nowIso(),
                durationMinutes = patch?.durationMinutes ?: remote.duration,
            )
        }.toMutableList()

        val remoteIds = remoteCourses.map { it.id }.toSet()
        local.filter { it.id !in remoteIds }.forEach { merged.add(it) }
        return merged.sortedByDescending { it.updatedAt }
    }

    suspend fun upsert(course: AdminCourseLocal) {
        val all = loadAllMutable()
        val idx = all.indexOfFirst { it.id == course.id && it.diveCenterId == course.diveCenterId }
        if (idx >= 0) {
            all[idx] = course.copy(updatedAt = nowIso())
        } else {
            all.add(course.copy(updatedAt = nowIso()))
        }
        saveAll(all)
    }

    suspend fun removeCourse(courseId: String, centerId: String) {
        val all = loadAllMutable()
        all.removeAll { it.id == courseId && it.diveCenterId == centerId }
        saveAll(all)
    }

    private suspend fun loadAllMutable(): MutableList<AdminCourseLocal> {
        val raw = graph.tokenStore.getPartnerCoursesJson().orEmpty()
        if (raw.isBlank()) return mutableListOf()
        return try {
            val type = object : TypeToken<List<AdminCourseLocal>>() {}.type
            gson.fromJson<List<AdminCourseLocal>>(raw, type).orEmpty().toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private suspend fun saveAll(items: List<AdminCourseLocal>) {
        graph.tokenStore.setPartnerCoursesJson(gson.toJson(items))
    }

    private fun nowIso(): String = Instant.now().toString()
}

