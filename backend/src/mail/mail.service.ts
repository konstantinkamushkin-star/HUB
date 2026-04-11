import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as nodemailer from 'nodemailer';

@Injectable()
export class MailService {
  private readonly logger = new Logger(MailService.name);

  constructor(private readonly config: ConfigService) {}

  private transporter(): nodemailer.Transporter | null {
    const host = this.config.get<string>('SMTP_HOST');
    const port = this.config.get<number>('SMTP_PORT') ?? 587;
    const user = this.config.get<string>('SMTP_USER');
    const pass = this.config.get<string>('SMTP_PASSWORD');
    if (!host || !user || !pass) {
      return null;
    }
    return nodemailer.createTransport({
      host,
      port,
      secure: port === 465,
      auth: { user, pass },
    });
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

    const from = this.config.get<string>('SMTP_FROM') ?? this.config.get<string>('SMTP_USER');
    const tx = this.transporter();
    if (!tx || !from) {
      this.logger.warn(
        `SMTP не настроен — письмо со сбросом пароля не отправлено (${params.to}). Код: ${params.code}`,
      );
      return false;
    }
    await tx.sendMail({
      from,
      to: params.to,
      subject,
      text,
      html,
    });
    this.logger.log(`Password reset email sent to ${params.to}`);
    return true;
  }
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
