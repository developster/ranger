/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ranger.plugin.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonAutoDetect(fieldVisibility = Visibility.ANY)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RangerPolicyDelta implements java.io.Serializable {
    public static final int CHANGE_TYPE_POLICY_CREATE            = 0;
    public static final int CHANGE_TYPE_POLICY_UPDATE            = 1;
    public static final int CHANGE_TYPE_POLICY_DELETE            = 2;
    public static final int CHANGE_TYPE_SERVICE_CHANGE           = 3;
    public static final int CHANGE_TYPE_SERVICE_DEF_CHANGE       = 4;
    public static final int CHANGE_TYPE_RANGER_ADMIN_START       = 5;
    public static final int CHANGE_TYPE_LOG_ERROR                = 6;
    public static final int CHANGE_TYPE_INVALIDATE_POLICY_DELTAS = 7;
    public static final int CHANGE_TYPE_ROLE_UPDATE              = 8;
    public static final int CHANGE_TYPE_GDS_UPDATE               = 9;

    private static final String[] changeTypeNames = {"POLICY_CREATE", "POLICY_UPDATE", "POLICY_DELETE", "SERVICE_CHANGE", "SERVICE_DEF_CHANGE", "RANGER_ADMIN_START", "LOG_ERROR", "INVALIDATE_POLICY_DELTAS", "ROLE_UPDATE", "GDS_UPDATE"};

    private Long         id;
    private Integer      changeType;
    private Long         policiesVersion;
    private RangerPolicy policy;

    public RangerPolicyDelta() {
        this(null, null, null, null);
    }

    public RangerPolicyDelta(final Long id, final Integer changeType, final Long policiesVersion, final RangerPolicy policy) {
        setId(id);
        setChangeType(changeType);
        setPoliciesVersion(policiesVersion);
        setPolicy(policy);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getChangeType() {
        return changeType;
    }

    public void setChangeType(Integer changeType) {
        this.changeType = changeType;
    }

    public Long getPoliciesVersion() {
        return policiesVersion;
    }

    private void setPoliciesVersion(Long policiesVersion) {
        this.policiesVersion = policiesVersion;
    }

    @JsonIgnore
    public String getServiceType() {
        return policy != null ? policy.getServiceType() : null;
    }

    @JsonIgnore
    public Integer getPolicyType() {
        return policy != null ? policy.getPolicyType() : null;
    }

    @JsonIgnore
    public Long getPolicyId() {
        return policy != null ? policy.getId() : null;
    }

    @JsonIgnore
    public String getZoneName() {
        return policy != null ? policy.getZoneName() : null;
    }

    public RangerPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(RangerPolicy policy) {
        this.policy = policy;
    }

    public void dedupStrings(Map<String, String> strTbl) {
        if (policy != null) {
            policy.dedupStrings(strTbl);
        }
    }

    @Override
    public String toString() {
        return "id:" + id
                + ", changeType:" + changeTypeNames[changeType]
                + ", policiesVersion:" + getPoliciesVersion()
                + ", serviceType:" + getServiceType()
                + ", policyType:" + getPolicyType()
                + ", policyId:[" + getPolicyId() + "]"
                + ", policy:[" + policy + "]";
    }
}
