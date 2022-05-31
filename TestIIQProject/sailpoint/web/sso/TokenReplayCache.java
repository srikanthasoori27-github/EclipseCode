/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 * This file originated from https://svn.apache.org/repos/asf/cxf/trunk/rt/rs/security/sso/saml/src/main/java/org/apache/cxf/rs/security/saml/sso/TokenReplayCache.java
 */

package sailpoint.web.sso;

import java.io.Closeable;
import java.time.Instant;

public interface TokenReplayCache<T> extends Closeable {

    T getId(T id);

    Instant getExpiry(T id);

    void putId(T id);

    void putId(T id, Instant expiry);

    void clear();
}
