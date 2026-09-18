package se.lth.math.videoimucapture;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Publishes completed TXT files outside Android/data so MTP and file managers can read them. */
final class RecordingExporter {
    private static final String EXPORT_ROOT = "VideoIMUCapture";
    private static final String TEXT_FILE_NAME = "video_meta.txt";
    private static final String DOWNLOAD_DIRECTORY = "Download";

    private RecordingExporter() {}

    static String exportText(Context context, String sourcePath) throws IOException {
        File source = new File(sourcePath);
        if (!source.isFile()) throw new IOException("TXT source file does not exist");
        String sessionName = source.getParentFile() == null
                ? "recording" : source.getParentFile().getName();
        String relativePath = publicRelativePath(sessionName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return exportThroughMediaStore(context, source, relativePath);
        }
        return exportToLegacyDownloads(source, relativePath);
    }

    static String publicRelativePath(String sessionName) {
        return DOWNLOAD_DIRECTORY + "/" + EXPORT_ROOT + "/" + sessionName;
    }

    private static String exportThroughMediaStore(Context context, File source,
                                                   String relativePath) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, TEXT_FILE_NAME);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Cannot create public TXT file");
        boolean completed = false;
        try {
            try (InputStream input = new FileInputStream(source);
                 OutputStream output = resolver.openOutputStream(uri, "w")) {
                if (output == null) throw new IOException("Cannot open public TXT file");
                copy(input, output);
                output.flush();
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(uri, ready, null, null) <= 0) {
                throw new IOException("Cannot publish public TXT file");
            }
            completed = true;
        } finally {
            if (!completed) resolver.delete(uri, null, null);
        }
        return relativePath + "/" + TEXT_FILE_NAME;
    }

    @SuppressWarnings("deprecation")
    private static String exportToLegacyDownloads(File source, String relativePath)
            throws IOException {
        File directory = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                EXPORT_ROOT + "/" + (source.getParentFile() == null
                        ? "recording" : source.getParentFile().getName()));
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create public TXT directory");
        }
        File destination = new File(directory, TEXT_FILE_NAME);
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(destination)) {
            copy(input, output);
            output.flush();
        }
        return relativePath + "/" + TEXT_FILE_NAME;
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }
}
