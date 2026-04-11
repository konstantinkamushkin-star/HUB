import Foundation

enum ConsentTexts {
    /// Публичный сайт с документами (совпадает с admin-web и Android `LEGAL_DOCS_BASE_URL`).
    private static let legalDocumentsBase = "https://dive-hub.ru"

    static var privacyPolicyURL: URL {
        URL(string: "\(legalDocumentsBase)/privacy")!
    }

    static var userAgreementURL: URL {
        URL(string: "\(legalDocumentsBase)/agreement")!
    }

    /// Текст, сохраняемый на сервере вместе с флагом согласия при регистрации / заявке партнёра.
    static func registrationConsentText() -> String {
        let base = legalDocumentsBase.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let stamp = ISO8601DateFormatter().string(from: Date())
        return "Пользователь подтверждает ознакомление и принятие Политики конфиденциальности DiveHub (\(base)/privacy) " +
            "и Пользовательского соглашения DiveHub (\(base)/agreement), а также даёт согласие на обработку персональных данных " +
            "в соответствии с указанной Политикой. Оператор персональных данных: ИП Попов-Толмачёв Денис Борисович (ИНН 772379972274, ОГРНИП 310774632100411). " +
            "Дата и время акцепта: \(stamp)."
    }

    /// Обратная совместимость с существующими вызовами API.
    static var personalDataProcessing: String { registrationConsentText() }
}
