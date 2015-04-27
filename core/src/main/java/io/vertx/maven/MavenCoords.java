/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.maven;

import java.lang.IllegalArgumentException;import java.lang.String; /**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class MavenCoords {

  private final String owner;
  private final String serviceName;
  private final String extension;
  private final String classifier;
  private final String version;

  public MavenCoords(String owner, String serviceName, String extension, String classifier, String version) {
    this.owner = owner;
    this.serviceName = serviceName;
    this.extension = extension;
    this.classifier = classifier;
    this.version = version;
  }

  public MavenCoords(String idString) {
    String[] split = idString.split(":");
    if (split.length != 2 && split.length != 3 && split.length != 4 && split.length != 5) {
      throw new IllegalArgumentException("Invalid maven coordinates: " + idString);
    }
    owner = split[0];
    serviceName = split[1];
    if (split.length == 3) {
      extension = null;
      classifier = null;
      version = split[2];
    } else if (split.length == 4) {
      extension = split[2];
      classifier = null;
      version = split[3];
    } else if (split.length == 5) {
      extension = split[2];
      classifier = split[3];
      version = split[4];
    } else {
      extension = null;
      classifier = null;
      version = null;
    }
  }

  public String owner() {
    return owner;
  }

  public String serviceName() {
    return serviceName;
  }

  public String extension() {
    return extension;
  }

  public String classifier() {
    return classifier;
  }

  public String version() {
    return version;
  }

}
