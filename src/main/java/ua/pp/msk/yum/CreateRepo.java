/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ua.pp.msk.yum;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Set;
import java.util.UUID;
import org.sonatype.nexus.common.io.DirSupport;
import org.sonatype.nexus.yum.internal.createrepo.YumStore;
import static org.sonatype.nexus.yum.Yum.PATH_OF_REPODATA;
import org.sonatype.nexus.yum.YumRegistry;
import org.sonatype.nexus.yum.internal.RpmScanner;
import ua.pp.msk.yum.createrepoutils.YumPackage;
import ua.pp.msk.yum.createrepoutils.YumPackageParser;
import org.sonatype.nexus.yum.internal.createrepo.YumStoreFactory;
import org.sonatype.nexus.yum.internal.createrepo.YumStoreFactoryImpl;
import ua.pp.msk.yum.createrepoutils.CreateYumRepository;

/**
 *
 * @author maskimko
 */
public class CreateRepo {

    private String repositoryId;
    private File rpmDir;
    private File repoBaseDir;
    private YumRegistry yumRegistry;
    private RpmScanner scanner;
    private static final String REPO_TMP_FOLDER = "/tmp/createrepo";

//    private static final Logger LOG = LoggerFactory.getLogger(CreateRepo.class);

    public CreateRepo(String repositoryId, File rpmDir, File repoBaseDir, RpmScanner scanner) {
        this.repositoryId = repositoryId;
        this.rpmDir = rpmDir;
        this.repoBaseDir = repoBaseDir;
        this.scanner = scanner;
    }

    
    
    
    public void setRepositoryId(String repositoryId) {
        this.repositoryId = repositoryId;
    }

    public void setRpmDir(File rpmDir) {
        this.rpmDir = rpmDir;
    }

    public void setRepoBaseDir(File repoBaseDir) {
        this.repoBaseDir = repoBaseDir;
    }

  



    private String getRpmDir() {
        return rpmDir.getAbsolutePath();
    }

    private void syncYumPackages(final YumStore yumStore) {
        Set<File> files = null;
        File rpmDir = new File(getRpmDir());
        if (true) {
            files = scanner.scan(rpmDir);
        }

        //TODO improve speed
//        else if (getAddedFiles() != null) {
//            String[] addedFiles = getAddedFiles().split(File.pathSeparator);
//            files = Sets.newHashSet();
//            for (String addedFile : addedFiles) {
//                files.add(new File(rpmDir, addedFile));
//            }
//        }
        if (files != null) {
            for (File file : files) {
                String location = RpmScanner.getRelativePath(rpmDir, file.getAbsoluteFile());
                try {
                    YumPackage yumPackage = new YumPackageParser().parse(
                            new FileInputStream(file), location, file.lastModified()
                    );
                    yumStore.put(yumPackage);
                } catch (FileNotFoundException e) {
//                    LOG.warn("Could not parse yum metadata for {}", location, e);
                }
            }
        }
    }

    protected void execute()
            throws Exception {

       
//        LOG.debug("Generating Yum-Repository for '{}' ...", getRpmDir());

        final File repoRepodataDir = new File(repoBaseDir, PATH_OF_REPODATA);
        final File repoTmpDir = new File(repoBaseDir, REPO_TMP_FOLDER + File.separator + UUID.randomUUID().toString());
        DirSupport.mkdir(repoTmpDir);
        final File repoTmpRepodataDir = new File(repoTmpDir, PATH_OF_REPODATA);
        DirSupport.mkdir(repoTmpRepodataDir);

        YumStoreFactory ysf = new YumStoreFactoryImpl();
        YumStore yumStore = ysf.create("test");
        syncYumPackages(yumStore);
        CreateYumRepository createRepo = null;

        createRepo = new CreateYumRepository(repoTmpRepodataDir, null, null);
        for (YumPackage yumPackage : yumStore.get()) {

            createRepo.write(yumPackage);
        }

        DirSupport.deleteIfExists(repoRepodataDir.toPath());
        DirSupport.moveIfExists(repoTmpRepodataDir.toPath(), repoRepodataDir.toPath());

    }

}
