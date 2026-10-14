package us.pochaev.dfs.scanner.infra.adapter;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Windows file identity through the FFM API (DFS-026): opens the file once (attributes only, all
 * share modes, {@code FILE_FLAG_BACKUP_SEMANTICS}), calls
 * {@code GetFileInformationByHandleEx(FileIdInfo)} and closes the handle. The key is the 64-bit
 * volume serial number and the 128-bit file ID of {@code FILE_ID_INFO}; {@code FileIdInfo} rather
 * than {@code GetFileInformationByHandle}, whose 64-bit file index is not guaranteed unique on ReFS.
 * <p>
 * Only for Windows paths of the default file system; {@link NioFileIdentity} decides when to call
 * it. Holds no per-scan state: the native method handles are shared, immutable and thread-safe.
 */
final class WindowsFileId {

    private WindowsFileId() {
    }

    /**
     * The identity key of the file {@code path} reaches (links followed, as {@code fileKey()} and
     * {@code Files.isSameFile} do).
     *
     * @return a {@link Key}; equal for two hard links of one file, different for distinct files
     * @throws IOException           if the file cannot be opened or queried; the message carries the
     *                               Win32 error (from {@code GetLastError}, captured by the call)
     * @throws IllegalStateException if the JVM refuses native access ({@code IllegalCallerException});
     *                               the message contains {@code --enable-native-access}
     */
    static Object key(Path path) throws IOException {
        throw new UnsupportedOperationException("not implemented: DFS-026");
    }

    /**
     * The key: {@code FILE_ID_INFO.VolumeSerialNumber} and the two halves of
     * {@code FILE_ID_INFO.FileId} (bytes 0 to 7 and 8 to 15 of the 128-bit ID). Value
     * {@code equals}/{@code hashCode} from the record.
     */
    private record Key(long volumeSerialNumber, long fileIdLow, long fileIdHigh) {
    }
}
