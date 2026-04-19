package com.divehub.app.util

import com.divehub.app.BuildConfig
import java.time.Instant

object ConsentTexts {
    /** Min length 20 — required by `POST auth/apple` (`AppleAuthDto`). */
    fun appleOAuthConsentText(): String {
        val base = BuildConfig.LEGAL_DOCS_BASE_URL.trimEnd('/')
        return "Пользователь даёт согласие на обработку персональных данных при входе через Apple ID " +
            "и подтверждает ознакомление с Политикой конфиденциальности DiveHub ($base/privacy). " +
            "Оператор: ИП Попов-Толмачёв Денис Борисович (ИНН 772379972274). Время акцепта: ${Instant.now()}."
    }

    /** Min length 20 — required by `POST auth/google` (`GoogleAuthDto`). */
    fun googleOAuthConsentText(): String {
        val base = BuildConfig.LEGAL_DOCS_BASE_URL.trimEnd('/')
        return "Пользователь даёт согласие на обработку персональных данных при входе через Google " +
            "и подтверждает ознакомление с Политикой конфиденциальности DiveHub ($base/privacy). " +
            "Оператор: ИП Попов-Толмачёв Денис Борисович (ИНН 772379972274). Время акцепта: ${Instant.now()}."
    }

    fun registrationConsentText(): String {
        val base = BuildConfig.LEGAL_DOCS_BASE_URL.trimEnd('/')
        return "Пользователь подтверждает ознакомление и принятие Политики конфиденциальности DiveHub ($base/privacy) " +
            "и Пользовательского соглашения DiveHub ($base/agreement), а также даёт согласие на обработку персональных данных " +
            "в соответствии с указанной Политикой. Оператор персональных данных: ИП Попов-Толмачёв Денис Борисович (ИНН 772379972274, ОГРНИП 310774632100411). " +
            "Дата и время акцепта: ${Instant.now()}."
    }
}
