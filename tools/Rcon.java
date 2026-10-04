import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Tiny RCON client for driving the local dev server from scripts.
 * Usage: java tools/Rcon.java <port> <password> "<command>" ["<command>" ...]
 */
public class Rcon {
    public static void main(String[] args) throws Exception {
        try (Socket s = new Socket("localhost", Integer.parseInt(args[0]))) {
            OutputStream out = s.getOutputStream();
            DataInputStream in = new DataInputStream(s.getInputStream());
            send(out, 1, 3, args[1]);
            if (read(in) == -1) throw new IllegalStateException("RCON auth failed");
            for (int i = 2; i < args.length; i++) {
                send(out, i, 2, args[i]);
                read(in);
            }
        }
    }

    static void send(OutputStream out, int id, int type, String body) throws Exception {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(14 + b.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(10 + b.length).putInt(id).putInt(type).put(b).put((byte) 0).put((byte) 0);
        out.write(buf.array());
        out.flush();
    }

    static int read(DataInputStream in) throws Exception {
        byte[] hdr = new byte[4];
        in.readFully(hdr);
        int len = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] rest = new byte[len];
        in.readFully(rest);
        ByteBuffer r = ByteBuffer.wrap(rest).order(ByteOrder.LITTLE_ENDIAN);
        int id = r.getInt();
        r.getInt();
        String body = new String(rest, 8, len - 10, StandardCharsets.UTF_8);
        if (!body.isEmpty()) System.out.println(body);
        return id;
    }
}
