/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ua.pp.msk.yum;

import static com.google.common.base.Preconditions.checkState;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import static org.apache.commons.io.FileUtils.deleteQuietly;
import org.apache.commons.lang.StringUtils;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.proxy.access.Action;
import org.sonatype.nexus.proxy.item.RepositoryItemUid;
import org.sonatype.nexus.proxy.maven.MavenRepository;
import org.sonatype.nexus.proxy.repository.HostedRepository;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.util.file.DirSupport;
import org.sonatype.nexus.yum.Yum;
import org.sonatype.nexus.yum.YumHosted;
import org.sonatype.nexus.yum.YumRepository;
import org.sonatype.nexus.yum.internal.YumRepositoryImpl;
import static org.sonatype.nexus.yum.Yum.PATH_OF_REPODATA;
import static org.sonatype.nexus.yum.Yum.PATH_OF_REPOMD_XML;
import org.sonatype.nexus.yum.YumGroup;
import org.sonatype.nexus.yum.YumRegistry;
import org.sonatype.nexus.yum.internal.RepositoryUtils;
import org.sonatype.nexus.yum.internal.RpmScanner;
import org.sonatype.nexus.yum.internal.YumHostedImpl;
import static org.sonatype.nexus.yum.internal.task.GenerateMetadataTask.PARAM_REPO_DIR;
import static org.sonatype.nexus.yum.internal.task.GenerateMetadataTask.PARAM_RPM_DIR;

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
    private boolean fullScan = true;
    private RpmScanner scanner;
    private String version = "1.0";

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

    private void regenerateMetadataForGroups(Yum yum) {
        if (yum != null && yum instanceof YumGroup) {
            ((YumGroup) yum).markDirty();
        }
    }

    
  protected void setDefaults()
      throws MalformedURLException, URISyntaxException
  {
    final Repository repository = findRepository();
    if (isBlank(getRpmDir()) && repository != null) {
      setRpmDir(RepositoryUtils.getBaseDir(repository).getAbsolutePath());
    }
    if (isBlank(getParameter(PARAM_REPO_DIR)) && isNotBlank(getRpmDir())) {
      setRepoDir(new File(getRpmDir()));
    }
  }
   

  protected YumRepository doRun()
      throws Exception
  {
    String repositoryId = "test";

    if (!StringUtils.isEmpty(repositoryId)) {
      checkState(
          yumRegistry.isRegistered(repositoryId),
          "Metadata regeneration can only be run on repositories that have an enabled 'Yum: Generate Metadata' capability"
      );
      Yum yum = yumRegistry.get(repositoryId);
      checkState(
          yum.getNexusRepository().getRepositoryKind().isFacetAvailable(HostedRepository.class),
          "Metadata generation can only be run on hosted repositories"
      );
    }

    setDefaults();

    final Repository repository = findRepository();
    final RepositoryItemUid mdUid = repository.createUid("/" + PATH_OF_REPOMD_XML);
    try {
      mdUid.getLock().lock(Action.update);

      LOG.debug("Generating Yum-Repository for '{}' ...", getRpmDir());
      try {
        // NEXUS-6680: Nuke cache dir if force rebuild in effect
        if (true) {
          DirSupport.deleteIfExists(getCacheDir().toPath());
        }
        DirSupport.mkdir(getRepoDir().toPath());

        File rpmListFile = createRpmListFile();
        commandLineExecutor.exec(buildCreateRepositoryCommand(rpmListFile));
      }
      catch (IOException e) {
        LOG.warn("Yum metadata generation failed", e);
        throw new IOException("Yum metadata generation failed", e);
      }
      // TODO dubious
      Thread.sleep(100);

      if (repository != null) {
        final MavenRepository mavenRepository = repository.adaptToFacet(MavenRepository.class);
        if (mavenRepository != null) {
          try {
            routingManager.forceUpdatePrefixFile(mavenRepository);
          }
          catch (Exception e) {
            logger.warn("Could not update Whitelist for repository '{}'", mavenRepository, e);
          }
        }
      }

      regenerateMetadataForGroups();
      return new YumRepositoryImpl(getRepoDir(), repositoryId, getVersion());
    }
    finally {
      mdUid.getLock().unlock();
    }
  }
  
}
