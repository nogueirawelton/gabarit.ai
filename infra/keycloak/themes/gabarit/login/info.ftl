<#--
  Tela de informação (ex.: confirmar e-mail aberto em outro navegador, "tudo pronto" depois de criar a
  senha). Baseada no info.ftl do tema base (Keycloak 26.8): mesma lógica de links, mas como botões
  e com texto próprio no lugar de "« Voltar para o aplicativo" / "» Clique aqui para continuar".
-->
<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        <#if messageHeader??>
            ${kcSanitize(msg("${messageHeader}"))?no_esc}
        <#else>
            ${message.summary}
        </#if>
    <#elseif section = "form">
    <div id="kc-info-message" class="gb-info-message">
        <#-- com messageHeader, o resumo vira o texto; sem ele, o resumo já é o título -->
        <#if messageHeader?? || requiredActions??>
            <p class="instruction">${message.summary}<#if requiredActions??><#list requiredActions>: <b><#items as reqActionItem>${kcSanitize(msg("requiredAction.${reqActionItem}"))?no_esc}<#sep>, </#items></b></#list></#if></p>
        </#if>
        <#if !skipLink??>
            <#if pageRedirectUri?has_content>
                <a class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}" href="${pageRedirectUri}">${msg("backToApplication")}</a>
            <#elseif actionUri?has_content>
                <a class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}" href="${actionUri}">
                    <#-- no Keycloak 26 o messageHeader já chega traduzido: compara com o texto -->
                    <#if (messageHeader!"") == msg("confirmEmailAddressVerificationHeader")>${msg("gbConfirmEmail")}<#else>${msg("proceedWithAction")}</#if>
                </a>
            <#elseif (client.baseUrl)?has_content>
                <a class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonBlockClass!}" href="${client.baseUrl}">${msg("backToApplication")}</a>
            </#if>
        </#if>
    </div>
    </#if>
</@layout.registrationLayout>
