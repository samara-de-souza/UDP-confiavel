# Escolher uma imagem base
FROM ubuntu:22.04

# Atualizar pacotes e instalar dependências
RUN apt update && apt install -y python3 python3-pip iputils-ping net-tools

# Copiar o código para dentro do container
COPY . /app

# Definir o diretório de trabalho
WORKDIR /app