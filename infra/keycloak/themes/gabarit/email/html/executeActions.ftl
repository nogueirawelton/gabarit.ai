<#-- Enviado pelo admin ("pedir ao usuário que..."): confirmar e-mail, trocar senha etc. -->
<#import "template.ftl" as layout>
<#assign firstName = (user.firstName)!"">
<@layout.emailLayout preheader=msg("gbActionsPreheader")>
<@layout.title text=msg("gbActionsTitle")/>
<p style="margin:0 0 12px 0;">${firstName?has_content?then(msg("gbHello", firstName), msg("gbHelloAnonymous"))}</p>
<p style="margin:0 0 8px 0;">${msg("gbActionsText")}</p>
<#if requiredActions??>
<ul style="margin:0;padding-left:20px;">
  <#list requiredActions as action>
  <li style="margin:4px 0;">${msg("requiredAction.${action}")}</li>
  </#list>
</ul>
</#if>
<@layout.button href=link label=msg("gbActionsButton")/>
<@layout.linkFooter href=link expiration=linkExpirationFormatter(linkExpiration)/>
</@layout.emailLayout>
