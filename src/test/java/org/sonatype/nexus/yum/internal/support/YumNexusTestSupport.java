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
package org.sonatype.nexus.yum.internal.support;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

import org.sonatype.nexus.SystemStatus;
import org.sonatype.nexus.configuration.ApplicationConfiguration;
import org.sonatype.nexus.configuration.DefaultGlobalRestApiSettings;
import org.sonatype.nexus.proxy.RequestContext;
import org.sonatype.nexus.proxy.events.NexusStoppedEvent;
import org.sonatype.nexus.proxy.item.RepositoryItemUid;
import org.sonatype.nexus.proxy.item.RepositoryItemUidLock;
import org.sonatype.nexus.proxy.item.StorageItem;
import org.sonatype.nexus.proxy.item.uid.IsHiddenAttribute;
import org.sonatype.nexus.proxy.maven.MavenHostedRepository;
import org.sonatype.nexus.proxy.maven.MavenRepository;
import org.sonatype.nexus.proxy.repository.HostedRepository;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.proxy.repository.RepositoryKind;
import org.sonatype.nexus.test.NexusTestSupport;
import org.sonatype.nexus.yum.internal.createrepo.YumPackage;
import org.sonatype.nexus.yum.internal.createrepo.YumStore;
import org.sonatype.nexus.yum.internal.createrepo.YumStoreFactory;
import org.sonatype.sisu.goodies.eventbus.EventBus;
import org.sonatype.sisu.litmus.testsupport.TestTracer;
import org.sonatype.sisu.litmus.testsupport.junit.TestDataRule;
import org.sonatype.sisu.litmus.testsupport.junit.TestIndexRule;

import com.google.code.tempusfugit.temporal.Condition;
import com.google.code.tempusfugit.temporal.ThreadSleep;
import com.google.code.tempusfugit.temporal.Timeout;
import com.google.inject.Binder;
import com.google.inject.Module;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.mockito.Mockito;
import org.redline_rpm.Builder;

import static com.google.code.tempusfugit.temporal.Duration.millis;
import static com.google.code.tempusfugit.temporal.Duration.seconds;
import static com.google.code.tempusfugit.temporal.WaitFor.waitOrTimeout;
import static java.util.Arrays.asList;
import static org.apache.commons.io.FileUtils.copyDirectory;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.redline_rpm.header.Architecture.NOARCH;
import static org.redline_rpm.header.Os.LINUX;
import static org.redline_rpm.header.RpmType.BINARY;

