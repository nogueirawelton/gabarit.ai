# One-shot que importa a topologia compartilhada (definitions.json) pela API do management.
# Arquivos dentro da imagem: produção não depende de arquivo do repositório montado.
FROM rabbitmq:3.13.7-management
COPY --chmod=755 init.sh /init.sh
COPY definitions.json /definitions.json
# rabbitmqadmin só fala HTTP com o broker: não precisa de root
USER rabbitmq
ENTRYPOINT ["/init.sh"]
