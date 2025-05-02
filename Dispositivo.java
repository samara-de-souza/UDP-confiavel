import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Dispositivo {
    static final int PORTA = 5000;
    static String meuNome;
    static DatagramSocket socket;
    static ConcurrentHashMap<String, Vizinho> vizinhos = new ConcurrentHashMap<>();

    static class Vizinho {
        String ip;
        long ultimoHeartbeat;
        Vizinho(String ip) {
            this.ip = ip;
            this.ultimoHeartbeat = System.currentTimeMillis();
        }
    }

    public static void main(String[] args) throws Exception {
        BufferedReader leitor = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("nome do dispositivo: ");
        meuNome = leitor.readLine();

        socket = new DatagramSocket(PORTA);
        socket.setBroadcast(true);

        new Thread(() -> escutar()).start();
        new Thread(() -> heartbeat()).start();
        new Thread(() -> limparVizinhos()).start();

        while (true) {
            System.out.print("> ");
            String linha = leitor.readLine();
            if (linha.equals("devices")) {
                listarVizinhos();
            } 
            if(linha.startsWith("talk ")) {
                String[] partes = linha.split(" ", 3);
                if (partes.length < 3) {
                    System.out.println("Uso: talk <nome> <mensagem>");
                    continue;
                }
                talk(partes[1], partes[2]);
            }
        }
    }

    static void escutar() {
        byte[] buffer = new byte[1024];
        while (true) {
            try {
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                socket.receive(pacote);
                String recebido = new String(pacote.getData(), 0, pacote.getLength());
                String ip = pacote.getAddress().getHostAddress();

                if (recebido.startsWith("HEARTBEAT:")) {
                    String nome = recebido.split(":")[1];
                    if (!nome.equals(meuNome)) {
                        vizinhos.put(nome, new Vizinho(ip));
                        /* System.out.println("HEARTBEAT de " + nome + " (" + ip + ")"); */
                    }
                } else if (recebido.startsWith("TALK:")) {
                    String[] partes = recebido.split(":", 3);
                    System.out.println("mensagem de " + partes[1] + ": " + partes[2]);
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        } 
    }

    static void heartbeat() {
        while (true) {
            try {
                String mensagem = "HEARTBEAT:" + meuNome;
                byte[] dados = mensagem.getBytes();
                DatagramPacket pacote = new DatagramPacket(dados, dados.length,
                        InetAddress.getByName("255.255.255.255"), PORTA);
                socket.send(pacote);
                Thread.sleep(5000);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }

    static void talk(String nome, String mensagem) {
        Vizinho vizinho = vizinhos.get(nome);
        if (vizinho == null) {
            System.out.println("dispositivo não encontrado: " + nome);
            return;
        }
        String msg = "TALK:" + meuNome + ":" + mensagem;
        try {
            byte[] dados = msg.getBytes();
            DatagramPacket pacote = new DatagramPacket(dados, dados.length,
                    InetAddress.getByName(vizinho.ip), PORTA);
            socket.send(pacote);
            System.out.println("enviado para " + nome);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    static void limparVizinhos() {
        while (true) {
            long agora = System.currentTimeMillis();
            vizinhos.entrySet().removeIf(entry -> (agora - entry.getValue().ultimoHeartbeat) > 15000);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {}
        }
    }

    static void listarVizinhos() {
        System.out.println("dispositivos ativos:");
        vizinhos.forEach((nome, vizinho) -> {
            System.out.println(" - " + nome + " (" + vizinho.ip + ")");
        });
    }
}
