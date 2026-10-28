<#--
  Layout de todos os e-mails HTML do gabarit.ai.
  E-mail não é página web: tabelas, estilos inline, sem SVG nem CSS externo (Gmail/Outlook ignoram).
  Os e-mails do Keycloak que não têm template próprio aqui chamam <@layout.emailLayout> sem
  parâmetros e também ganham este visual.
-->
<#macro emailLayout preheader="">
<!DOCTYPE html>
<html lang="${locale.language}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="color-scheme" content="light">
  <meta name="supported-color-schemes" content="light">
  <title>gabarit.ai</title>
</head>
<body style="margin:0;padding:0;background-color:#f6f7fb;-webkit-text-size-adjust:100%;">
  <#if preheader?has_content>
  <#-- resumo que aparece na lista de e-mails, ao lado do assunto -->
  <div style="display:none;max-height:0;overflow:hidden;mso-hide:all;">${preheader}&#847;&zwnj;&nbsp;&#847;&zwnj;&nbsp;&#847;&zwnj;&nbsp;</div>
  </#if>
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" bgcolor="#f6f7fb" style="background-color:#f6f7fb;">
    <tr>
      <td align="center" style="padding:40px 16px;">
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;">

          <#-- marca -->
          <tr>
            <td style="padding:0 8px 24px 8px;">
              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td width="32" height="32" align="center" valign="middle" bgcolor="#6366f1"
                      style="width:32px;height:32px;background-color:#6366f1;background-image:linear-gradient(135deg,#6366f1,#8b5cf6);border-radius:9px;color:#ffffff;font-family:Arial,Helvetica,sans-serif;font-size:18px;font-weight:bold;line-height:32px;">&#10003;</td>
                  <td style="padding-left:10px;font-family:Inter,-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:20px;font-weight:700;letter-spacing:-0.3px;color:#0b1020;">gabarit<span style="color:#4f46e5;">.ai</span></td>
                </tr>
              </table>
            </td>
          </tr>

          <#-- cartão -->
          <tr>
            <td bgcolor="#ffffff" style="background-color:#ffffff;border:1px solid #e3e6ee;border-radius:16px;padding:40px 36px;font-family:Inter,-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:16px;line-height:26px;color:#3f4657;">
              <#nested>
            </td>
          </tr>

          <#-- rodapé -->
          <tr>
            <td align="center" style="padding:24px 16px 0 16px;font-family:Inter,-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:13px;line-height:20px;color:#8a92a3;">
              ${msg("gbEmailFooterTagline")}<br>
              ${msg("gbEmailFooterAuto")}
            </td>
          </tr>

        </table>
      </td>
    </tr>
  </table>
</body>
</html>
</#macro>

<#-- Título do cartão -->
<#macro title text>
<h1 style="margin:0 0 16px 0;font-size:24px;line-height:32px;font-weight:700;letter-spacing:-0.4px;color:#0b1020;">${text}</h1>
</#macro>

<#-- Botão "à prova de Outlook": tabela com fundo, não só <a> estilizado -->
<#macro button href label>
<table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:28px 0;">
  <tr>
    <td align="center" bgcolor="#4f46e5" style="background-color:#4f46e5;border-radius:10px;">
      <a href="${href}" target="_blank"
         style="display:inline-block;padding:14px 28px;font-family:Inter,-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:16px;font-weight:600;line-height:20px;color:#ffffff;text-decoration:none;border-radius:10px;">${label}</a>
    </td>
  </tr>
</table>
</#macro>

<#-- Validade do link + link em texto (quando o botão não funciona) -->
<#macro linkFooter href expiration>
<p style="margin:0 0 24px 0;font-size:14px;line-height:22px;color:#5b6475;">${msg("gbEmailLinkExpires", expiration)}</p>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
  <tr><td style="border-top:1px solid #e3e6ee;font-size:0;line-height:0;">&nbsp;</td></tr>
</table>
<p style="margin:20px 0 6px 0;font-size:12px;line-height:18px;color:#8a92a3;">${msg("gbEmailButtonFallback")}</p>
<p style="margin:0;font-size:11px;line-height:16px;word-break:break-all;"><a href="${href}" target="_blank" style="color:#8a92a3;text-decoration:underline;">${href}</a></p>
</#macro>
