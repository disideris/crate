/*
 * Licensed to Crate.io GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.auth;

import io.crate.user.User;
import io.crate.user.UserLookup;
import io.crate.protocols.SSL;
import io.crate.protocols.postgres.ConnectionProperties;
import org.elasticsearch.common.settings.SecureString;

import javax.annotation.Nullable;
import java.security.cert.Certificate;
import java.util.Objects;

public class ClientCertAuth implements AuthenticationMethod {

    public static final String NAME = "cert";
    private final UserLookup userLookup;
    private final boolean switchToPlaintext;

    ClientCertAuth(UserLookup userLookup, boolean switchToPlaintext) {
        this.userLookup = userLookup;
        this.switchToPlaintext = switchToPlaintext;
    }

    @Nullable
    @Override
    public User authenticate(String userName, SecureString passwd, ConnectionProperties connProperties) {
        Certificate clientCert = connProperties.clientCert();
        if (clientCert != null) {
            String commonName = SSL.extractCN(clientCert);
            if (Objects.equals(userName, commonName) || connProperties.protocol() == Protocol.TRANSPORT) {
                User user = userLookup.findUser(userName);
                if (user != null) {
                    return user;
                }
            } else {
                throw new RuntimeException(
                    "Common name \"" + commonName + "\" in client certificate doesn't match username \"" + userName + "\"");
            }
        }
        throw new RuntimeException("Client certificate authentication failed for user \"" + userName + "\"");
    }

    @Override
    public String name() {
        return NAME;
    }

    /**
     * This flag represents an HBA config entry corresponding
     * to the concrete instance of CertAuth and letting user to downgrade to non-SSL.
     * SSL is normally required for cert method, but can be set to false in HBA config.
     */
    public boolean isSwitchToPlaintext() {
        return switchToPlaintext;
    }
}
