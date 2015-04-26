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
package org.sonatype.nexus.yum.internal.capabilities;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.proxy.registry.RepositoryRegistry;
import org.sonatype.nexus.yum.Yum;
import org.sonatype.nexus.yum.YumRegistry;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * @since 2.7.0
 */
@Named(ProxyMetadataCapabilityDescriptor.TYPE_ID)
public class ProxyMetadataCapability
    extends MetadataCapabilitySupport<ProxyMetadataCapabilityConfiguration>
{

  @Inject
  public ProxyMetadataCapability(final YumRegistry yumRegistry,
                                 final RepositoryRegistry repositoryRegistry)
  {
    super(yumRegistry, repositoryRegistry);
  }

  @Override
  void configureYum(final Yum yum, ProxyMetadataCapabilityConfiguration config) {
    checkNotNull(yum);
    checkState(isConfigured());
  }

  @Override
  protected ProxyMetadataCapabilityConfiguration createConfig(final Map<String, String> properties) {
    return new ProxyMetadataCapabilityConfiguration(properties);
  }

}
