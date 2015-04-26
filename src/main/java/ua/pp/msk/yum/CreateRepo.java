/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ua.pp.msk.yum;

import static com.google.common.base.Preconditions.checkState;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import javax.inject.Provider;
import static org.apache.commons.io.FileUtils.deleteQuietly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.proxy.access.Action;
import org.sonatype.nexus.proxy.item.RepositoryItemUid;
import org.sonatype.nexus.proxy.maven.MavenRepository;
import org.sonatype.nexus.proxy.repository.HostedRepository;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.util.file.DirSupport;
import org.sonatype.nexus.yum.Yum;
import static org.sonatype.nexus.yum.Yum.PATH_OF_REPODATA;
import static org.sonatype.nexus.yum.Yum.PATH_OF_REPOMD_XML;
import org.sonatype.nexus.yum.YumHosted;
import org.sonatype.nexus.yum.YumRepository;
import org.sonatype.nexus.yum.internal.YumRepositoryImpl;
import org.sonatype.nexus.yum.internal.createrepo.YumStore;
import static org.sonatype.nexus.yum.Yum.PATH_OF_REPODATA;
import static org.sonatype.nexus.yum.Yum.PATH_OF_REPOMD_XML;
import org.sonatype.nexus.yum.YumRegistry;
import org.sonatype.nexus.yum.internal.RpmScanner;
import org.sonatype.nexus.yum.internal.YumHostedImpl;
import org.sonatype.nexus.yum.internal.createrepo.YumPackage;
import org.sonatype.nexus.yum.internal.createrepo.YumPackageParser;
import org.sonatype.nexus.yum.internal.createrepo.YumStoreFactory;
import org.sonatype.nexus.yum.internal.createrepo.YumStoreFactoryImpl;

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

    private static final Logger LOG = LoggerFactory.getLogger(CreateRepo.class);

    public static void main(String[] args) {
        CreateRepo cr = new CreateRepo();

    }

    private void create() {
        if (repositoryId == null) {
            LOG.error("Metadata regeneration can only be run when repository id is set");
            System.exit(1);
        }

    }

    private String getRpmDir() {
        return rpmDir.getAbsolutePath();
    }

    private void syncYumPackages(final YumStore yumStore) {
        Set<File> files = null;
        File rpmDir = new File(getRpmDir());
        if (true) {
            files = scanner.scan(rpmDir);
            yumStore.deleteAll();
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
                    LOG.warn("Could not parse yum metadata for {}", location, e);
                }
            }
        }
    }

    protected void execute()
            throws Exception {

        LOG.debug("Generating Yum-Repository for '{}' ...", getRpmDir());

        final File repoRepodataDir = new File(repoBaseDir, PATH_OF_REPODATA);
        final File repoTmpDir = new File(repoBaseDir, REPO_TMP_FOLDER + File.separator + UUID.randomUUID().toString());
        DirSupport.mkdir(repoTmpDir);
        final File repoTmpRepodataDir = new File(repoTmpDir, PATH_OF_REPODATA);
        DirSupport.mkdir(repoTmpRepodataDir);

        try {
            YumStoreFactory ysf = new YumStoreFactoryImpl();
            YumStore yumStore = ysf.create("test");
            syncYumPackages(yumStore);
            try (CreateYumRepository createRepo = new CreateYumRepository(repoTmpRepodataDir, null, resolveYumGroups())) {
                String version = getVersion();
                for (YumPackage yumPackage : yumStore.get()) {
                    if (version == null || hasRequiredVersion(version, yumPackage.getLocation())) {
                        createRepo.write(yumPackage);
                    }
                }
            }

            DirSupport.deleteIfExists(repoRepodataDir.toPath());
            DirSupport.moveIfExists(repoTmpRepodataDir.toPath(), repoRepodataDir.toPath());
        } catch (IOException e) {
            LOG.warn("Yum metadata generation failed", e);
            throw new IOException("Yum metadata generation failed", e);
        } finally {
            deleteQuietly(repoTmpDir);
        }
    }

}
