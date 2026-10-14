package us.pochaev.dfs.scanner.infra.adapter;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Windows file identity through the FFM API (DFS-026): opens the file once (attributes only, all
 * share modes, {@code FILE_FLAG_BACKUP_SEMANTICS}), calls
 * {@code GetFileInformationByHandleEx(FileIdInfo)} and closes the handle. The key is the 64-bit
 * volume serial number and the 128-bit file ID of {@code FILE_ID_INFO}; {@code FileIdInfo} rather
 * than {@code GetFileInformationByHandle}, whose 64-bit file index is not guaranteed unique on ReFS.
 * <p>
 * Only for Windows paths of the default file system; {@link NioFileIdentity} decides when to call
 * it. Holds no per-scan state: the native method handles are shared, immutable and thread-safe, and
 * each call allocates in its own confined {@link Arena}.
 */
final class WindowsFileId {

    private static final int FILE_READ_ATTRIBUTES = 0x80;
    private static final int FILE_SHARE_READ_WRITE_DELETE = 0x1 | 0x2 | 0x4;
    private static final int OPEN_EXISTING = 3;
    /** Needed to open directories, harmless for files. No {@code FILE_FLAG_OPEN_REPARSE_POINT}: links are followed. */
    private static final int FILE_FLAG_BACKUP_SEMANTICS = 0x02000000;
    /** {@code FILE_INFO_BY_HANDLE_CLASS.FileIdInfo}. */
    private static final int FILE_ID_INFO_CLASS = 18;
    /** {@code sizeof(FILE_ID_INFO)}: a {@code ULONGLONG} volume serial number, then 16 ID bytes. */
    private static final int FILE_ID_INFO_SIZE = 24;
    private static final long INVALID_HANDLE_VALUE = -1L;

    private static final int ERROR_FILE_NOT_FOUND = 2;
    private static final int ERROR_PATH_NOT_FOUND = 3;
    private static final int ERROR_ACCESS_DENIED = 5;

    private static final String REFUSED_MESSAGE =
            "native access refused; run the JVM with --enable-native-access=ALL-UNNAMED (or the module name)";

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
        Objects.requireNonNull(path, "path");
        Natives natives = Natives.get();
        String file = path.toString();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment name = arena.allocateFrom(win32Path(path), StandardCharsets.UTF_16LE);
            MemorySegment state = arena.allocate(Natives.CAPTURE_LAYOUT);

