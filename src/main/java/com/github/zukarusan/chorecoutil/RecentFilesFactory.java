package com.github.zukarusan.chorecoutil;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class RecentFilesFactory {
    private static final File recent_cache = new File(Paths.get(System.getProperty("user.dir"), ".jchoutil").toString());
    private static final int limit = 5;

    static File getRecentCache() {
        return recent_cache;
    }

    public static LinkedList<File> getRecentFiles() {
        LinkedList<File> recentFiles = new LinkedList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(recent_cache))) {
            String row;
            row = reader.readLine();
            String[] header = row.split(" ");
            if (!header[0].equals("RECENT_TOTAL")) throw new UnsupportedOperationException("Header mismatch");
            int f = 0;
            while ((row = reader.readLine()) != null) {
                recentFiles.add(new File(row));
                f++;
            }
            if (Integer.parseInt(header[1]) != f) throw new IllegalStateException("Total mismatch");
        } catch (IOException e) {
            if (e instanceof FileNotFoundException) {
                System.out.println("Recent Cache not yet specified");
                return recentFiles;
            }
            throw new IllegalStateException("Error reading recent files cache", e);
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalStateException("Corrupted cache file", e);
        }
        recentFiles.removeIf(val -> !val.exists());
        return recentFiles;
    }

    private static boolean validate(LinkedList<File> files, File file) {
        for (File val : files) {
            if (val.getAbsolutePath().equals(file.getAbsolutePath())) {
                files.remove(val);
                files.addFirst(val);
                return false;
            }
        }
        return true;
    }

    public static void prependRecentFile(File file) {
        LinkedList<File> recentFiles = getRecentFiles();
        if (validate(recentFiles, file)) {
            if (recentFiles.size() >= limit) {
                recentFiles.removeLast();
            }
            recentFiles.addFirst(file);
        }
        if (recent_cache.exists())if (!recent_cache.delete()) throw new IllegalStateException("Error in overwriting recent files cache");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(recent_cache))) {
            writer.write("RECENT_TOTAL ");
            writer.write(Integer.toString(recentFiles.size()));
            for (File props : recentFiles) {
                writer.newLine();
                writer.write(props.getAbsolutePath());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error prepending recent files cache", e);
        }
    }
}
