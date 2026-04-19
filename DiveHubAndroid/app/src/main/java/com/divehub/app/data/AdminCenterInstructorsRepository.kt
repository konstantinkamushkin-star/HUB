package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.AdminCenterInstructorsLocal
import com.divehub.app.data.remote.dto.AdminInstructorLocal
import com.divehub.app.data.remote.dto.UserDto
import com.google.gson.reflect.TypeToken

class AdminCenterInstructorsRepository(private val graph: AppGraph) {

    private val gson get() = graph.gson

    suspend fun mergeWithLocal(centerId: String, remote: List<UserDto>): List<UserDto> {
        val local = getCenterData(centerId)
        val removed = local.removedIds.toSet()
        val byId = LinkedHashMap<String, UserDto>()
        remote.filterNot { removed.contains(it.id) }.forEach { byId[it.id] = it }
        local.added.forEach { a ->
            byId[a.id] = UserDto(
                id = a.id,
                email = a.email,
                firstName = a.firstName,
                lastName = a.lastName,
                role = a.role,
            )
        }
        return byId.values.toList()
    }

    suspend fun assign(centerId: String, user: UserDto) {
        val all = loadAll().toMutableList()
        val idx = all.indexOfFirst { it.centerId == centerId }
        val current = if (idx >= 0) all[idx] else AdminCenterInstructorsLocal(centerId = centerId)
        val added = current.added.filterNot { it.id == user.id }.toMutableList().apply {
            add(
                AdminInstructorLocal(
                    id = user.id,
                    email = user.email,
                    firstName = user.firstName,
                    lastName = user.lastName,
                    role = user.role,
                ),
            )
        }
        val next = current.copy(
            added = added,
            removedIds = current.removedIds.filterNot { it == user.id },
        )
        if (idx >= 0) all[idx] = next else all.add(next)
        saveAll(all)
    }

    suspend fun unassign(centerId: String, userId: String) {
        val all = loadAll().toMutableList()
        val idx = all.indexOfFirst { it.centerId == centerId }
        val current = if (idx >= 0) all[idx] else AdminCenterInstructorsLocal(centerId = centerId)
        val next = current.copy(
            added = current.added.filterNot { it.id == userId },
            removedIds = (current.removedIds + userId).distinct().sorted(),
        )
        if (idx >= 0) all[idx] = next else all.add(next)
        saveAll(all)
    }

    private suspend fun getCenterData(centerId: String): AdminCenterInstructorsLocal =
        loadAll().firstOrNull { it.centerId == centerId } ?: AdminCenterInstructorsLocal(centerId = centerId)

    private suspend fun loadAll(): List<AdminCenterInstructorsLocal> {
        val raw = graph.tokenStore.getAdminCenterInstructorsJson() ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AdminCenterInstructorsLocal>>() {}.type
            gson.fromJson<List<AdminCenterInstructorsLocal>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun saveAll(list: List<AdminCenterInstructorsLocal>) {
        graph.tokenStore.setAdminCenterInstructorsJson(if (list.isEmpty()) null else gson.toJson(list))
    }
}
