# Uma thread para ficar escutando mensagens UDP.

# Outra thread para enviar mensagens HEARTBEAT automaticamente.

# Um menu interativo para o usuário digitar comandos (talk, devices, sendfile).

# Controle de tabela de vizinhos (nome, IP, porta, último heartbeat).

# Implementação manual de ACK/NACK para garantir confiabilidade

import socket
import threading
import time
import json

PORTA = 5000
MEU_NOME = input("Digite o nome do dispositivo: ")
TABELA_VIZINHOS = {}

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind(("", PORTA))
sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

def enviar_heartbeat():
    while True:
        mensagem = {
            "tipo": "HEARTBEAT",
            "nome": MEU_NOME
        }
        sock.sendto(json.dumps(mensagem).encode(), ("<broadcast>", PORTA))
        time.sleep(5)

def escutar_mensagens():
    while True:
        try:
            data, addr = sock.recvfrom(1024)
            mensagem = json.loads(data.decode())
            ip_remetente = addr[0]

            if mensagem["tipo"] == "HEARTBEAT":
                nome_vizinho = mensagem["nome"]
                TABELA_VIZINHOS[nome_vizinho] = (ip_remetente, time.time())
                print(f"HEARTBEAT recebido de {nome_vizinho} ({ip_remetente})")
            elif mensagem["tipo"] == "TALK":
                print(f"Mensagem de {mensagem['nome']}: {mensagem['mensagem']}")
            # adicionar + tipos (SEND, FILE, ACK, etc.)

        except Exception as e:
            print(f"Erro ao receber mensagem: {e}")

def limpar_vizinhos():
    while True:
        agora = time.time()
        remover = []
        for nome, (ip, ultimo_heartbeat) in TABELA_VIZINHOS.items():
            if agora - ultimo_heartbeat > 15:  
                remover.append(nome)
        for nome in remover:
            print(f"Vizinho {nome} removido (timeout)")
            del TABELA_VIZINHOS[nome]
        time.sleep(5)

def menu():
    while True:
        comando = input("> ").strip()
        if comando == "devices":
            print("Dispositivos ativos:")
            for nome, (ip, _) in TABELA_VIZINHOS.items():
                print(f" - {nome} ({ip})")
        elif comando.startswith("talk "):
            partes = comando.split(" ", 2)
            if len(partes) < 3:
                print("Uso: talk <nome> <mensagem>")
                continue
            nome_destino, mensagem_texto = partes[1], partes[2]
            if nome_destino not in TABELA_VIZINHOS:
                print(f"Dispositivo {nome_destino} não encontrado.")
                continue
            ip_destino = TABELA_VIZINHOS[nome_destino][0]
            mensagem = {
                "tipo": "TALK",
                "nome": MEU_NOME,
                "mensagem": mensagem_texto
            }
            sock.sendto(json.dumps(mensagem).encode(), (ip_destino, PORTA))
            print(f"Mensagem enviada para {nome_destino}.")

threading.Thread(target=enviar_heartbeat, daemon=True).start()
threading.Thread(target=escutar_mensagens, daemon=True).start()
threading.Thread(target=limpar_vizinhos, daemon=True).start()

menu()