            MemorySegment handle = natives.createFile(state, name);
            if (handle.address() == INVALID_HANDLE_VALUE || handle.address() == 0L) {
                throw failure(file, "CreateFileW", lastError(state));
            }
            try {
                MemorySegment info = arena.allocate(FILE_ID_INFO_SIZE, Long.BYTES);
                if (natives.getFileIdInfo(state, handle, info) == 0) {
                    throw failure(file, "GetFileInformationByHandleEx(FileIdInfo)", lastError(state));
                }
                return new Key(
                        info.get(ValueLayout.JAVA_LONG, 0),
                        info.get(ValueLayout.JAVA_LONG, 8),
                        info.get(ValueLayout.JAVA_LONG, 16));
            } finally {
                natives.closeHandle(handle);
            }
        }
    }

    /**
     * The absolute, normalized path with the {@code \\?\} (or {@code \\?\UNC\}) prefix, so paths of
     * 260 characters or more work. Windows does not normalize prefixed paths, hence
     * {@link Path#normalize()}.
     */
    private static String win32Path(Path path) {
        String absolute = path.toAbsolutePath().normalize().toString();
        if (absolute.startsWith("\\\\?\\") || absolute.startsWith("\\\\.\\")) {
            return absolute;
        }
        if (absolute.startsWith("\\\\")) {
            return "\\\\?\\UNC\\" + absolute.substring(2);
        }
        return "\\\\?\\" + absolute;
    }

    private static int lastError(MemorySegment state) {
        return (int) Natives.GET_LAST_ERROR.get(state, 0L);
    }

    private static IOException failure(String file, String call, int error) {
        String reason = call + " failed, Win32 error " + error;
        return switch (error) {
            case ERROR_FILE_NOT_FOUND, ERROR_PATH_NOT_FOUND -> new NoSuchFileException(file, null, reason);
            case ERROR_ACCESS_DENIED -> new AccessDeniedException(file, null, reason);
            default -> new FileSystemException(file, null, reason);
        };
    }

    /**
     * The key: {@code FILE_ID_INFO.VolumeSerialNumber} and the two halves of
     * {@code FILE_ID_INFO.FileId} (bytes 0 to 7 and 8 to 15 of the 128-bit ID). Value
     * {@code equals}/{@code hashCode} from the record.
     */
    private record Key(long volumeSerialNumber, long fileIdLow, long fileIdHigh) {
    }

    /**
     * The kernel32 downcall handles, linked once on first use (only ever touched on Windows). A
     * refusal of native access ({@code IllegalCallerException}, raised by the restricted lookup and
     * linking methods) is kept rather than escaping the static initializer, which would surface as
     * {@code ExceptionInInitializerError} and then {@code NoClassDefFoundError}; every call then
     * throws an {@link IllegalStateException} naming {@code --enable-native-access}.
     */
    private static final class Natives {

        static final StructLayout CAPTURE_LAYOUT = Linker.Option.captureStateLayout();
        static final VarHandle GET_LAST_ERROR =
                CAPTURE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));

        private static final Natives INSTANCE;
        private static final RuntimeException FAILURE;

        static {
            Natives instance = null;
            RuntimeException failure = null;
            try {
                instance = new Natives();
            } catch (IllegalCallerException e) {
                failure = e;
            } catch (RuntimeException e) {
                failure = new IllegalStateException("cannot link kernel32 for file IDs: " + e.getMessage(), e);
            }
            INSTANCE = instance;
            FAILURE = failure;
        }

        private final MethodHandle createFileW;
        private final MethodHandle getFileInformationByHandleEx;
        private final MethodHandle closeHandle;

        private Natives() {
            Linker linker = Linker.nativeLinker();
            SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
            Linker.Option lastError = Linker.Option.captureCallState("GetLastError");
            createFileW = linker.downcallHandle(
                    kernel32.findOrThrow("CreateFileW"),
                    FunctionDescriptor.of(ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS),
                    lastError);
            getFileInformationByHandleEx = linker.downcallHandle(
                    kernel32.findOrThrow("GetFileInformationByHandleEx"),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT),
                    lastError);
            closeHandle = linker.downcallHandle(
                    kernel32.findOrThrow("CloseHandle"),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        }

        /** The linked handles, or a fresh exception for a failed link (a refusal names the flag). */
        static Natives get() {
            if (FAILURE instanceof IllegalCallerException refusal) {
                throw new IllegalStateException(REFUSED_MESSAGE, refusal);
            }
            if (FAILURE != null) {
                throw new IllegalStateException(FAILURE.getMessage(), FAILURE.getCause());
            }
            return INSTANCE;
        }

        MemorySegment createFile(MemorySegment state, MemorySegment name) {
            try {
                return (MemorySegment) createFileW.invokeExact(state, name, FILE_READ_ATTRIBUTES,
                        FILE_SHARE_READ_WRITE_DELETE, MemorySegment.NULL, OPEN_EXISTING,
                        FILE_FLAG_BACKUP_SEMANTICS, MemorySegment.NULL);
            } catch (Throwable e) {
                throw rethrow(e);
            }
        }

        int getFileIdInfo(MemorySegment state, MemorySegment handle, MemorySegment info) {
            try {
                return (int) getFileInformationByHandleEx.invokeExact(state, handle, FILE_ID_INFO_CLASS,
                        info, (int) info.byteSize());
            } catch (Throwable e) {
                throw rethrow(e);
            }
        }

        void closeHandle(MemorySegment handle) {
            try {
                int ignored = (int) closeHandle.invokeExact(handle);
            } catch (Throwable e) {
                throw rethrow(e);
            }
        }

        /** A refused downcall becomes the flag-naming {@link IllegalStateException}; the rest pass through. */
        private static RuntimeException rethrow(Throwable e) {
            if (e instanceof IllegalCallerException refusal) {
                return new IllegalStateException(REFUSED_MESSAGE, refusal);
            }
            if (e instanceof RuntimeException runtime) {
                return runtime;
            }
            if (e instanceof Error error) {
                throw error;
            }
            return new IllegalStateException(e);
        }
    }
}
