// Copyright 2017 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.adaptor.documentum;

import com.google.common.base.Strings;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Validates URIs by syntax checking and validating the host is reachable.
 */
class ValidatedUri {
  /** The logger for this class. */
  private static final Logger log =
      Logger.getLogger(ValidatedUri.class.getName());

  /** The connect timeout, in milliseconds. */
  private static final int TIMEOUT_MILLIS = (int) TimeUnit.SECONDS.toMillis(30);

  /** The validated URI. */
  private final URI uri;

  /**
   * Attempts to validate the given URI syntax with some more stringent checks
   * than new URI. In this case, we're mostly trying to catch typos and obvious
   * configuration issues.
   *
   * @param uriString the URI to test
   * @throws URISyntaxException if the URL syntax is invalid
   */
  public ValidatedUri(String uriString) throws URISyntaxException {
    if (Strings.isNullOrEmpty(uriString)) {
      throw new URISyntaxException("" + uriString, "null or empty URI");
    }
    try {
      // Basic syntax checking, with more understandable error messages.
      // Also ensures the URI is a URL, not a URN, and is absolute.
      new URL(uriString);
      // Advanced syntax checking, with more cryptic error messages.
      uri = new URI(uriString);
      uri.toURL();
    } catch (MalformedURLException e) {
      int index = e.getMessage().lastIndexOf(": " + uriString);
      String reason = (index < 0)
          ? e.getMessage() : e.getMessage().substring(0, index);
      throw new URISyntaxException(uriString, reason);
    }

    if (Strings.isNullOrEmpty(uri.getHost())) {
      throw new URISyntaxException(uriString, "no host");
    }
  }

  /**
   * Returns the validated URI.
   */
  public URI getUri() {
    return uri;
  }

  /**
   * Checks whether the URI's host is reachable without throwing exceptions.
   * Logs a warning if the host is not reachable.
   */
  public ValidatedUri logUnreachableHost() {
    // Try to determine if the host is reachable at this time.
    String host = uri.getHost();
    try {
      if (!(InetAddress.getByName(host).isReachable(TIMEOUT_MILLIS))) {
        log.log(Level.WARNING, "Host {0} from URI {1} is not reachable.",
            new Object[] { host, uri });
      }
    } catch (IOException e) {
      log.log(Level.WARNING, "Host " + host + " from URI " + uri
          + " is not reachable.", e);
    }
    return this;
  }
}
