package com.divehub.app.ui.shops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.data.repository.ShopRepository
import com.divehub.app.data.remote.dto.ShopV1DetailDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShopPublicUiState(
    val loading: Boolean = true,
    val shop: ShopV1DetailDto? = null,
    val error: String? = null,
)

class ShopPublicViewModel(
    private val graph: AppGraph,
    private val shopId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(ShopPublicUiState())
    val state: StateFlow<ShopPublicUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = ShopPublicUiState(loading = true, error = null)
            runCatching { ShopRepository(graph).getShop(shopId) }
                .onSuccess { shop ->
                    _state.value = ShopPublicUiState(loading = false, shop = shop, error = null)
                }
                .onFailure {
                    _state.value = ShopPublicUiState(loading = false, shop = null, error = "not_found")
                }
        }
    }

    companion object {
        fun factory(graph: AppGraph, shopId: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ShopPublicViewModel(graph, shopId) as T
            }
        }
    }
}
