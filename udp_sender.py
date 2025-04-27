import socket
import time

DEST_IP = input("Digite o IP de destino: ")
DEST_PORT = 5000  # Porta fixa para o teste

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

while True:
    mensagem = input("Mensagem para enviar: ")
    sock.sendto(mensagem.encode(), (DEST_IP, DEST_PORT))
    print(f"Mensagem enviada para {DEST_IP}:{DEST_PORT}")
    time.sleep(1)