public class YumNexusTestSupport
    extends NexusTestSupport
{

  private String javaTmpDir;

  public static final String TMP_DIR_KEY = "java.io.tmpdir";

  @Rule
  public final TestTracer tracer = new TestTracer(this);

  @Rule
  public TestIndexRule testIndex = new TestIndexRule(
      util.resolveFile("target/ut-reports"), util.resolveFile("target/ut-data")
  );

  @Rule
  public TestDataRule testData = new TestDataRule(util.resolveFile("src/test/ut-resources"));

  @Rule
  public final TestName testName = new TestName();

  @Inject
  private DefaultGlobalRestApiSettings globalRestApiSettings;

  protected File rpmsDir() {
    return testData.resolveFile("rpms");
  }

  protected File cacheDir() {
    return testIndex.getDirectory("cache");
  }

  protected File repositoryDir(final String repositoryId) {
    return testIndex.getDirectory("repository/" + repositoryId);
  }

  protected File randomDir() {
    return testIndex.getDirectory(RandomStringUtils.randomAlphabetic(20));
  }

  @Before
  public void setBaseUrl() {
    globalRestApiSettings.setBaseUrl("http://localhost:8080/nexus");
    globalRestApiSettings.commitChanges();
  }

  @After
  public void resetJavaTmpDir() {
    System.setProperty(TMP_DIR_KEY, javaTmpDir);
  }

  @After
  public void recordSurefireAndFailsafeInfo() {
    {
      final String name = "target/surefire-reports/" + getClass().getName();
      testIndex.recordLink("surefire result", util.resolveFile(name + ".txt"));
      testIndex.recordLink("surefire output", util.resolveFile(name + "-output.txt"));
    }
  }

  protected void waitFor(Condition condition)
      throws TimeoutException, InterruptedException
  {
    waitOrTimeout(condition, Timeout.timeout(seconds(60)), new ThreadSleep(millis(30)));
  }

  @Override
  protected void customizeModules(final List<Module> modules) {
    super.customizeModules(modules);
    YumStore store = mock(YumStore.class);
    final YumStoreFactory factory = mock(YumStoreFactory.class);
    when(factory.create(Mockito.anyString())).thenReturn(store);
    when(store.get()).thenReturn(Collections.<YumPackage>emptyList());
    modules.add(new Module()
    {
      @Override
      public void configure(final Binder binder) {
        binder.bind(SystemStatus.class).toInstance(new SystemStatus());
        binder.bind(YumStoreFactory.class).toInstance(factory);
      }
    });
  }

  @Override
  protected void setUp()
      throws Exception
  {
    javaTmpDir = System.getProperty(TMP_DIR_KEY);
    System.setProperty(TMP_DIR_KEY, cacheDir().getAbsolutePath());
    super.setUp();
    lookup(ApplicationConfiguration.class).loadConfiguration(true);
    injectFields();
  }

  @Override
  protected void tearDown()
      throws Exception
  {
    lookup(EventBus.class).post(new NexusStoppedEvent(null));
    super.tearDown();
  }

  private void injectFields()
      throws Exception
  {
    for (Field field : getAllFields()) {
      if (field.getAnnotation(Inject.class) != null) {
        lookupField(field);
      }
    }
  }

  private void lookupField(Field field)
      throws Exception
  {
    Object value = lookup(field.getType());
    if (!field.isAccessible()) {
      field.setAccessible(true);
      field.set(this, value);
      field.setAccessible(false);
    }
  }

  private List<Field> getAllFields() {
    List<Field> fields = new ArrayList<Field>();
    Class<?> clazz = getClass();
    do {
      List<? extends Field> classFields = getFields(clazz);
      fields.addAll(classFields);
      clazz = clazz.getSuperclass();
    }
    while (!Object.class.equals(clazz));
    return fields;
  }

  private List<? extends Field> getFields(Class<?> clazz) {
    return asList(clazz.getDeclaredFields());
  }

  public static File createDummyRpm(String name, String version, File outputDirectory)
      throws NoSuchAlgorithmException, IOException
  {
    Builder rpmBuilder = new Builder();
    rpmBuilder.setVendor("IS24");
    rpmBuilder.setGroup("is24");
    rpmBuilder.setPackager("maven - " + System.getProperty("user.name"));
    try {
      rpmBuilder.setBuildHost(InetAddress.getLocalHost().getHostName());
    }
    catch (UnknownHostException e) {
      throw new RuntimeException("Could not determine hostname.", e);
    }
    rpmBuilder.setPackage(name, version, "1");
    rpmBuilder.setPlatform(NOARCH, LINUX);
    rpmBuilder.setType(BINARY);
    rpmBuilder.setSourceRpm("dummy-source-rpm-because-yum-needs-this");

    outputDirectory.mkdirs();

    String filename = rpmBuilder.build(outputDirectory);
    return new File(outputDirectory, filename);
  }

  public File copyToTempDir(File srcDir)
      throws IOException
  {
    final File destDir = randomDir();
    copyDirectory(srcDir, destDir);
    return destDir;
  }

  public MavenRepository createRepository(final boolean isMavenHostedRepository) {
    return createRepository(isMavenHostedRepository, testName.getMethodName());
  }

  public MavenRepository createRepository(final boolean isMavenHostedRepository,
                                          final String repositoryId)
  {
    final RepositoryKind kind = mock(RepositoryKind.class);
    when(kind.isFacetAvailable(HostedRepository.class)).thenReturn(true);
    when(kind.isFacetAvailable(MavenHostedRepository.class)).thenReturn(isMavenHostedRepository);

    final MavenHostedRepository repository = mock(MavenHostedRepository.class);
    when(repository.getRepositoryKind()).thenReturn(kind);
    when(repository.getId()).thenReturn(repositoryId);
    when(repository.getProviderRole()).thenReturn(Repository.class.getName());
    when(repository.getProviderHint()).thenReturn("maven2");
    final RepositoryItemUid uid = mock(RepositoryItemUid.class);
    when(uid.getLock()).thenReturn(mock(RepositoryItemUidLock.class));
    when(repository.createUid(anyString())).thenReturn(uid);

    if (isMavenHostedRepository) {
      when(repository.adaptToFacet(HostedRepository.class)).thenReturn(repository);
      when(repository.adaptToFacet(MavenRepository.class)).thenReturn(repository);
    }
    else {
      when(repository.adaptToFacet(HostedRepository.class)).thenThrow(new ClassCastException());
    }

    when(repository.getLocalUrl()).thenReturn(repositoryDir(repositoryId).toURI().toString());

    return repository;
  }

  public static StorageItem createItem(final String version, final String filename) {
    final StorageItem item = mock(StorageItem.class);
    final RepositoryItemUid uid = mock(RepositoryItemUid.class);

    when(item.getPath()).thenReturn("foo/" + version + "/" + filename);
    when(item.getParentPath()).thenReturn("foo/" + version);
    when(item.getItemContext()).thenReturn(new RequestContext());
    when(item.getRepositoryItemUid()).thenReturn(uid);
    when(uid.getBooleanAttributeValue(IsHiddenAttribute.class)).thenReturn(true);

    return item;
  }

  // for Windows builds, since win file paths aren't valid URLs
  protected String osIndependentUri(File file) throws IOException {
    return file.getCanonicalFile().toURI().toString();
  }

}
