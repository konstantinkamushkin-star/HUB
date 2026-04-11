package com.divehub.app.util

import com.divehub.app.BuildConfig
import java.time.Instant

object ConsentTexts {
    fun registrationConsentText(): String {
        val base = BuildConfig.LEGAL_DOCS_BASE_URL.trimEnd('/')
        return "Пользователь подтверждает ознакомление и принятие Политики конфиденциальности DiveHub ($base/privacy) " +
            "и Пользовательского соглашения DiveHub ($base/agreement), а также даёт согласие на обработку персональных данных " +
            "в соответствии с указанной Политикой. Оператор персональных данных: ИП Попов-Толмачёв Денис Борисович (ИНН 772379972274, ОГРНИП 310774632100411). " +
            "Дата и время акцепта: ${Instant.now()}."
    }
}
