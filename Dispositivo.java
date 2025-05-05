
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public class Dispositivo {
    static final int PORTA = 5000;
    static String meuNome;
    static DatagramSocket socket;
    static int idGlobal = 1;
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
    
            if (linha.startsWith("devices")) {
                devices(); 
            }
            if (linha.startsWith("talk ")) {
                String[] partes = linha.split(" ", 3);
                if (partes.length < 3) {
                    System.out.println("talk <nome> <mensagem>");
                    continue;
                }
                talk(partes[1], partes[2]); 
            }
    
            if (linha.startsWith("file ")) {
                String[] partes = linha.split(" ", 3);
                if (partes.length < 3) {
                    System.out.println("file <nome> <arquivo>");
                    continue;
                }
                file(partes[1], partes[2]); 
            }
        }
    }
    

    static void escutar() {
        byte[] buffer = new byte[2048];
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
                    }
    
                } else if (recebido.startsWith("TALK:")) {
                    String[] partes = recebido.split(":", 4);
                    System.out.println("mensagem de " + partes[2] + ": " + partes[3]);
                    ack(ip, partes[1]); 
    
                } else if (recebido.startsWith("FILE:")) {
                    String[] partes = recebido.split(":", 4);
                    System.out.println("recebendo arquivo: " + partes[2] + " (" + partes[3] + " bytes)");
                    ack(ip, partes[1]); 
    
                } else if (recebido.startsWith("CHUNK:")) {
                    String[] partes = recebido.split(":", 4);
                    System.out.println("CHUNK recebido (id " + partes[1] + ", seq " + partes[2] + ")");
                    ack(ip, partes[1]); 
    
                } else if (recebido.startsWith("END:")) {
                    String[] partes = recebido.split(":", 3);
                    System.out.println("END recebido (hash: " + partes[2] + ")");
                    boolean hashConfere = true; 
                    
                    if (hashConfere) {
                        System.out.println("arquivo recebido (hash válido)");
                        ack(ip, partes[1]); 
                    } else {
                        nack(ip, partes[1], "arquivo corrompido (hash inválido)"); 
                    }
    
                } else if (recebido.startsWith("ACK:")) {
                    System.out.println("ACK recebido para id " + recebido.split(":")[1]);
    
                } else if (recebido.startsWith("NACK:")) {
                    String[] partes = recebido.split(":", 3);
                    System.out.println("NACK recebido para id " + partes[1] + " motivo: " + partes[2]);
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
        int id = idGlobal++;
        Vizinho vizinho = vizinhos.get(nome);
        if (vizinho == null) {
            System.out.println("dispositivo não encontrado: " + nome);
            return;
        }
        String msg = "TALK:" + id + ":" + meuNome + ":" + mensagem;
        try {
            byte[] dados = msg.getBytes();
            DatagramPacket pacote = new DatagramPacket(dados, dados.length,
                    InetAddress.getByName(vizinho.ip), PORTA);
            socket.send(pacote);
            System.out.println("enviado para " + nome + " com id " + id);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    static void file(String nome, String nomeArquivo) {
        int id = idGlobal++;
        Vizinho vizinho = vizinhos.get(nome);
        if (vizinho == null) {
            System.out.println("dispositivo não encontrado: " + nome);
            return;
        }
    
        File arquivo = new File("/app/teste.txt");
        if (!arquivo.exists()) {
            System.out.println("arquivo não encontrado em: " + arquivo);
            return;
        }
    
        long tamanho = arquivo.length();
        String mensagem = "FILE:" + id + ":" + nomeArquivo + ":" + tamanho;
        try {
            byte[] dados = mensagem.getBytes();
            DatagramPacket pacote = new DatagramPacket(dados, dados.length,
                    InetAddress.getByName(vizinho.ip), PORTA);
            socket.send(pacote);
            System.out.println("FILE enviado, aguardando ACK...");

            byte[] buffer = new byte[2048];
            DatagramPacket pacoteAck = new DatagramPacket(buffer, buffer.length);
            socket.receive(pacoteAck);
            String resposta = new String(pacoteAck.getData(), 0, pacoteAck.getLength());
            System.out.println(resposta);

            if (resposta.startsWith("ACK")) {
                System.out.println("ACK recebido para o FILE");
                chunk(vizinho.ip, id, nomeArquivo); 
            } else {
                System.out.println("ACK não recebido corretamente.");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    static void chunk(String ip, int id, String nomeArquivo) {
        try {
            FileInputStream in = new FileInputStream(nomeArquivo);
            byte[] buffer = new byte[512];
            int bytesRead;
            int seq = 0;
    
            while ((bytesRead = in.read(buffer)) != -1) {
                byte[] dadosOriginais = Arrays.copyOf(buffer, bytesRead);
                String base64 = Base64.getEncoder().encodeToString(dadosOriginais);
                String mensagem = "CHUNK:" + id + ":" + seq + ":" + base64;
    
                byte[] dados = mensagem.getBytes();
                DatagramPacket pacote = new DatagramPacket(dados, dados.length,
                        InetAddress.getByName(ip), PORTA);
                socket.send(pacote);
                System.out.println("CHUNK seq " + seq + " enviado");
                seq++;
                Thread.sleep(100); 
            }
    
            in.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
    
    static void end(String ip, int id, String nomeArquivo) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            FileInputStream fis = new FileInputStream(nomeArquivo);
            byte[] buffer = new byte[1024];
            int count;

            while ((count = fis.read(buffer)) > 0) {
                md.update(buffer, 0, count);
            }
            fis.close();

            byte[] hashBytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            String hash = sb.toString();

            String mensagem = "END:" + id + ":" + hash;
            byte[] dados = mensagem.getBytes();
            DatagramPacket pacote = new DatagramPacket(dados, dados.length,
                    InetAddress.getByName(ip), PORTA);
            socket.send(pacote);
            System.out.println("END enviado com hash: " + hash);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
    
    static void ack(String ip, String id) {
        String mensagem = "ACK:" + id;
        try {
            byte[] dados = mensagem.getBytes();
            DatagramPacket pacote = new DatagramPacket(dados, dados.length,
                    InetAddress.getByName(ip), PORTA);
            socket.send(pacote);
            System.out.println("ACK enviado para " + ip + " com id " + id);
        } catch (Exception e) {
            System.out.println("Erro ao enviar ACK: " + e.getMessage());
        }
    }
    
    static void nack(String ip, String id, String motivo) {
        String mensagem = "NACK:" + id + ":" + motivo;
        try {
            byte[] dados = mensagem.getBytes();
            DatagramPacket pacote = new DatagramPacket(dados, dados.length,
                    InetAddress.getByName(ip), PORTA);
            socket.send(pacote);
            System.out.println("NACK enviado para " + ip + " com id " + id + " motivo: " + motivo);
        } catch (Exception e) {
            System.out.println("Erro ao enviar NACK: " + e.getMessage());
        }
    }
    
    static void limparVizinhos() {
        while (true) {
            long agora = System.currentTimeMillis();
            for (String nome : vizinhos.keySet()) {
                if ((agora - vizinhos.get(nome).ultimoHeartbeat) > 15000) {
                    System.out.println("vizinho " + nome + " REMOVIDO");
                    vizinhos.remove(nome);
                }
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {}
        }
    }

    static void devices() {
        System.out.println("dispositivos ativos:");
        vizinhos.forEach((nome, vizinho) -> {
            System.out.println(" - " + nome + " (" + vizinho.ip + ")");
        });
    }
}
