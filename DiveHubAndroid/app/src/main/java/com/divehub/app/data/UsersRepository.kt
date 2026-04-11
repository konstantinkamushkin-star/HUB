package com.divehub.app.data

import com.divehub.app.AppGraph
import com.divehub.app.data.remote.dto.UserDto

class UsersRepository(private val graph: AppGraph) {
    suspend fun getUser(id: String): UserDto = graph.usersApi().getUser(id)
}
