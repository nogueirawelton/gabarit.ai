<#ftl output_format="plainText">
<#assign firstName = (user.firstName)!"">
${firstName?has_content?then(msg("gbHello", firstName), msg("gbHelloAnonymous"))}

${msg("gbResetText")}

${msg("gbResetButton")}: ${link}

${msg("gbEmailLinkExpires", linkExpirationFormatter(linkExpiration))}
${msg("gbResetNotYou")}

--
gabarit.ai
