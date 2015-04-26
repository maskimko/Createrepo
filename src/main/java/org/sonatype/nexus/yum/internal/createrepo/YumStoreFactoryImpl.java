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
package org.sonatype.nexus.yum.internal.createrepo;

import java.util.List;


import org.sonatype.nexus.events.EventSubscriber;
import org.sonatype.sisu.goodies.lifecycle.LifecycleSupport;

import java.util.ArrayList;

/**
 * Orient DB {@link YumStoreFactory} implementation.
 *
 * @since 3.0
 */
public class YumStoreFactoryImpl
    extends LifecycleSupport
    implements YumStoreFactory, EventSubscriber
{



  public YumStoreFactoryImpl() {
  }

  @Override
  public YumStore create(final String repositoryId) {
    return new YumStoreImpl(repositoryId);
  }

 

  @Override
  protected void doStart() throws Exception {
      //Log it
  }

  private class YumStoreImpl
      implements YumStore
  {

    private String repositoryId;
    List<YumPackage> pkgList = new ArrayList<>();

    private YumStoreImpl(final String repositoryId) {
      this.repositoryId = repositoryId;
    }

    @Override
    public void put(final YumPackage yumPackage) {
      pkgList.add(yumPackage);
    }

    @Override
    public Iterable<YumPackage> get() {
     
        return pkgList;
    }

    @Override
    public void delete(final String location) {
        for (int i = 0; i< pkgList.size(); i++){
          if (pkgList.get(i).getLocation().equals(location)) {
              pkgList.remove(i);
          }
      }
    }

    @Override
    public void deleteAll() {
      for (int i = pkgList.size()-1; i >=0; i--) {
          pkgList.remove(i);
      }
    }

  }

}
