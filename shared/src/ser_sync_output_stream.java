import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Binary output stream for writing Serato crate files.
 */
public class ser_sync_output_stream extends DataOutputStream {

    public ser_sync_output_stream(OutputStream out) {
        super(new BufferedOutputStream(out));
    }

    /**
     * Writes a string as UTF-16.
     */
    public void writeUTF16(String str) throws ser_sync_exception {
        try {
            writeChars(str);
        } catch (IOException e) {
            throw new ser_sync_exception(e);
        }
    }

    /**
     * Writes a long value as specified number of bytes.
     */
    public void writeLong(long value, int bytes) throws ser_sync_exception {
        byte[] data = new byte[bytes];
        for (int i = 0; i < bytes; i++) {
            data[bytes - 1 - i] = (byte) (value & 0xFF);
            value >>>= 8;
        }
        for (byte v : data) {
            try {
                write(v);
            } catch (IOException e) {
                throw new ser_sync_exception(e);
            }
        }
    }
}
