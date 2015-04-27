/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ua.pp.msk.yum.helper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 *
 * @author maskimko
 */
public class DirSupport {
    
    public static void mkdir(File dir) {
        if (dir.isFile()) {
            System.err.println("Removingn file, and creating directory instead");
            boolean delete = dir.delete();
            if (delete) {
                boolean mkdir = dir.mkdir();
                if (mkdir) {
                    System.out.println("Directory " + dir.getAbsolutePath() + " has been successfuly created");
                }
            } else {
                System.err.println("Cannot delete file " + dir.getAbsolutePath());
            }
        }
    }
    
    public static void deleteIfExists(File file) throws IOException {
        if (file.isDirectory()) {            
            deleteDir(file);
        } else {
            Files.deleteIfExists(file.toPath());
        }
    }
    
    public static void moveIfExists(File source, File destination) throws IOException {
        if (source.exists()) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }
    
    private static void deleteDir(File file) throws IOException {
        if (!file.isDirectory()) {
            System.err.println("This method supposed to delete directories only");
        } else {
            
            File[] listFiles = file.listFiles();
            
            for (File f : listFiles) {
                deleteIfExists(f);
            }
            if (listFiles.length == 0) {
                file.delete();
            }
        }
    }
}
