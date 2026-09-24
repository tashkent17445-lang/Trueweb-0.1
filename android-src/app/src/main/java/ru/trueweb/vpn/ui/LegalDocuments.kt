package ru.trueweb.vpn.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.trueweb.vpn.i18n.L10n
import ru.trueweb.vpn.i18n.L10n.t

object LegalDocuments {
    const val OPERATOR = "Клячко Павел Владимирович"

    val PRIVACY_TITLE: String get() = t("Политика конфиденциальности", "Privacy Policy")
    val TERMS_TITLE: String get() = t("Пользовательское соглашение", "Terms of Service")
    val RULES_TITLE: String get() = t("Правила использования", "Acceptable Use Rules")
    val DELETION_TITLE: String get() = t("Удаление аккаунта и данных", "Account and Data Deletion")

    val PRIVACY: String get() = if (L10n.isRussian()) PRIVACY_RU else PRIVACY_EN
    val TERMS: String get() = if (L10n.isRussian()) TERMS_RU else TERMS_EN
    val RULES: String get() = if (L10n.isRussian()) RULES_RU else RULES_EN
    val DELETION: String get() = if (L10n.isRussian()) DELETION_RU else DELETION_EN

    private const val PRIVACY_RU = """
Дата редакции: 18 сентября 2026 года.

1. Общие положения
TrueWeb — сервис защищённого сетевого подключения. Оператор сервиса: Клячко Павел Владимирович.

Настоящая политика описывает, какие данные обрабатываются при использовании приложения и сервиса TrueWeb, зачем это необходимо и как пользователь может управлять своими данными.

2. Какие данные могут обрабатываться
Для работы аккаунта и VPN могут обрабатываться: email, Telegram ID при привязке Telegram, выбранный пользователем логин, технический идентификатор устройства, модель устройства, версия Android, версия приложения, сведения о сроке подписки и оплатах, количество подключённых устройств, объём переданного трафика, время соединения и технические сведения об ошибках.

Пароли не хранятся в открытом виде. Коды подтверждения email имеют ограниченный срок действия и после использования становятся недействительными.

3. Сетевой трафик
TrueWeb не осуществляет намеренный просмотр, анализ или запись содержимого пользовательского интернет-трафика для рекламы, профилирования или продажи третьим лицам.

Серверы TrueWeb технически принимают и пересылают сетевые пакеты, поэтому для маршрутизации и работы сервиса могут обрабатываться служебные сетевые данные, включая IP-адрес подключения, объём переданных данных, время соединения и техническую информацию, необходимую для доставки трафика.

TrueWeb не устанавливает на устройство пользователя сертификаты для перехвата трафика, не выполняет MITM-подмену TLS-сертификатов и не расшифровывает защищённые HTTPS/TLS-соединения. Содержимое данных, дополнительно защищённых HTTPS/TLS или сквозным шифрованием, не доступно сервису в открытом виде.

Использование незашифрованных протоколов само по себе не обеспечивает конфиденциальность передаваемой информации.

4. Диагностика и отчёты об ошибках
Приложение не предназначено для ведения пользовательских журналов активности. При технической ошибке приложение может отправить на сервер TrueWeb минимальный диагностический отчёт: версию приложения, модель устройства, версию Android, этап работы, тип ошибки и техническое состояние сети.

В такие отчёты не должны включаться пароли, коды подтверждения, токены авторизации, полные VLESS-ссылки, ссылки подписок и содержимое пользовательского интернет-трафика. Диагностические сведения используются только для устранения неисправностей и обеспечения безопасности сервиса.

5. Цели обработки
Данные используются для авторизации, предоставления VPN-доступа, контроля срока подписки и количества устройств, обработки платежей, предотвращения злоупотреблений, технической поддержки, диагностики ошибок и защиты сервиса.

TrueWeb не продаёт персональные данные и не использует содержимое VPN-трафика для рекламного профилирования.

6. Хранение и удаление
Пользователь может запросить удаление аккаунта непосредственно в приложении. После подтверждения активные сессии прекращаются, VPN-доступ отзывается, а данные аккаунта удаляются или обезличиваются в объёме, допустимом и технически необходимом.

После удаления может сохраняться минимальный необратимый или псевдонимизированный технический маркер, необходимый для предотвращения повторного получения одноразового пробного периода и иных злоупотреблений. Также могут сохраняться сведения о платежах и операциях в объёме и на срок, необходимые для бухгалтерского, налогового, платёжного учёта, разрешения споров и исполнения требований закона.

7. Защита данных
TrueWeb применяет технические меры для ограничения доступа к данным, использует защищённые сетевые соединения и серверную авторизацию. Ни один интернет-сервис не может гарантировать абсолютную безопасность передачи и хранения информации.

8. Изменения политики
Политика может обновляться при изменении функциональности сервиса или требований законодательства. Актуальная версия публикуется в приложении и на официальном сайте TrueWeb.

9. Контакт
По вопросам конфиденциальности и удаления данных пользователь может обратиться через доступные каналы поддержки TrueWeb, указанные в приложении.
"""

