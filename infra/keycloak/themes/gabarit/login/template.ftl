<#--
  Layout de todas as telas de login do Keycloak (login, cadastro, recuperar senha, erros...).
  Baseado no template.ftl do tema "base" do Keycloak 26.8: mantém as seções que as páginas usam
  (header, show-username, form, socialProviders, info) e os scripts de sessão; muda só o visual.
-->
<#import "footer.ftl" as loginFooter>
<#import "theme-resources.ftl" as themeResourceTags>
<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html lang="${lang}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="robots" content="noindex, nofollow">
    <meta name="color-scheme" content="light dark">
    <title>${title!} · gabarit.ai</title>
    <link rel="icon" type="image/svg+xml" href="${url.resourcesPath}/img/favicon.svg">
    <link rel="preload" href="${url.resourcesPath}/fonts/inter-latin-wght-normal.woff2" as="font" type="font/woff2" crossorigin>
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet">
        </#list>
    </#if>
    <script type="importmap">
        {
            "imports": {
                "rfc4648": "${url.resourcesCommonPath}/vendor/rfc4648/rfc4648.js"
            }
        }
    </script>
    <#if scripts??>
        <#list scripts as script>
            <script src="${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <script type="module">
        <#outputformat "JavaScript">
        import { startSessionPolling } from ${(url.resourcesPath + "/js/authChecker.js")?c};

        startSessionPolling(
            ${url.ssoLoginInOtherTabsUrl?c}
        );
        </#outputformat>
    </script>
    <script type="module">
        document.addEventListener("click", (event) => {
            const link = event.target.closest("a[data-once-link]");
            if (!link) {
                return;
            }
            if (link.getAttribute("aria-disabled") === "true") {
                event.preventDefault();
                return;
            }
            const { disabledClass } = link.dataset;
            if (disabledClass) {
                link.classList.add(...disabledClass.trim().split(/\s+/));
            }
            link.setAttribute("role", "link");
            link.setAttribute("aria-disabled", "true");
        });
    </script>
    <#if authenticationSession??>
        <script type="module">
            <#outputformat "JavaScript">
            import { checkAuthSession } from ${(url.resourcesPath + "/js/authChecker.js")?c};

            checkAuthSession(
                ${authenticationSession.authSessionIdHash?c}
            );
            </#outputformat>
        </script>
    </#if>
</head>

<body class="gb ${bodyClass}<#if isAppInitiatedAction??> gb-app-body</#if>" data-page-id="login-${pageId}">
<#if isAppInitiatedAction??>
<#--
  Ação pedida pelo app (ex.: "Trocar senha" no perfil): visual de página do app, não de login.
  Mesmo cabeçalho, container e cartão do web (web/src/components/AppLayout.tsx).
-->
<#assign appUrl = (client.baseUrl)!"/">
<div class="gb-app">
    <header class="gb-app-header">
        <div class="gb-app-header-inner">
            <a class="gb-logo" href="${appUrl}" aria-label="gabarit.ai, página inicial">
                <span class="gb-logo-mark"><img src="${url.resourcesPath}/img/mark.svg" alt=""></span>
                <span class="gb-logo-word">gabarit<span>.ai</span></span>
            </a>
        </div>
    </header>

    <main class="gb-app-main">
        <a class="gb-app-crumb" href="${appUrl}perfil">&larr; ${msg("gbBackToProfile")}</a>
        <#if pageId == "login-update-password">
            <h1 id="kc-page-title" class="gb-app-title">${msg("gbChangePasswordTitle")}</h1>
            <p class="gb-app-subtitle">${msg("gbChangePasswordSubtitle")}</p>
        <#else>
            <h1 id="kc-page-title" class="gb-app-title"><#nested "header"></h1>
        </#if>

        <section class="gb-app-card">
            <#if displayMessage && message?has_content && message.type != 'warning'>
                <div class="gb-alert gb-alert--${message.type}" role="alert">
                    <span class="gb-alert-text">${kcSanitize(message.summary)?no_esc}</span>
                </div>
            </#if>
            <#nested "form">
            <#if displayInfo>
                <div id="kc-info" class="gb-info">
                    <#nested "info">
                </div>
            </#if>
        </section>
    </main>
</div>
<#else>
<div class="gb-shell">

    <aside class="gb-brand" aria-hidden="true">
        <div class="gb-brand-glow"></div>
        <div class="gb-logo gb-logo--light">
            <span class="gb-logo-mark"><img src="${url.resourcesPath}/img/mark.svg" alt=""></span>
            <span class="gb-logo-word">gabarit<span>.ai</span></span>
        </div>

        <div class="gb-brand-copy">
            <h2>${msg("gbBrandTitle")}</h2>
            <p>${msg("gbBrandSubtitle")}</p>
            <ul class="gb-brand-list">
                <li>${msg("gbBrandItem1")}</li>
                <li>${msg("gbBrandItem2")}</li>
                <li>${msg("gbBrandItem3")}</li>
            </ul>
        </div>

        <p class="gb-brand-footer">${msg("gbBrandFooter")}</p>
    </aside>

    <main class="gb-panel">
        <div class="gb-card">
            <div class="gb-logo gb-logo--mobile">
                <span class="gb-logo-mark"><img src="${url.resourcesPath}/img/mark.svg" alt=""></span>
                <span class="gb-logo-word">gabarit<span>.ai</span></span>
            </div>

            <header class="gb-card-header">
                <#if !(auth?has_content && auth.showUsername() && !auth.showResetCredentials())>
                    <h1 id="kc-page-title"><#nested "header"></h1>
                <#else>
                    <#nested "show-username">
                    <div id="kc-username" class="gb-username">
                        <span id="kc-attempted-username">${auth.attemptedUsername}</span>
                        <a id="reset-login" href="${url.loginRestartFlowUrl}" aria-label="${msg("restartLoginTooltip")}">${msg("gbChangeUser")}</a>
                    </div>
                </#if>
                <#if displayRequiredFields>
                    <p class="gb-required-note"><span class="required">*</span> ${msg("requiredFields")}</p>
                </#if>
            </header>

            <#-- Ações iniciadas pelo app não devem ver aviso de que precisam completar a ação -->
            <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                <div class="gb-alert gb-alert--${message.type}" role="alert">
                    <span class="gb-alert-text">${kcSanitize(message.summary)?no_esc}</span>
                </div>
            </#if>

            <#nested "form">

            <#if auth?has_content && auth.showTryAnotherWayLink()>
                <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post" class="gb-inline-form">
                    <input type="hidden" name="tryAnotherWay" value="on"/>
                    <a href="#" id="try-another-way"
                       onclick="document.forms['kc-select-try-another-way-form'].requestSubmit();return false;">${msg("doTryAnotherWay")}</a>
                </form>
            </#if>

            <#if switchOrganizationEnabled?? && switchOrganizationEnabled>
                <form id="kc-switch-organization-form" action="${url.loginAction}" method="post" class="gb-inline-form">
                    <input type="hidden" name="switchOrganization" value="true"/>
                    <a href="#" id="switch-organization"
                       onclick="document.forms['kc-switch-organization-form'].requestSubmit();return false;">${msg("doSwitchOrganization")}</a>
                </form>
            </#if>

            <#nested "socialProviders">

            <#if displayInfo>
                <div id="kc-info" class="gb-info">
                    <#nested "info">
                </div>
            </#if>
        </div>

        <footer class="gb-footer">
            <@loginFooter.content/>
            <span>© gabarit.ai</span>
        </footer>
    </main>
</div>
</#if>
</body>
</html>
</#macro>
