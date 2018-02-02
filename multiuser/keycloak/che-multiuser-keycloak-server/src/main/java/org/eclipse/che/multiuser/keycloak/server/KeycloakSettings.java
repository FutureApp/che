/*
 * Copyright (c) 2012-2017 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.multiuser.keycloak.server;

import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.AUTH_SERVER_URL_SETTING;
import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.CLIENT_ID_SETTING;
import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.GITHUB_ENDPOINT_SETTING;
import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.JS_ADAPTER_URL_SETTING;
import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.JWKS_ENDPOINT_SETTING;
import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.LOGOUT_ENDPOINT_SETTING;
import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.OIDC_PROVIDER_SETTING;
import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.OSO_ENDPOINT_SETTING;
import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.PASSWORD_ENDPOINT_SETTING;
import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.PROFILE_ENDPOINT_SETTING;
import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.REALM_SETTING;
import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.TOKEN_ENDPOINT_SETTING;
import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.USERINFO_ENDPOINT_SETTING;
import static org.eclipse.che.multiuser.keycloak.shared.KeycloakConstants.USE_NONCE_SETTING;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.che.commons.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author Max Shaposhnik (mshaposh@redhat.com) */
@Singleton
public class KeycloakSettings {
  private static final Logger LOG = LoggerFactory.getLogger(KeycloakSettings.class);

  private final Map<String, String> settings;

  @Inject
  public KeycloakSettings(
      @Nullable @Named(JS_ADAPTER_URL_SETTING) String jsAdapterUrl,
      @Nullable @Named(AUTH_SERVER_URL_SETTING) String serverURL,
      @Nullable @Named(REALM_SETTING) String realm,
      @Named(CLIENT_ID_SETTING) String clientId,
      @Nullable @Named(OIDC_PROVIDER_SETTING) String oidcProvider,
      @Named(USE_NONCE_SETTING) boolean useNonce,
      @Nullable @Named(OSO_ENDPOINT_SETTING) String osoEndpoint,
      @Nullable @Named(GITHUB_ENDPOINT_SETTING) String gitHubEndpoint) {

    String wellKnownEndpoint = oidcProvider != null ? oidcProvider : serverURL + "/realms/" + realm;
    if (! wellKnownEndpoint.endsWith("/")) {
        wellKnownEndpoint = wellKnownEndpoint + "/";
    }
    wellKnownEndpoint += ".well-known/openid-configuration";
    
    LOG.info("Retrieving OpenId configuration from endpoint: {}", wellKnownEndpoint);
    
    URL url;
    Map<String, Object> openIdConfiguration;
    try {
      url = new URL(wellKnownEndpoint);
      final InputStream inputStream = url.openStream();
      final JsonFactory factory = new JsonFactory();
      final JsonParser parser = factory.createParser(inputStream);
      final TypeReference<Map<String, Object>> typeReference =
          new TypeReference<Map<String, Object>>() {};
      openIdConfiguration = new ObjectMapper().reader().readValue(parser, typeReference);
    } catch (IOException e) {
        LOG.error("Exception while retrieving OpenId configuration from endpoint: " + wellKnownEndpoint, e);
        this.settings = Collections.unmodifiableMap(new HashMap<>());
        return;
    }

    LOG.info("openid configuration = {}", openIdConfiguration);
    
    Map<String, String> settings = Maps.newHashMap();
    settings.put(AUTH_SERVER_URL_SETTING, serverURL);
    settings.put(CLIENT_ID_SETTING, clientId);
    settings.put(REALM_SETTING, realm);
    if (serverURL != null) {
      settings.put(PROFILE_ENDPOINT_SETTING, serverURL + "/realms/" + realm + "/account");
      settings.put(PASSWORD_ENDPOINT_SETTING, serverURL + "/realms/" + realm + "/account/password");
    }
    String endSessionEndpoint = (String) openIdConfiguration.get("end_session_endpoint");
    if (endSessionEndpoint != null) {
      settings.put(LOGOUT_ENDPOINT_SETTING, endSessionEndpoint);
    }
    String tokenEndpoint = (String) openIdConfiguration.get("token_endpoint");
    if (tokenEndpoint != null) {
      settings.put(TOKEN_ENDPOINT_SETTING, tokenEndpoint);
    }
    String userInfoEndpoint = (String) openIdConfiguration.get("userinfo_endpoint");
    if (userInfoEndpoint != null) {
      settings.put(USERINFO_ENDPOINT_SETTING, userInfoEndpoint);
    }
    String jwksUriEndpoint = (String) openIdConfiguration.get("jwks_uri");
    if (jwksUriEndpoint != null) {
      settings.put(JWKS_ENDPOINT_SETTING, jwksUriEndpoint);
    }
    settings.put(OSO_ENDPOINT_SETTING, osoEndpoint);
    settings.put(GITHUB_ENDPOINT_SETTING, gitHubEndpoint);
    if (oidcProvider != null) {
      settings.put(OIDC_PROVIDER_SETTING, oidcProvider);
    }
    settings.put(USE_NONCE_SETTING, Boolean.toString(useNonce));
    if (jsAdapterUrl == null) {
      jsAdapterUrl =
          (oidcProvider != null)
              ? "/wsmaster/keycloak/OIDCKeycloak.js"
              : serverURL + "/js/keycloak.js";
    }
    settings.put(JS_ADAPTER_URL_SETTING, jsAdapterUrl);

    this.settings = Collections.unmodifiableMap(settings);
  }

  public Map<String, String> get() {
    return settings;
  }
}
