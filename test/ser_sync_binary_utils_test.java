import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ser_sync_binary_utils.
 */
class ser_sync_binary_utils_test {

    @Test
    void readInt_parsesPositiveValue() {
        byte[] data = { 0x00, 0x00, 0x01, 0x00 }; // 256
        assertEquals(256, ser_sync_binary_utils.readInt(data, 0));
    }

    @Test
    void readInt_parsesMaxValue() {
        byte[] data = { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
        assertEquals(-1, ser_sync_binary_utils.readInt(data, 0)); // Two's complement
    }

    @Test
    void readInt_parsesAtOffset() {
        byte[] data = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00 };
        assertEquals(0x00020000, ser_sync_binary_utils.readInt(data, 4));
    }

    @Test
    void getFilename_extractsFromUnixPath() {
        assertEquals("track.mp3", ser_sync_binary_utils.getFilename("/Music/DJ/track.mp3"));
    }

    @Test
    void getFilename_extractsFromWindowsPath() {
        assertEquals("track.mp3", ser_sync_binary_utils.getFilename("C:\\Music\\DJ\\track.mp3"));
    }

    @Test
    void getFilename_lowercases() {
        assertEquals("track.mp3", ser_sync_binary_utils.getFilename("/Music/TRACK.MP3"));
    }

    @Test
    void getFilename_handlesNull() {
        assertEquals("", ser_sync_binary_utils.getFilename(null));
    }

    @Test
    void getFilename_handlesNoSlash() {
        assertEquals("track.mp3", ser_sync_binary_utils.getFilename("track.mp3"));
    }

    @Test
    void getRawFilename_preservesCase() {
        assertEquals("TRACK.MP3", ser_sync_binary_utils.getRawFilename("/Music/TRACK.MP3"));
    }

    @Test
    void getRawFilename_handlesNull() {
        assertEquals("", ser_sync_binary_utils.getRawFilename(null));
    }

    @Test
    void formatSize_bytes() {
        assertEquals("512 B", ser_sync_binary_utils.formatSize(512));
    }

    @Test
    void formatSize_kilobytes() {
        String result = ser_sync_binary_utils.formatSize(2048);
        assertTrue(result.contains("KB"), "Expected KB but got: " + result);
    }

    @Test
    void formatSize_megabytes() {
        String result = ser_sync_binary_utils.formatSize(5 * 1024 * 1024);
        assertTrue(result.contains("MB"), "Expected MB but got: " + result);
    }

    @Test
    void formatSize_gigabytes() {
        String result = ser_sync_binary_utils.formatSize(2L * 1024 * 1024 * 1024);
        assertTrue(result.contains("GB"), "Expected GB but got: " + result);
    }

    @Test
    void readFile_writeFile_roundTrip() throws Exception {
        java.io.File tmp = java.io.File.createTempFile("ser_sync_test", ".bin");
        tmp.deleteOnExit();

        byte[] expected = { 0x01, 0x02, 0x03, 0x04, 0x05 };
        ser_sync_binary_utils.writeFile(tmp, expected);

        byte[] actual = ser_sync_binary_utils.readFile(tmp);
        assertArrayEquals(expected, actual);
    }
}
