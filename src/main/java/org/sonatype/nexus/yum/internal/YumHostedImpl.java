/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-2015 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.yum.internal;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.proxy.ResourceStoreRequest;
import org.sonatype.nexus.proxy.repository.HostedRepository;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.scheduling.TaskConfiguration;
import org.sonatype.nexus.scheduling.TaskInfo;
import org.sonatype.nexus.scheduling.TaskScheduler;
import org.sonatype.nexus.yum.YumHosted;
import org.sonatype.nexus.yum.YumRepository;
import org.sonatype.nexus.yum.internal.createrepo.YumStore;
import org.sonatype.nexus.yum.internal.createrepo.YumStoreFactory;
import org.sonatype.nexus.yum.internal.task.GenerateMetadataTask;
import org.sonatype.nexus.yum.internal.task.GenerateMetadataTaskDescriptor;

import com.google.common.collect.Maps;
import com.google.inject.assistedinject.Assisted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.io.File.pathSeparator;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;

/**
 * @since yum 3.0
 */
@Named
public class YumHostedImpl
    implements YumHosted
{

  private final static Logger LOG = LoggerFactory.getLogger(YumHostedImpl.class);

  private static final int MAX_EXECUTION_COUNT = 100;

  private final TaskScheduler nexusScheduler;

  private final GenerateMetadataTaskDescriptor generateMetadataTaskDescriptor;

  private final ScheduledThreadPoolExecutor executor;

  private final HostedRepository repository;

  private final File temporaryDirectory;

  private final YumStore yumStore;

  private boolean processDeletes;

  private long deleteProcessingDelay;

  private String yumGroupsDefinitionFile;

  private final Map<String, String> aliases;

  private final Map<ScheduledFuture<?>, DelayedDirectoryDeletionTask> taskMap =
      new HashMap<ScheduledFuture<?>, DelayedDirectoryDeletionTask>();

  private final Map<DelayedDirectoryDeletionTask, ScheduledFuture<?>> reverseTaskMap =
      new HashMap<DelayedDirectoryDeletionTask, ScheduledFuture<?>>();

  @Inject
  public YumHostedImpl(final TaskScheduler nexusScheduler,
                       final GenerateMetadataTaskDescriptor generateMetadataTaskDescriptor,
                       final ScheduledThreadPoolExecutor executor,
                       final BlockSqliteDatabasesRequestStrategy blockSqliteDatabasesRequestStrategy,
                       final YumStoreFactory yumStoreFactory,
                       final @Assisted HostedRepository repository,
                       final @Assisted File temporaryDirectory)
      throws MalformedURLException, URISyntaxException

  {
    this.nexusScheduler = checkNotNull(nexusScheduler);
    this.generateMetadataTaskDescriptor = checkNotNull(generateMetadataTaskDescriptor);
    this.executor = checkNotNull(executor);
    this.repository = checkNotNull(repository);
    this.temporaryDirectory = checkNotNull(temporaryDirectory);

    this.processDeletes = true;
    this.deleteProcessingDelay = DEFAULT_DELETE_PROCESSING_DELAY;

    this.aliases = Maps.newHashMap();

    this.yumGroupsDefinitionFile = null;

    this.yumStore = checkNotNull(yumStoreFactory).create(repository.getId());

    repository.registerRequestStrategy(
        BlockSqliteDatabasesRequestStrategy.class.getName(), checkNotNull(blockSqliteDatabasesRequestStrategy)
    );
  }

  private final YumRepositoryCache cache = new YumRepositoryCache();

  @Override
  public YumHosted setProcessDeletes(final boolean processDeletes) {
    this.processDeletes = processDeletes;
    return this;
  }

  @Override
  public YumHosted setDeleteProcessingDelay(final long numberOfSeconds) {
    this.deleteProcessingDelay = numberOfSeconds;
    return this;
  }

  @Override
  public YumHosted setYumGroupsDefinitionFile(final String yumGroupsDefinitionFile) {
    this.yumGroupsDefinitionFile = yumGroupsDefinitionFile;
    return this;
  }

  @Override
  public boolean shouldProcessDeletes() {
    return processDeletes;
  }

  @Override
  public long deleteProcessingDelay() {
    return deleteProcessingDelay;
  }

  @Override
  public String getYumGroupsDefinitionFile() {
    return yumGroupsDefinitionFile;
  }

  @Override
  public YumHosted addAlias(final String alias, final String version) {
    aliases.put(alias, version);
    return this;
  }

  @Override
  public YumHosted removeAlias(final String alias) {
    aliases.remove(alias);
    return this;
  }

  @Override
  public YumHosted setAliases(final Map<String, String> aliases) {
    this.aliases.clear();
    this.aliases.putAll(aliases);

    return this;
  }

  @Override
  public String getVersion(final String alias) {
    return aliases.get(alias);
  }

  @Override
  public Repository getNexusRepository() {
    return repository;
  }

  @Override
  public YumRepository getYumRepository() throws Exception {
    return getYumRepository(null);
  }

  @Override
  public YumStore getYumStore() {
    return yumStore;
  }

  TaskInfo createYumRepository(final String version,
                                              final File yumRepoBaseDir)
  {
    try {
      File rpmBaseDir = RepositoryUtils.getBaseDir(repository);
      GenerateMetadataTask task = createTask();
      task.setRpmDir(rpmBaseDir.getAbsolutePath());
      task.setRepoDir(yumRepoBaseDir);
      task.setRepositoryId(repository.getId());
      task.setVersion(version);
      task.setYumGroupsDefinitionFile(getYumGroupsDefinitionFile());
      return submitTask(task.taskConfiguration());
    }
    catch (Exception e) {
      throw new RuntimeException("Unable to create repository", e);
    }
  }

  @Override
  public YumRepository getYumRepository(final String version)
      throws Exception
  {
    YumRepositoryImpl yumRepository = cache.lookup(repository.getId(), version);
    if ((yumRepository == null) || yumRepository.isDirty()) {
      final TaskInfo taskInfo = createYumRepository(
          version, createRepositoryTempDir(repository, version)
      );
      yumRepository = (YumRepositoryImpl) taskInfo.getCurrentState().getFuture().get();
      cache.cache(yumRepository);
    }
    return yumRepository;
  }

  private TaskInfo submitTask(final TaskConfiguration task) {
    final List<TaskInfo> taskInfos = generateMetadataTaskDescriptor.filter(nexusScheduler.listsTasks());
    // type + repoId + version wil conflict
    for (TaskInfo taskInfo : taskInfos) {
      if (Objects.equals(taskInfo.getConfiguration().getRepositoryId(), task.getRepositoryId()) &&
          Objects.equals(taskInfo.getConfiguration().getString(GenerateMetadataTask.PARAM_VERSION), task.getString(
              GenerateMetadataTask.PARAM_VERSION))) {
        final TaskConfiguration taskConfiguration = mergeAddedFiles(taskInfo.getConfiguration(), task);
        return nexusScheduler.scheduleTask(taskConfiguration, taskInfo.getSchedule());
      }
    }
    return nexusScheduler.submit(task);
  }

  @Override
  public TaskInfo regenerate() {
    return addRpmAndRegenerate(null);
  }

  @Override
  public void markDirty(final String version) {
    cache.markDirty(repository.getId(), version);
  }

  @Override
  public TaskInfo addRpmAndRegenerate(@Nullable String filePath) {
    try {
      LOG.debug("Processing added rpm {}:{}", repository.getId(), filePath);
      final File rpmBaseDir = RepositoryUtils.getBaseDir(repository);
      final GenerateMetadataTask task = createTask();
      task.setRpmDir(rpmBaseDir.getAbsolutePath());
      task.setRepositoryId(repository.getId());
      task.setAddedFiles(filePath);
      task.setYumGroupsDefinitionFile(getYumGroupsDefinitionFile());
      return submitTask(task.taskConfiguration());
    }
    catch (Exception e) {
      throw new RuntimeException("Unable to create repository", e);
    }
  }

  public TaskInfo removeRpmAndRegenerate(@Nullable String filePath) {
    try {
      LOG.debug("Processing deleted rpm {}:{}", repository.getId(), filePath);
      final File rpmBaseDir = RepositoryUtils.getBaseDir(repository);
      final GenerateMetadataTask task = createTask();
      task.setRpmDir(rpmBaseDir.getAbsolutePath());
      task.setRepositoryId(repository.getId());
      task.setRemovedFile(filePath);
      task.setYumGroupsDefinitionFile(getYumGroupsDefinitionFile());
      return submitTask(task.taskConfiguration());
    }
    catch (Exception e) {
      throw new RuntimeException("Unable to create repository", e);
    }
  }

  @SuppressWarnings("unchecked")
  private TaskConfiguration mergeAddedFiles(final TaskConfiguration existingTaskConfiguration,
                                            final TaskConfiguration taskToMerge)
  {
    if (isNotBlank(taskToMerge.getString(GenerateMetadataTask.PARAM_ADDED_FILES))) {
      if (isBlank(existingTaskConfiguration.getString(GenerateMetadataTask.PARAM_ADDED_FILES))) {
        existingTaskConfiguration.setString(GenerateMetadataTask.PARAM_ADDED_FILES,
            taskToMerge.getString(GenerateMetadataTask.PARAM_ADDED_FILES));
      }
      else {
        existingTaskConfiguration.setString(GenerateMetadataTask.PARAM_ADDED_FILES,
            existingTaskConfiguration.getString(GenerateMetadataTask.PARAM_ADDED_FILES) + pathSeparator +
                taskToMerge.getString(GenerateMetadataTask.PARAM_ADDED_FILES));
      }
    }
    return existingTaskConfiguration;
  }

  private GenerateMetadataTask createTask() {
    final TaskConfiguration taskCfg = nexusScheduler
        .createTaskConfigurationInstance(generateMetadataTaskDescriptor.getId());
    final GenerateMetadataTask task = nexusScheduler.createTaskInstance(taskCfg);
    if (task == null) {
      throw new IllegalStateException(
          "Could not create a task fo type " + GenerateMetadataTask.class.getName()
      );
    }
    return task;
  }

  private File createRepositoryTempDir(Repository repository, String version) {
    return new File(temporaryDirectory, repository.getId() + File.separator + version);
  }

  @Override
  public void regenerateWhenPathIsRemoved(String path) {
    if (shouldProcessDeletes()) {
      if (findDelayedParentDirectory(path) == null) {
        removeRpmAndRegenerate(path);
      }
    }
  }

  @Override
  public void regenerateWhenDirectoryIsRemoved(String path) {
    if (shouldProcessDeletes()) {
      if (findDelayedParentDirectory(path) == null) {
        schedule(new DelayedDirectoryDeletionTask(path));
      }
    }
  }

  private void schedule(DelayedDirectoryDeletionTask task) {
    final ScheduledFuture<?> future = executor.schedule(task, deleteProcessingDelay(), SECONDS);
    taskMap.put(future, task);
    reverseTaskMap.put(task, future);
  }

  private DelayedDirectoryDeletionTask findDelayedParentDirectory(final String path) {
    for (final Runnable runnable : executor.getQueue()) {
      DelayedDirectoryDeletionTask dirTask = taskMap.get(runnable);
      if (dirTask != null && path.startsWith(dirTask.path)) {
        return dirTask;
      }
    }
    return null;
  }

  private boolean isDeleted(String path) {
    try {
      repository.retrieveItem(new ResourceStoreRequest(path));
      return false;
    }
    catch (Exception e) {
      return true;
    }
  }

  private class DelayedDirectoryDeletionTask
      implements Runnable
  {

    private final String path;

    private int executionCount = 0;

    private DelayedDirectoryDeletionTask(final String path) {
      this.path = path;
    }

    @Override
    public void run() {
      executionCount++;
      final ScheduledFuture<?> future = reverseTaskMap.remove(this);
      if (future != null) {
        taskMap.remove(future);
      }
      if (isDeleted(path)) {
        LOG.debug(
            "Recreate yum repository {} because of removed path {}", getNexusRepository().getId(), path
        );
        removeRpmAndRegenerate(path);
      }
      else if (executionCount < MAX_EXECUTION_COUNT) {
        LOG.debug(
            "Rescheduling creation of yum repository {} because path {} not deleted.",
            getNexusRepository().getId(), path
        );
        schedule(this);
      }
      else {
        LOG.warn(
            "Deleting path {} in repository {} took too long - retried {} times.",
            path, getNexusRepository().getId(), MAX_EXECUTION_COUNT
        );
      }
    }
  }

}
