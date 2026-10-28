<#ftl output_format="plainText">
<#assign firstName = (user.firstName)!"">
${firstName?has_content?then(msg("gbHello", firstName), msg("gbHelloAnonymous"))}

${msg("gbActionsText")}
<#if requiredActions??><#list requiredActions as action>
- ${msg("requiredAction.${action}")}
</#list></#if>

${msg("gbActionsButton")}: ${link}

${msg("gbEmailLinkExpires", linkExpirationFormatter(linkExpiration))}

--
gabarit.ai
