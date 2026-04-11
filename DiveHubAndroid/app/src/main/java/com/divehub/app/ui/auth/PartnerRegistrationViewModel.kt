package com.divehub.app.ui.auth

import com.divehub.app.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.divehub.app.AppGraph
import com.divehub.app.data.AuthRepository
import com.divehub.app.data.remote.dto.SubmitPartnerRegistrationRequestDto
import com.divehub.app.util.ConsentTexts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PartnerRegKind { DIVE_CENTER, SHOP }

enum class PartnerRegShopType { OFFLINE, ONLINE }

data class PartnerRegUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

class PartnerRegistrationViewModel(
    private val graph: AppGraph,
    private val authRepo: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PartnerRegUiState())
    val state: StateFlow<PartnerRegUiState> = _state.asStateFlow()

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearSuccess() {
        _state.update { it.copy(successMessage = null) }
    }

    fun submit(
        kind: PartnerRegKind,
        shopType: PartnerRegShopType,
        name: String,
        description: String,
        contactEmail: String,
        contactPhone: String,
        country: String,
        city: String,
        address: String,
        website: String,
        latitudeText: String,
        longitudeText: String,
        personalDataConsent: Boolean,
    ) {
        val lat = parseCoord(latitudeText)
        val lng = parseCoord(longitudeText)

        val validation = validate(
            kind,
            shopType,
            name,
            contactEmail,
            contactPhone,
            country,
            city,
            lat,
            lng,
            personalDataConsent,
        )
        if (validation != null) {
            _state.update { it.copy(error = graph.application.getString(validation)) }
            return
        }

        val body = SubmitPartnerRegistrationRequestDto(
            kind = if (kind == PartnerRegKind.DIVE_CENTER) "dive_center" else "shop",
            name = name.trim(),
            description = description.trim().takeIf { it.isNotEmpty() },
            contactEmail = contactEmail.trim(),
            contactPhone = contactPhone.trim(),
            country = country.trim(),
            city = city.trim(),
            address = address.trim().takeIf { it.isNotEmpty() },
            website = website.trim().takeIf { it.isNotEmpty() },
            shopType = if (kind == PartnerRegKind.SHOP) shopType.name.lowercase() else null,
            latitude = lat,
            longitude = lng,
            personalDataConsent = personalDataConsent,
            personalDataConsentText = ConsentTexts.registrationConsentText(),
        )

        viewModelScope.launch {
            _state.update { PartnerRegUiState(loading = true) }
            runCatching { graph.partnerRegistrationApi().submit(body) }
                .onSuccess { res ->
                    _state.update {
                        PartnerRegUiState(loading = false, successMessage = res.message.ifBlank { null })
                    }
                }
                .onFailure { e ->
                    _state.update {
                        PartnerRegUiState(loading = false, error = authRepo.parseErrorMessage(e))
                    }
                }
        }
    }

    private fun validate(
        kind: PartnerRegKind,
        shopType: PartnerRegShopType,
        name: String,
        contactEmail: String,
        contactPhone: String,
        country: String,
        city: String,
        lat: Double?,
        lng: Double?,
        personalDataConsent: Boolean,
    ): Int? {
        if (name.trim().length < 2) return R.string.partner_reg_err_name
        if (contactEmail.trim().length < 5 || !contactEmail.contains('@')) {
            return R.string.partner_reg_err_email
        }
        if (contactPhone.trim().length < 5) return R.string.partner_reg_err_phone
        if (country.trim().length < 2) return R.string.partner_reg_err_country
        if (city.trim().length < 2) return R.string.partner_reg_err_city
        if (!personalDataConsent) return R.string.personal_data_consent_required

        val needCoords = kind == PartnerRegKind.DIVE_CENTER ||
            (kind == PartnerRegKind.SHOP && shopType == PartnerRegShopType.OFFLINE)
        if (needCoords) {
            if (lat == null || lng == null) return R.string.partner_reg_err_coords
            if (lat < -90.0 || lat > 90.0) return R.string.partner_reg_err_lat
            if (lng < -180.0 || lng > 180.0) return R.string.partner_reg_err_lng
        } else {
            if (lat != null && (lat < -90.0 || lat > 90.0)) return R.string.partner_reg_err_lat
            if (lng != null && (lng < -180.0 || lng > 180.0)) return R.string.partner_reg_err_lng
        }
        return null
    }

    private fun parseCoord(raw: String): Double? {
        val t = raw.trim().replace(',', '.')
        if (t.isEmpty()) return null
        return t.toDoubleOrNull()
    }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PartnerRegistrationViewModel(graph, AuthRepository(graph)) as T
            }
        }
    }
}
