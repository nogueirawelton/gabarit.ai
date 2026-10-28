<#--
  Tela de erro. Baseada no error.ftl do tema base (Keycloak 26.8): o link para o app vira botão.
-->
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        ${kcSanitize(msg("errorTitle"))?no_esc}
    <#elseif section = "form">
        <div id="kc-error-message" class="gb-info-message">
            <p class="instruction">${kcSanitize(message.summary)?no_esc}</p>
            <#if traceId??>
                <p class="instruction gb-help" id="traceId">${msg("traceIdSupportMessage", traceId)}</p>
            </#if>
            <#if !skipLink?? && client?? && client.baseUrl?has_content>
                <a id="backToApplication" class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}" href="${client.baseUrl}">${msg("backToApplication")}</a>
            </#if>
        </div>
    </#if>
</@layout.registrationLayout>
