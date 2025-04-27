import socket

LISTEN_IP = "0.0.0.0"
LISTEN_PORT = 5000  # Porta onde o receiver vai escutar

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind((LISTEN_IP, LISTEN_PORT))

print(f"Escutando UDP na porta {LISTEN_PORT}...")

while True:
    data, addr = sock.recvfrom(1024)
    print(f"Recebido de {addr}: {data.decode()}")