    private const val PRIVACY_EN = """
Revision date: September 18, 2026.

1. General
TrueWeb is a secure network connection service. Service operator: Клячко Павел Владимирович.

This policy explains what data is processed when using the TrueWeb app and service, why it is needed, and how users can manage their data.

2. Data that may be processed
To provide the account and VPN service, TrueWeb may process: email address, Telegram ID when Telegram is linked, a username selected by the user, technical device identifier, device model, Android version, app version, subscription and payment information, number of connected devices, transferred traffic volume, connection time, and technical error information.

Passwords are not stored in plain text. Email verification codes have a limited validity period and become invalid after use.

3. Network traffic
TrueWeb does not intentionally inspect, analyze, or record the contents of users' internet traffic for advertising, profiling, or sale to third parties.

TrueWeb servers technically receive and forward network packets. For routing and service operation, service network data may therefore be processed, including the connection IP address, transferred data volume, connection time, and technical information required to deliver traffic.

TrueWeb does not install traffic interception certificates on users' devices, does not perform MITM substitution of TLS certificates, and does not decrypt protected HTTPS/TLS connections. Data additionally protected by HTTPS/TLS or end-to-end encryption is not available to the service in plain text.

Using unencrypted protocols does not by itself provide confidentiality for transmitted information.

4. Diagnostics and error reports
The app is not designed to keep user activity logs. When a technical error occurs, the app may send a minimal diagnostic report to TrueWeb: app version, device model, Android version, operation stage, error type, and technical network state.

Such reports must not include passwords, verification codes, authorization tokens, full VLESS links, subscription links, or the contents of users' internet traffic. Diagnostic information is used only for troubleshooting and service security.

5. Purposes of processing
Data is used for authorization, providing VPN access, managing subscription duration and device limits, processing payments, preventing abuse, technical support, error diagnostics, and service protection.

TrueWeb does not sell personal data and does not use VPN traffic contents for advertising profiling.

6. Storage and deletion
Users can request account deletion directly in the app. After confirmation, active sessions are terminated, VPN access is revoked, and account data is deleted or anonymized to the extent permitted and technically necessary.

After deletion, a minimal irreversible or pseudonymized technical marker may be retained to prevent repeated receipt of a one-time trial period and other abuse. Payment and transaction information may also be retained to the extent and for the period required for accounting, tax, payment records, dispute resolution, and legal compliance.

7. Data protection
TrueWeb uses technical measures to restrict access to data, protected network connections, and server-side authorization. No internet service can guarantee absolute security of data transmission and storage.

8. Policy changes
This policy may be updated when service functionality or legal requirements change. The current version is published in the app and on the official TrueWeb website.

9. Contact
For privacy and data deletion questions, users can contact TrueWeb through the support channels listed in the app.
"""

