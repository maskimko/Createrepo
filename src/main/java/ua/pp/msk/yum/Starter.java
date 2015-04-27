/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package ua.pp.msk.yum;

import java.io.File;
import java.io.FileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.yum.internal.RpmScanner;
import org.sonatype.sisu.resource.scanner.Listener;
import org.sonatype.sisu.resource.scanner.Scanner;

/**
 *
 * @author Maksym Shkolnyi aka maskimko
 */
public class Starter {
    
    private static final Logger LOG = LoggerFactory.getLogger(Starter.class);
    
    public static void main(String[] args) throws Exception {
        Scanner s = new Scanner() {

            @Override
            public void scan(File arg0, Listener arg1) {
              scan(arg0, arg1, null);
            }

            @Override
            public void scan(File dir, Listener arg1, FileFilter arg2) {
                System.out.println("Scanner: " + dir.getAbsolutePath());
                if (!dir.isDirectory()) {
                    System.err.println("File " + dir.getAbsolutePath() + " is not a directory");
                    return;
                }
                if (!dir.canRead() ){
                    System.err.println("There is no read permission on " + dir.getAbsolutePath() );
                    return;
                }
                if (!dir.canExecute() ){
                    System.err.println("There is no execute permission on " + dir.getAbsolutePath() );
                    return;
                }
                //TODO implement filefilter
                String[] fileList = dir.list();
               for (String fileName : fileList) {
                   File currentFile = new File(dir.getAbsolutePath() + File.separator + fileName);
                   if (currentFile.exists()) {
                       arg1.onFile(currentFile);
                   }
               }
                
            }
        };
        RpmScanner scanner = new RpmScanner(s);
        
        CreateRepo cr = new CreateRepo("test", new File("/tmp/testrepo"), new File("/tmp/testrepo"), scanner);
//        LOG.debug("Executing repo creation");
        cr.execute();
    }
}
