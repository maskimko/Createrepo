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

import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.capability.CapabilityIdentity;
import org.sonatype.nexus.capability.CapabilityType;
import org.sonatype.nexus.capability.Tag;
import org.sonatype.nexus.capability.Taggable;
import org.sonatype.nexus.capability.Validator;
import org.sonatype.nexus.capability.support.CapabilityDescriptorSupport;
import org.sonatype.nexus.capability.support.validator.Validators;
import org.sonatype.nexus.formfields.FormField;
import org.sonatype.nexus.formfields.NumberTextFormField;
import org.sonatype.sisu.goodies.i18n.I18N;
import org.sonatype.sisu.goodies.i18n.MessageBundle;

import com.google.common.collect.Lists;

import static org.sonatype.nexus.capability.CapabilityType.capabilityType;
import static org.sonatype.nexus.capability.Tag.categoryTag;
import static org.sonatype.nexus.capability.Tag.tags;

/**
 * {@link YumCapability} descriptor.
 *
 * @since yum 3.0
 */
@Singleton
@Named(YumCapabilityDescriptor.TYPE_ID)
public class YumCapabilityDescriptor
    extends CapabilityDescriptorSupport
    implements Taggable
{
  public static final String TYPE_ID = "yum";

  public static final CapabilityType TYPE = capabilityType(TYPE_ID);

  private static interface Messages
      extends MessageBundle
  {
    @DefaultMessage("Yum: Configuration")
    String name();

    @DefaultMessage("Max number of parallel threads")
    String maxNumberParallelThreadsLabel();

    @DefaultMessage("Maximum number of threads to be used for generating Yum repositories (default 10 threads)")
    String maxNumberParallelThreadsHelp();
  }

  private static final Messages messages = I18N.create(Messages.class);

  private final Validators validators;

  private final List<FormField> formFields;

  @Inject
  public YumCapabilityDescriptor(final Validators validators) {
    this.validators = validators;

    this.formFields = Lists.<FormField>newArrayList(
        new NumberTextFormField(
            YumCapabilityConfiguration.MAX_NUMBER_PARALLEL_THREADS,
            messages.maxNumberParallelThreadsLabel(),
            messages.maxNumberParallelThreadsHelp(),
            FormField.OPTIONAL
        ).withInitialValue(10)
    );
  }

  @Override
  public Validator validator() {
    return validators.logical().and(
        validators.capability().uniquePer(TYPE)
    );
  }

  @Override
  public Validator validator(final CapabilityIdentity id) {
    return validators.logical().and(
        validators.capability().uniquePerExcluding(id, TYPE)
    );
  }

  @Override
  public CapabilityType type() {
    return TYPE;
  }

  @Override
  public String name() {
    return messages.name();
  }

  @Override
  public List<FormField> formFields() {
    return formFields;
  }

  @Override
  protected String renderAbout() throws Exception {
    return render(TYPE_ID + "-about.vm");
  }

  @Override
  public Set<Tag> getTags() {
    return tags(categoryTag("Yum"));
  }
}