    private const val TERMS_RU = """
Дата редакции: 18 сентября 2026 года.

1. Предмет соглашения
TrueWeb предоставляет пользователю программное приложение и сетевую инфраструктуру для организации VPN-подключения. Оператор сервиса: Клячко Павел Владимирович.

Используя TrueWeb, пользователь подтверждает согласие с настоящим соглашением и Политикой конфиденциальности.

2. Доступность сервиса
TrueWeb принимает разумные меры для стабильной работы сервиса, однако не гарантирует доступность VPN во всех сетях, у всех операторов связи и интернет-провайдеров, на всех устройствах, во всех регионах и при любых способах фильтрации трафика.

Работа может зависеть от ограничений оператора, локальной сети, настроек устройства, версии операционной системы, блокировок, фильтрации, маршрутизации, доступности сторонней инфраструктуры и иных обстоятельств вне контроля TrueWeb.

Не гарантируются непрерывная работа 24/7, конкретная скорость, задержка, доступность отдельного сайта, приложения, протокола, страны или сетевого ресурса. Параметры соединения могут меняться автоматически для сохранения работоспособности.

3. Подписка и пробный период
Актуальные тарифы, срок подписки, стоимость и доступный пробный период показываются в приложении до совершения оплаты. Одноразовый пробный период предназначен для одного пользователя и не восстанавливается автоматически после удаления и повторного создания аккаунта.

Если в интерфейсе прямо не указано иное, продление выполняется пользователем самостоятельно и не является автоматическим списанием.

4. Платежи
Оплата производится через доступного платёжного провайдера. TrueWeb не хранит полные реквизиты банковской карты. Статус оплаты подтверждается сервером и платёжным провайдером.

Вопросы по ошибочной оплате, невозможности использования оплаченного периода или возврату рассматриваются индивидуально через поддержку с учётом фактически оказанной услуги и правил платёжного провайдера.

5. Ограничения ответственности
TrueWeb не несёт ответственности за неполадки сети пользователя, блокировки со стороны оператора или владельца ресурса, действия сторонних сервисов, совместимость конкретного устройства, а также за убытки, вызванные обстоятельствами, находящимися вне разумного контроля сервиса.

Ничто в соглашении не ограничивает права пользователя, которые не могут быть ограничены в силу применимого законодательства.

6. Безопасность аккаунта
Пользователь обязан разумно защищать доступ к своему устройству, email, Telegram и учётным данным. Передача доступа третьим лицам может привести к ограничению сервиса при обнаружении злоупотреблений.

7. Изменения
Функции, серверы, способы подключения и тарифы могут изменяться. Существенные изменения условий публикуются в приложении или официальных каналах TrueWeb.
"""

    private const val TERMS_EN = """
Revision date: September 18, 2026.

1. Subject of the agreement
TrueWeb provides the user with a software application and network infrastructure for establishing a VPN connection. Service operator: Клячко Павел Владимирович.

By using TrueWeb, the user agrees to these Terms of Service and the Privacy Policy.

2. Service availability
TrueWeb takes reasonable measures to keep the service stable, but does not guarantee VPN availability on every network, with every mobile operator or internet provider, on every device, in every region, or under every traffic-filtering method.

Operation may depend on carrier restrictions, local network settings, device settings, operating system version, blocking, filtering, routing, availability of third-party infrastructure, and other circumstances outside TrueWeb's control.

Continuous 24/7 operation, a particular speed or latency, or availability of any particular website, app, protocol, country, or network resource is not guaranteed. Connection parameters may change automatically to preserve service availability.

3. Subscription and trial period
Current plans, subscription duration, price, and available trial period are shown in the app before payment. A one-time trial period is intended for one user and is not automatically restored after account deletion and re-registration.

Unless the interface explicitly states otherwise, renewal is initiated by the user and is not an automatic recurring charge.

4. Payments
Payments are processed through an available payment provider. TrueWeb does not store full bank card details. Payment status is confirmed by the server and payment provider.

Questions about incorrect payments, inability to use a paid period, or refunds are handled individually through support, taking into account the service actually provided and the payment provider's rules.

5. Limitation of liability
TrueWeb is not responsible for failures in the user's network, restrictions imposed by a carrier or resource owner, actions of third-party services, compatibility of a particular device, or losses caused by circumstances outside the service's reasonable control.

Nothing in these terms limits user rights that cannot be limited under applicable law.

6. Account security
Users must take reasonable steps to protect access to their device, email, Telegram, and account credentials. Sharing access with third parties may result in service restrictions if abuse is detected.

7. Changes
Features, servers, connection methods, and plans may change. Material changes to these terms are published in the app or through official TrueWeb channels.
"""

