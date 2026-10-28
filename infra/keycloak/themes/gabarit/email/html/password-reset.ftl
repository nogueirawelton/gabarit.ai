<#import "template.ftl" as layout>
<#assign firstName = (user.firstName)!"">
<@layout.emailLayout preheader=msg("gbResetPreheader")>
<@layout.title text=msg("gbResetTitle")/>
<p style="margin:0 0 12px 0;">${firstName?has_content?then(msg("gbHello", firstName), msg("gbHelloAnonymous"))}</p>
<p style="margin:0;">${msg("gbResetText")}</p>
<@layout.button href=link label=msg("gbResetButton")/>
<p style="margin:0 0 8px 0;font-size:14px;line-height:22px;color:#5b6475;">${msg("gbResetNotYou")}</p>
<@layout.linkFooter href=link expiration=linkExpirationFormatter(linkExpiration)/>
</@layout.emailLayout>
