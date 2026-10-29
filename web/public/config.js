// Configuração de runtime do web. Em dev vale este arquivo; na imagem de produção ele é
// gerado no boot do container a partir das variáveis de ambiente (config.js.template).
window.__GABARIT_CONFIG__ = {
  keycloakUrl: "http://localhost:8180",
  keycloakRealm: "gabaritai",
  keycloakClientId: "web",
  apiUrl: "http://localhost:8080",
};
