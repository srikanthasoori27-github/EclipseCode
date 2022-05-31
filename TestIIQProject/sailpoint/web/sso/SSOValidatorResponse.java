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
 * This file originated at https://svn.apache.org/repos/asf/cxf/trunk/rt/rs/security/sso/saml/src/main/java/org/apache/cxf/rs/security/saml/sso/SSOValidatorResponse.java
 */
package sailpoint.web.sso;

import java.util.Date;

/**
 * Some information that encapsulates a successful validation by the SAMLSSOResponseValidator
 */
public class SSOValidatorResponse {
    private Date sessionNotOnOrAfter;
    private String responseId;
    private String assertion;

    public String getAssertion() {
        return assertion;
    }

    public void setAssertion(String assertion) {
        this.assertion = assertion;
    }

    public Date getSessionNotOnOrAfter() {
        return sessionNotOnOrAfter;
    }

    public void setSessionNotOnOrAfter(Date sessionNotOnOrAfter) {
        this.sessionNotOnOrAfter = sessionNotOnOrAfter;
    }

    public String getResponseId() {
        return responseId;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }
}
