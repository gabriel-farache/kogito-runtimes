/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.security.token.persistence;

import io.vertx.pgclient.PgPool;
import org.kie.kogito.security.token.persistence.Tokens;
import org.kie.kogito.security.token.persistence.TokensFactory;

public abstract class AbstractTokensFactory implements TokensFactory {

    private final Long queryTimeout;
    private final PgPool client;
    private final Boolean lock;

    // Constructor for DI
    protected AbstractTokensFactory() {
        this(null, 10000L, false);
    }

    public AbstractTokensFactory(PgPool client, Long queryTimeout, Boolean lock) {
        this.client = client;
        this.queryTimeout = queryTimeout;
        this.lock = lock;
    }

    public PgPool client() {
        return this.client;
    }

    public boolean lock() {
        return lock;
    }

    @Override
    public Tokens createTokens() {
        return null;
        //return new JPATokens(client, queryTimeout, lock);
    }
}
