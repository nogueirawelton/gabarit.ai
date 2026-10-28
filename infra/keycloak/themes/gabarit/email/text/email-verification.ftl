<#ftl output_format="plainText">
<#assign firstName = (user.firstName)!"">
${firstName?has_content?then(msg("gbHello", firstName), msg("gbHelloAnonymous"))}

${msg("gbVerifyText")}

${msg("gbVerifyButton")}: ${link}

${msg("gbEmailLinkExpires", linkExpirationFormatter(linkExpiration))}
${msg("gbVerifyNotYou")}

--
gabarit.ai
