-- Foto de perfil: o arquivo fica no MinIO (bucket avatars); aqui só a chave do objeto, nunca a URL
-- (a URL é pré-assinada a cada leitura e expira). Nulo = sem foto.
ALTER TABLE user_profile ADD COLUMN avatar_key TEXT;
