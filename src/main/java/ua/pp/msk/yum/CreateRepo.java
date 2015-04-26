/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ua.pp.msk.yum;

import static com.google.common.base.Preconditions.checkState;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
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
import org.sonatype.nexus.yum.internal.YumHostedImpl;
/**
 *
 * @author maskimko
 */
public class CreateRepo {
    
    private String repositoryId;
    private File rpmDir;
    private File repoBaseDir;
    private YumRegistry yumRegistry;
     private static final String REPO_TMP_FOLDER = "/tmp/createrepo";
     
    private static final Logger LOG = LoggerFactory.getLogger(CreateRepo.class);
    
    public static void main(String[] args) {
        CreateRepo cr = new CreateRepo();
        
    }
    
    private void create(){
        if (repositoryId == null) {
            LOG.error("Metadata regeneration can only be run when repository id is set");
            System.exit(1);
        }
        
        
    }
    
    private String getRpmDir(){
        return rpmDir.getAbsolutePath();
    }
    
  protected YumRepository execute()
      throws Exception
  {
   

    YumHosted yum = new YumHostedImpl(null,null, null, null, null, null, null);

    

      LOG.debug("Generating Yum-Repository for '{}' ...", getRpmDir());

      final File repoRepodataDir = new File(repoBaseDir, PATH_OF_REPODATA);
      final File repoTmpDir = new File(repoBaseDir, REPO_TMP_FOLDER + File.separator + UUID.randomUUID().toString());
      DirSupport.mkdir(repoTmpDir);
      final File repoTmpRepodataDir = new File(repoTmpDir, PATH_OF_REPODATA);
      DirSupport.mkdir(repoTmpRepodataDir);

      try {
        YumStore yumStore = ((YumHosted) yum).getYumStore();
        syncYumPackages(yumStore);
        try (CreateYumRepository createRepo = new CreateYumRepository(repoTmpRepodataDir, null, resolveYumGroups())) {
          String version = getVersion();
          for (YumPackage yumPackage : yumStore.get()) {
            if (version == null || hasRequiredVersion(version, yumPackage.getLocation())) {
              createRepo.write(yumPackage);
            }
          }
        }

        // at the end check for cancellation
        CancelableSupport.checkCancellation();
        // got here, not canceled, move results to proper place
        DirSupport.deleteIfExists(repoRepodataDir.toPath());
        DirSupport.moveIfExists(repoTmpRepodataDir.toPath(), repoRepodataDir.toPath());
      }
      catch (IOException e) {
        LOG.warn("Yum metadata generation failed", e);
        throw new IOException("Yum metadata generation failed", e);
      }
      finally {
        deleteQuietly(repoTmpDir);
      }
    }
    finally {
      mdUid.getLock().unlock();
    }

    // TODO dubious
    Thread.sleep(100);

    final MavenRepository mavenRepository = repository.adaptToFacet(MavenRepository.class);
    if (mavenRepository != null) {
      try {
        routingManager.forceUpdatePrefixFile(mavenRepository);
      }
      catch (Exception e) {
        log.warn("Could not update Whitelist for repository '{}'", mavenRepository, e);
      }
    }

    regenerateMetadataForGroups();
    return new YumRepositoryImpl(repoBaseDir, repositoryId, getVersion());
  }
}
