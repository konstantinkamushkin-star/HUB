import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as nodemailer from 'nodemailer';

@Injectable()
export class MailService {
  private readonly logger = new Logger(MailService.name);

  constructor(private readonly config: ConfigService) {}

  /** True if password reset / welcome emails can be sent (same rules as transporter + From). */
  isSmtpConfigured(): boolean {
    const host = this.config.get<string>('SMTP_HOST')?.trim();
    const user = this.config.get<string>('SMTP_USER')?.trim();
    const pass = this.config.get<string>('SMTP_PASSWORD')?.trim();
    const from =
      this.config.get<string>('SMTP_FROM')?.trim() ||
      this.config.get<string>('SMTP_USER')?.trim();
    return !!(host && user && pass && from);
  }

  private transporter(): nodemailer.Transporter | null {
    const host = this.config.get<string>('SMTP_HOST')?.trim();
    const user = this.config.get<string>('SMTP_USER')?.trim();
    const pass = this.config.get<string>('SMTP_PASSWORD')?.trim();
    if (!host || !user || !pass) {
      return null;
    }
    const portRaw = this.config.get<string | number>('SMTP_PORT');
    const port =
      typeof portRaw === 'number'
        ? portRaw
        : parseInt(String(portRaw ?? '587'), 10) || 587;
    const secure = port === 465;
    const tlsServername =
      this.config.get<string>('SMTP_TLS_SERVERNAME')?.trim() || host;
    return nodemailer.createTransport({
      host,
      port,
      secure,
      requireTLS: !secure && port === 587,
      auth: { user, pass },
      connectionTimeout: 20_000,
      greetingTimeout: 20_000,
      socketTimeout: 25_000,
      tls: { servername: tlsServername },
    });
  }

  private smtpFailureDetails(e: unknown): string {
    if (e === null || typeof e !== 'object') {
      return '';
    }
    const o = e as Record<string, unknown>;
    const parts: string[] = [];
    if (typeof o.code === 'string') {
      parts.push(`code=${o.code}`);
    }
    if (typeof o.responseCode === 'number') {
      parts.push(`responseCode=${o.responseCode}`);
    }
    if (typeof o.command === 'string') {
      parts.push(`command=${o.command}`);
    }
    if (typeof o.response === 'string' && o.response.length < 600) {
      parts.push(
        `response=${o.response.replace(/\r?\n/g, ' ').trim().slice(0, 500)}`,
      );
    }
    return parts.length ? ` ${parts.join(' ')}` : '';
  }

  async sendPartnerWelcome(params: {
    to: string;
    temporaryPassword: string;
    businessName: string;
    appName?: string;
  }): Promise<void> {
    const app = params.appName ?? 'DiveHub';
    const subject = `${app}: доступ к кабинету партнёра`;
    const text = `Здравствуйте!\n\nВаша заявка для «${params.businessName}» одобрена.\n\n` +
      `Вход в приложение ${app}:\n  Email: ${params.to}\n  Временный пароль: ${params.temporaryPassword}\n\n` +
      `При первом входе приложение попросит сменить пароль.\n\n` +
      `Если вы не подавали заявку, проигнорируйте это письмо.\n`;

    const html = `<p>Здравствуйте!</p>
<p>Заявка для <strong>${escapeHtml(params.businessName)}</strong> одобрена.</p>
<p>Вход в приложение <strong>${escapeHtml(app)}</strong>:</p>
<ul><li><b>Email:</b> ${escapeHtml(params.to)}</li>
<li><b>Временный пароль:</b> <code>${escapeHtml(params.temporaryPassword)}</code></li></ul>
<p>При первом входе вы смените пароль в приложении.</p>`;

    const from = this.config.get<string>('SMTP_FROM') ?? this.config.get<string>('SMTP_USER');
    const tx = this.transporter();
    if (!tx || !from) {
      this.logger.warn(
        `SMTP не настроен — письмо не отправлено. Временный пароль для ${params.to}: ${params.temporaryPassword}`,
      );
      return;
    }
    await tx.sendMail({
      from,
      to: params.to,
      subject,
      text,
      html,
    });
    this.logger.log(`Partner welcome email sent to ${params.to}`);
  }

  /** Код сброса пароля (6 цифр). Возвращает false, если SMTP не настроен. */
  async sendPasswordReset(params: {
    to: string;
    code: string;
    appName?: string;
    validMinutes?: number;
  }): Promise<boolean> {
    const app = params.appName ?? 'DiveHub';
    const mins = params.validMinutes ?? 15;
    const subject = `${app}: код восстановления пароля`;
    const text =
      `Код для сброса пароля: ${params.code}\n\n` +
      `Действителен ${mins} минут.\n\n` +
      `Если вы не запрашивали восстановление, проигнорируйте это письмо.\n`;
    const html = `<p>Код для сброса пароля в <strong>${escapeHtml(app)}</strong>:</p>
<p style="font-size:22px;letter-spacing:4px;"><code>${escapeHtml(params.code)}</code></p>
<p>Действителен <strong>${mins}</strong> минут.</p>
<p>Если вы не запрашивали восстановление, проигнорируйте это письмо.</p>`;

    const from =
      this.config.get<string>('SMTP_FROM')?.trim() ||
      this.config.get<string>('SMTP_USER')?.trim();
    const tx = this.transporter();
    if (!tx || !from) {
      this.logger.warn(
        `SMTP не настроен — письмо со сбросом пароля не отправлено (${params.to}). Код: ${params.code}`,
      );
      return false;
    }
    try {
      await tx.sendMail({
        from,
        to: params.to,
        subject,
        text,
        html,
      });
      this.logger.log(`Password reset email sent to ${params.to}`);
      return true;
    } catch (e) {
      this.logger.error(
        `sendMail password reset failed for ${params.to}: ${
          e instanceof Error ? e.message : String(e)
        }${this.smtpFailureDetails(e)}`,
      );
      throw e;
    }
  }
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
