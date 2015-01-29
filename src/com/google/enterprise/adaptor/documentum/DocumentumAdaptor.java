// Copyright 2014 Google Inc. All Rights Reserved.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.enterprise.adaptor.AbstractAdaptor;
import com.google.enterprise.adaptor.AdaptorContext;
import com.google.enterprise.adaptor.Config;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.DocIdPusher;
import com.google.enterprise.adaptor.InvalidConfigurationException;
import com.google.enterprise.adaptor.Request;
import com.google.enterprise.adaptor.Response;

import com.documentum.com.DfClientX;
import com.documentum.com.IDfClientX;
import com.documentum.fc.client.IDfSession;
import com.documentum.fc.client.IDfSessionManager;
import com.documentum.fc.common.DfException;
import com.documentum.fc.common.IDfLoginInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Adaptor to feed Documentum repository content into a 
 *  Google Search Appliance.
 */
public class DocumentumAdaptor extends AbstractAdaptor {
  private static Logger logger =
      Logger.getLogger(DocumentumAdaptor.class.getName());

  /** Charset used in generated HTML responses. */
  private static final Charset CHARSET = Charset.forName("UTF-8");

  private final IDfClientX dmClientX;
  private List<String> startPaths;

  public static void main(String[] args) {
    AbstractAdaptor.main(new DocumentumAdaptor(), args);
  }

  public DocumentumAdaptor() {
    this(new DfClientX());
  }

  @VisibleForTesting
  DocumentumAdaptor(IDfClientX dmClientX) {
    this.dmClientX = dmClientX;
  }

  @Override
  public void initConfig(Config config) {
    config.addKey("documentum.username", null);
    config.addKey("documentum.password", null);
    config.addKey("documentum.docbaseName", null);
    config.addKey("documentum.src", null);
    config.addKey("documentum.separatorRegex", ",");
  }

  @Override
  public void init(AdaptorContext context) throws DfException {
    Config config = context.getConfig();
    validateConfig(config);
    String src = config.getValue("documentum.src");
    logger.log(Level.CONFIG, "documentum.src: {0}", src);
    String separatorRegex = config.getValue("documentum.separatorRegex");
    logger.log(Level.CONFIG, "documentum.separatorRegex: {0}", separatorRegex);
    startPaths = parseStartPaths(src, separatorRegex);
    logger.log(Level.CONFIG, "start paths: {0}", startPaths);
    //TODO (sveldurthi): validate start paths
    initDfc(config);
  }

  /** Get all doc ids from Documentum repository. 
   * @throws InterruptedException if pusher is interrupted in sending Doc Ids
   */
  @Override
  public void getDocIds(DocIdPusher pusher) throws InterruptedException {
    logger.entering("DocumentumAdaptor", "getDocIds");
    ArrayList<DocId> docIds = new ArrayList<DocId>();
    for (String startPath : startPaths) {
      docIds.add(new DocId(startPath));
    }
    logger.log(Level.FINER, "DocumentumAdaptor DocIds: {0}", docIds);
    pusher.pushDocIds(docIds);
    logger.exiting("DocumentumAdaptor", "getDocIds");
  }

  @VisibleForTesting
  List<String> getStartPaths() {
    return Collections.unmodifiableList(startPaths);
  }

  @VisibleForTesting
  static List<String> parseStartPaths(String paths, String separatorRegex) {
    if (separatorRegex.isEmpty()) {
      return ImmutableList.of(paths);
    } else {
      return ImmutableList.copyOf(Splitter.on(Pattern.compile(separatorRegex))
          .trimResults().omitEmptyStrings().split(paths));
    }
  }

  /** Gives the bytes of a document referenced with id. 
   * @throws IOException */
  @Override
  public void getDocContent(Request req, Response resp) throws IOException {
    DocId id = req.getDocId();
    logger.log(Level.FINER, "Get content for id: {0}", id);
    String str = "Content for " + id.toString();
    resp.setContentType("text/plain; charset=" + CHARSET.name());
    OutputStream os = resp.getOutputStream();
    os.write(str.getBytes(CHARSET));
  }

  private static void validateConfig(Config config) {
    if (Strings.isNullOrEmpty(config.getValue("documentum.username"))) {
      throw new InvalidConfigurationException(
          "documentum.username is required");
    }
    if (Strings.isNullOrEmpty(config.getValue("documentum.password"))) {
      throw new InvalidConfigurationException(
          "documentum.password is required");
    }
    if (Strings.isNullOrEmpty(config.getValue("documentum.docbaseName"))) {
      throw new InvalidConfigurationException(
          "documentum.docbaseName is required");
    }
    if (Strings.isNullOrEmpty(config.getValue("documentum.src"))) {
      throw new InvalidConfigurationException(
          "documentum.src is required");
    }
  }

  private void initDfc(Config config) throws DfException {
    IDfSessionManager dmSessionManager =
        dmClientX.getLocalClient().newSessionManager();
    IDfLoginInfo dmLoginInfo = dmClientX.getLoginInfo();

    String username = config.getValue("documentum.username");
    String password = config.getValue("documentum.password");
    String docbaseName = config.getValue("documentum.docbaseName");
    logger.log(Level.CONFIG, "documentum.username: {0}", username);
    logger.log(Level.CONFIG, "documentum.docbaseName: {0}", docbaseName);

    dmLoginInfo.setUser(username);
    dmLoginInfo.setPassword(password);
    dmSessionManager.setIdentity(docbaseName, dmLoginInfo);
    IDfSession dmSession = dmSessionManager.getSession(docbaseName);
    logger.log(Level.FINE, "Session Manager set the identity for {0}",
        username);
    logger.log(Level.INFO, "DFC {0} connected to Content Server {1}",
        new Object[] {dmClientX.getDFCVersion(), dmSession.getServerVersion()});
    logger.log(Level.INFO, "Created a new session for the docbase {0}",
        docbaseName);

    logger.log(Level.INFO, "Releasing dfc session for {0}", docbaseName);
    dmSessionManager.release(dmSession);
  }
}