    private const val RULES_RU = """
TrueWeb предназначен для законного использования сети и защиты соединения пользователя.

Запрещается использовать сервис для распространения вредоносного ПО, фишинга, спама, несанкционированного доступа к чужим системам, DDoS-атак, массового сканирования уязвимостей, распространения материалов, оборот которых запрещён применимым законодательством, а также для намеренного создания чрезмерной нагрузки на инфраструктуру TrueWeb.

При обнаружении технических атак, автоматизированного злоупотребления, обхода ограничений сервиса или угрозы инфраструктуре доступ может быть временно ограничен или прекращён. По возможности пользователь получает понятное уведомление о причине ограничения.

Пользователь самостоятельно отвечает за законность своих действий и соблюдение правил ресурсов, к которым получает доступ.
"""

    private const val RULES_EN = """
TrueWeb is intended for lawful network use and protection of the user's connection.

The service must not be used to distribute malware, conduct phishing or spam, gain unauthorized access to third-party systems, perform DDoS attacks, mass-scan vulnerabilities, distribute material prohibited by applicable law, or intentionally create excessive load on TrueWeb infrastructure.

If technical attacks, automated abuse, attempts to bypass service restrictions, or threats to the infrastructure are detected, access may be temporarily restricted or terminated. Where possible, the user will receive a clear explanation of the reason.

Users are responsible for the legality of their actions and for following the rules of the resources they access.
"""

    private const val DELETION_RU = """
Удаление аккаунта доступно в настройках TrueWeb.

После подтверждения удаления приложение завершает текущую VPN-сессию и отзывает авторизацию. Серверный VPN-доступ текущего аккаунта отключается или удаляется, а данные аккаунта удаляются либо обезличиваются в объёме, который не требуется для законных и технически обоснованных целей.

Одноразовый пробный период не выдаётся повторно только потому, что пользователь удалил и заново создал аккаунт. Для этого после удаления может сохраняться минимальный псевдонимизированный anti-abuse идентификатор, который не используется для рекламы или профилирования.

Сведения о платежах и финансовых операциях могут сохраняться на необходимый срок для подтверждения операций, рассмотрения возвратов, бухгалтерского и налогового учёта и исполнения требований закона.

Удаление аккаунта необратимо: прежние активные VPN-ключи и сессии перестают считаться действующими. При последующей регистрации создаётся новый аккаунт в соответствии с действующими условиями сервиса.
"""

    private const val DELETION_EN = """
Account deletion is available in TrueWeb settings.

After deletion is confirmed, the app terminates the current VPN session and revokes authorization. Server-side VPN access for the current account is disabled or removed, and account data is deleted or anonymized except where retention is required for lawful and technically justified purposes.

A one-time trial period is not issued again merely because a user deleted and recreated an account. For this purpose, a minimal pseudonymized anti-abuse identifier may be retained after deletion. It is not used for advertising or profiling.

Payment and financial transaction information may be retained for the period necessary to confirm transactions, process refund requests, maintain accounting and tax records, and comply with legal requirements.

Account deletion is irreversible: previously active VPN keys and sessions cease to be valid. If the user registers again, a new account is created under the terms then in effect.
"""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentScreen(title: String, text: String, onBack: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text(t("‹ Назад", "‹ Back")) }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
            Text(
                text = text.trim(),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun PrivacyConsentScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenPolicy: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🌐", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(16.dp))
            Text(t("Конфиденциальность TrueWeb", "TrueWeb Privacy"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text(
                t(
                    "Для работы аккаунта и VPN TrueWeb обрабатывает минимальные технические данные. Содержимое защищённого HTTPS/TLS-трафика сервис не расшифровывает и не использует для рекламы или профилирования.",
                    "To provide the account and VPN service, TrueWeb processes minimal technical data. The service does not decrypt protected HTTPS/TLS traffic and does not use its contents for advertising or profiling."
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpenPolicy) { Text(t("Открыть Политику конфиденциальности", "Open Privacy Policy")) }
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(t("Согласен", "Agree")) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(t("Не согласен", "Decline")) }
        }
    }
}
