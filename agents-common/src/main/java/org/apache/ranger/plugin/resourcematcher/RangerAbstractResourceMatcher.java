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

package org.apache.ranger.plugin.resourcematcher;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.lang.StringUtils;
import org.apache.ranger.plugin.model.RangerPolicy.RangerPolicyResource;
import org.apache.ranger.plugin.model.RangerServiceDef.RangerResourceDef;
import org.apache.ranger.plugin.policyengine.RangerAccessRequest.ResourceElementMatchType;
import org.apache.ranger.plugin.util.ServiceDefUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.apache.ranger.plugin.policyengine.RangerAccessRequest.ResourceElementMatchType.NONE;
import static org.apache.ranger.plugin.policyengine.RangerAccessRequest.ResourceElementMatchType.SELF;

public abstract class RangerAbstractResourceMatcher implements RangerResourceMatcher {
    private static final Logger LOG = LoggerFactory.getLogger(RangerAbstractResourceMatcher.class);

    public static final String WILDCARD_ASTERISK              = "*";
    public static final String WILDCARD_QUESTION_MARK         = "?";
    public static final String OPTION_IGNORE_CASE             = "ignoreCase";
    public static final String OPTION_QUOTED_CASE_SENSITIVE   = "quotedCaseSensitive";
    public static final String OPTION_QUOTE_CHARS             = "quoteChars";
    public static final String OPTION_WILD_CARD               = "wildCard";
    public static final String OPTION_REPLACE_TOKENS          = "replaceTokens";
    public static final String OPTION_TOKEN_DELIMITER_START   = "tokenDelimiterStart";
    public static final String OPTION_TOKEN_DELIMITER_END     = "tokenDelimiterEnd";
    public static final String OPTION_TOKEN_DELIMITER_ESCAPE  = "tokenDelimiterEscape";
    public static final String OPTION_TOKEN_DELIMITER_PREFIX  = "tokenDelimiterPrefix";
    public static final String OPTION_REPLACE_REQ_EXPRESSIONS = "replaceReqExpressions";

    protected RangerResourceDef      resourceDef;
    protected RangerPolicyResource   policyResource;
    protected boolean                optIgnoreCase;
    protected boolean                optQuotedCaseSensitive;
    protected String                 optQuoteChars = "\"";
    protected boolean                optWildCard;
    protected List<String>           policyValues;
    protected boolean                policyIsExcludes;
    protected boolean                isMatchAny;
    protected ResourceMatcherWrapper resourceMatchers;
    protected boolean                optReplaceTokens;
    protected char                   startDelimiterChar = '{';
    protected char                   endDelimiterChar   = '}';
    protected char                   escapeChar         = '\\';
    protected String                 tokenPrefix        = "";

    public static boolean getOptionIgnoreCase(Map<String, String> options) {
        return ServiceDefUtil.getBooleanOption(options, OPTION_IGNORE_CASE, true);
    }

    public static boolean getOptionQuotedCaseSensitive(Map<String, String> options) {
        return ServiceDefUtil.getBooleanOption(options, OPTION_QUOTED_CASE_SENSITIVE, false);
    }

    public static String getOptionQuoteChars(Map<String, String> options) {
        return ServiceDefUtil.getOption(options, OPTION_QUOTE_CHARS, "\"");
    }

    public static boolean getOptionWildCard(Map<String, String> options) {
        return ServiceDefUtil.getBooleanOption(options, OPTION_WILD_CARD, true);
    }

    public static boolean getOptionReplaceTokens(Map<String, String> options) {
        return ServiceDefUtil.getBooleanOption(options, OPTION_REPLACE_TOKENS, true);
    }

    public static char getOptionDelimiterStart(Map<String, String> options) {
        return ServiceDefUtil.getCharOption(options, OPTION_TOKEN_DELIMITER_START, '{');
    }

    public static char getOptionDelimiterEnd(Map<String, String> options) {
        return ServiceDefUtil.getCharOption(options, OPTION_TOKEN_DELIMITER_END, '}');
    }

    public static char getOptionDelimiterEscape(Map<String, String> options) {
        return ServiceDefUtil.getCharOption(options, OPTION_TOKEN_DELIMITER_ESCAPE, '\\');
    }

    public static String getOptionDelimiterPrefix(Map<String, String> options) {
        return ServiceDefUtil.getOption(options, OPTION_TOKEN_DELIMITER_PREFIX, "");
    }

    public static boolean getOptionReplaceReqExpressions(Map<String, String> options) {
        return ServiceDefUtil.getBooleanOption(options, OPTION_REPLACE_REQ_EXPRESSIONS, true);
    }

    @Override
    public void setResourceDef(RangerResourceDef resourceDef) {
        this.resourceDef = resourceDef;
    }

    @Override
    public void setPolicyResource(RangerPolicyResource policyResource) {
        this.policyResource = policyResource;
    }

    @Override
    public void init() {
        LOG.debug("==> RangerAbstractResourceMatcher.init()");

        Map<String, String> options = resourceDef != null ? resourceDef.getMatcherOptions() : null;

        optIgnoreCase          = getOptionIgnoreCase(options);
        optQuotedCaseSensitive = getOptionQuotedCaseSensitive(options);
        optQuoteChars          = getOptionQuoteChars(options);
        optWildCard            = getOptionWildCard(options);

        policyValues     = new ArrayList<>();
        policyIsExcludes = policyResource != null && policyResource.getIsExcludes();

        if (policyResource != null && policyResource.getValues() != null) {
            for (String policyValue : policyResource.getValues()) {
                if (StringUtils.isEmpty(policyValue)) {
                    continue;
                }
                policyValues.add(policyValue);
            }
        }

        optReplaceTokens = getOptionReplaceTokens(options);

        if (optReplaceTokens) {
            startDelimiterChar = getOptionDelimiterStart(options);
            endDelimiterChar   = getOptionDelimiterEnd(options);
            escapeChar         = getOptionDelimiterEscape(options);
            tokenPrefix        = getOptionDelimiterPrefix(options);

            if (escapeChar == startDelimiterChar || escapeChar == endDelimiterChar ||
                    tokenPrefix.indexOf(escapeChar) != -1 || tokenPrefix.indexOf(startDelimiterChar) != -1 ||
                    tokenPrefix.indexOf(endDelimiterChar) != -1) {
                String resouceName = resourceDef == null ? "" : resourceDef.getName();

                String msg = "Invalid token-replacement parameters for resource '" + resouceName + "': { ";
                msg += (OPTION_TOKEN_DELIMITER_START + "='" + startDelimiterChar + "'; ");
                msg += (OPTION_TOKEN_DELIMITER_END + "='" + endDelimiterChar + "'; ");
                msg += (OPTION_TOKEN_DELIMITER_ESCAPE + "='" + escapeChar + "'; ");
                msg += (OPTION_TOKEN_DELIMITER_PREFIX + "='" + tokenPrefix + "' }. ");
                msg += "Token replacement disabled";

                LOG.error(msg);

                optReplaceTokens = false;
            }
        }

        resourceMatchers = buildResourceMatchers();
        isMatchAny       = resourceMatchers == null || CollectionUtils.isEmpty(resourceMatchers.getResourceMatchers());

        LOG.debug("<== RangerAbstractResourceMatcher.init()");
    }

    @Override
    public boolean isMatchAny() {
        return isMatchAny;
    }

    @Override
    public boolean isCompleteMatch(String resource, Map<String, Object> evalContext) {
        LOG.debug("==> RangerAbstractResourceMatcher.isCompleteMatch({}, {})", resource, evalContext);

        boolean ret = false;

        if (CollectionUtils.isEmpty(policyValues)) {
            ret = StringUtils.isEmpty(resource);
        } else if (policyValues.size() == 1) {
            String policyValue = policyValues.get(0);

            if (isMatchAny) {
                ret = StringUtils.isEmpty(resource) || StringUtils.containsOnly(resource, WILDCARD_ASTERISK);
            } else {
                ret = optIgnoreCase && !(optQuotedCaseSensitive && ResourceMatcher.startsWithAnyChar(resource, optQuoteChars)) ? StringUtils.equalsIgnoreCase(resource, policyValue) : StringUtils.equals(resource, policyValue);
            }

            if (policyIsExcludes) {
                ret = !ret;
            }
        }

        LOG.debug("<== RangerAbstractResourceMatcher.isCompleteMatch({}, {}): {}", resource, evalContext, ret);

        return ret;
    }

    public boolean getNeedsDynamicEval() {
        return resourceMatchers != null && resourceMatchers.getNeedsDynamicEval();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        toString(sb);

        return sb.toString();
    }

    public StringBuilder toString(StringBuilder sb) {
        sb.append("RangerAbstractResourceMatcher={");

        sb.append("resourceDef={");
        if (resourceDef != null) {
            resourceDef.toString(sb);
        }
        sb.append("} ");
        sb.append("policyResource={");
        if (policyResource != null) {
            policyResource.toString(sb);
        }
        sb.append("} ");
        sb.append("optIgnoreCase={").append(optIgnoreCase).append("} ");
        sb.append("optQuotedCaseSensitive={").append(optQuotedCaseSensitive).append("} ");
        sb.append("optQuoteChars={").append(optQuoteChars).append("} ");
        sb.append("optWildCard={").append(optWildCard).append("} ");

        sb.append("policyValues={");
        if (policyValues != null) {
            for (String value : policyValues) {
                sb.append(value).append(",");
            }
        }
        sb.append("} ");

        sb.append("policyIsExcludes={").append(policyIsExcludes).append("} ");
        sb.append("isMatchAny={").append(isMatchAny).append("} ");

        sb.append("options={");
        if (resourceDef != null && resourceDef.getMatcherOptions() != null) {
            for (Map.Entry<String, String> e : resourceDef.getMatcherOptions().entrySet()) {
                sb.append(e.getKey()).append("=").append(e.getValue()).append(';');
            }
        }
        sb.append("} ");

        sb.append("}");

        return sb;
    }

    /**
     * The only case where excludes flag does NOT change the result is the following:
     * - Resource denotes all possible values (i.e. resource in (null, "", "*")
     * - where as policy does not allow all possible values (i.e. policy.values().contains("*")
     */
    public boolean applyExcludes(boolean allValuesRequested, boolean resultWithoutExcludes) {
        if (!policyIsExcludes) {
            return resultWithoutExcludes;                   // not an excludes policy!
        }
        if (allValuesRequested && !isMatchAny) {
            return resultWithoutExcludes;  // one case where excludes has no effect
        }
        return !resultWithoutExcludes;                                         // all other cases flip it
    }

    public ResourceElementMatchType applyExcludes(boolean allValuesRequested, ResourceElementMatchType resultWithoutExcludes) {
        if (!policyIsExcludes) {
            return resultWithoutExcludes;                   // not an excludes policy!
        }
        if (allValuesRequested && !isMatchAny) {
            return resultWithoutExcludes;  // one case where excludes has no effect
        }
        return resultWithoutExcludes == NONE ? SELF : NONE;                    // all other cases flip it
    }

    protected Map<String, String> getOptions() {
        return resourceDef != null ? resourceDef.getMatcherOptions() : null;
    }

    protected ResourceMatcherWrapper buildResourceMatchers() {
        List<ResourceMatcher> resourceMatchers = new ArrayList<>();
        boolean               needsDynamicEval = false;

        for (String policyValue : policyValues) {
            ResourceMatcher matcher = getMatcher(policyValue);

            if (matcher != null) {
                if (matcher.isMatchAny()) {
                    resourceMatchers.clear();
                    break;
                }
                if (!needsDynamicEval && matcher.getNeedsDynamicEval()) {
                    needsDynamicEval = true;
                }
                resourceMatchers.add(matcher);
            }
        }

        resourceMatchers.sort(new ResourceMatcher.PriorityComparator());

        return CollectionUtils.isNotEmpty(resourceMatchers) ?
                new ResourceMatcherWrapper(needsDynamicEval, resourceMatchers) : null;
    }

    boolean isAllValuesRequested(Object resource) {
        final boolean result;

        if (resource == null) {
            result = true;
        } else if (resource instanceof String) {
            result = StringUtils.isEmpty((String) resource) || WILDCARD_ASTERISK.equals(resource);
        } else { // return false for any other type of resourceValue
            result = false;
        }

        LOG.debug("isAllValuesRequested({}): {}", resource, result);

        return result;
    }

    ResourceMatcher getMatcher(String policyValue) {
        final int len = policyValue != null ? policyValue.length() : 0;

        if (len == 0) {
            return null;
        }

        final ResourceMatcher ret;

        int     wildcardStartIdx  = -1;
        int     wildcardEndIdx    = -1;
        boolean needWildcardMatch = false;

        // If optWildcard is true
        //   If ('?' found or non-contiguous '*'s found in policyValue)
        //     needWildcardMatch = true
        //   End
        //
        //   wildcardStartIdx is set to index of first '*' in policyValue or -1 if '*' is not found in policyValue, and
        //   wildcardEndIdx is set to index of last '*' in policyValue or -1 if '*' is not found in policyValue
        // Else
        //   needWildcardMatch is set to false
        // End
        if (optWildCard) {
            for (int i = 0; i < len; i++) {
                final char c = policyValue.charAt(i);

                if (c == '?') {
                    needWildcardMatch = true;
                    break;
                } else if (c == '*') {
                    if (wildcardEndIdx == -1 || wildcardEndIdx == (i - 1)) {
                        wildcardEndIdx = i;
                        if (wildcardStartIdx == -1) {
                            wildcardStartIdx = i;
                        }
                    } else {
                        needWildcardMatch = true;
                        break;
                    }
                }
            }
        }

        if (needWildcardMatch) { // test?, test*a*, test*a*b, *test*a
            ret = optIgnoreCase ? (optQuotedCaseSensitive ? new QuotedCaseSensitiveWildcardMatcher(policyValue, getOptions(), optQuoteChars) : new CaseInsensitiveWildcardMatcher(policyValue, getOptions())) : new CaseSensitiveWildcardMatcher(policyValue, getOptions());
        } else if (wildcardStartIdx == -1) { // test, testa, testab
            ret = optIgnoreCase ? (optQuotedCaseSensitive ? new QuotedCaseSensitiveStringMatcher(policyValue, getOptions(), optQuoteChars) : new CaseInsensitiveStringMatcher(policyValue, getOptions())) : new CaseSensitiveStringMatcher(policyValue, getOptions());
        } else if (wildcardStartIdx == 0) { // *test, **test, *testa, *testab
            String matchStr = policyValue.substring(wildcardEndIdx + 1);
            ret = optIgnoreCase ? (optQuotedCaseSensitive ? new QuotedCaseSensitiveEndsWithMatcher(matchStr, getOptions(), optQuoteChars) : new CaseInsensitiveEndsWithMatcher(matchStr, getOptions())) : new CaseSensitiveEndsWithMatcher(matchStr, getOptions());
        } else if (wildcardEndIdx != (len - 1)) { // test*a, test*ab
            ret = optIgnoreCase ? (optQuotedCaseSensitive ? new QuotedCaseSensitiveWildcardMatcher(policyValue, getOptions(), optQuoteChars) : new CaseInsensitiveWildcardMatcher(policyValue, getOptions())) : new CaseSensitiveWildcardMatcher(policyValue, getOptions());
        } else { // test*, test**, testa*, testab*
            String matchStr = policyValue.substring(0, wildcardStartIdx);
            ret = optIgnoreCase ? (optQuotedCaseSensitive ? new QuotedCaseSensitiveStartsWithMatcher(matchStr, getOptions(), optQuoteChars) : new CaseInsensitiveStartsWithMatcher(matchStr, getOptions())) : new CaseSensitiveStartsWithMatcher(matchStr, getOptions());
        }

        if (optReplaceTokens) {
            ret.setDelimiters(startDelimiterChar, endDelimiterChar, escapeChar, tokenPrefix);
        }

        return ret;
    }

    abstract static class AbstractStringResourceMatcher extends ResourceMatcher {
        protected AbstractStringResourceMatcher(String value, Map<String, String> options) {
            super(value, options);
        }

        @Override
        public boolean isChildMatch(String resourceValue, Map<String, Object> evalContext) {
            return false; // child-match is applicable only for path resource
        }
    }

    static final class CaseSensitiveStringMatcher extends AbstractStringResourceMatcher {
        CaseSensitiveStringMatcher(String value, Map<String, String> options) {
            super(value, options);
        }

        @Override
        boolean isMatch(String resourceValue, Map<String, Object> evalContext) {
            return StringUtils.equals(resourceValue, getExpandedValue(evalContext));
        }

        @Override
        public boolean isPrefixMatch(String resourceValue, Map<String, Object> evalContext) {
            return StringUtils.startsWith(getExpandedValue(evalContext), resourceValue);
        }

        int getPriority() {
            return 1 + (getNeedsDynamicEval() ? DYNAMIC_EVALUATION_PENALTY : 0);
        }
    }

    static final class CaseInsensitiveStringMatcher extends AbstractStringResourceMatcher {
        CaseInsensitiveStringMatcher(String value, Map<String, String> options) {
            super(value, options);
        }

        @Override
        boolean isMatch(String resourceValue, Map<String, Object> evalContext) {
            return StringUtils.equalsIgnoreCase(resourceValue, getExpandedValue(evalContext));
        }

        @Override
        public boolean isPrefixMatch(String resourceValue, Map<String, Object> evalContext) {
            return StringUtils.startsWithIgnoreCase(getExpandedValue(evalContext), resourceValue);
        }

        int getPriority() {
            return 2 + (getNeedsDynamicEval() ? DYNAMIC_EVALUATION_PENALTY : 0);
        }
    }

    static final class QuotedCaseSensitiveStringMatcher extends AbstractStringResourceMatcher {
        private final String quoteChars;

        QuotedCaseSensitiveStringMatcher(String value, Map<String, String> options, String quoteChars) {
            super(value, options);

            this.quoteChars = quoteChars;
        }

        @Override
        boolean isMatch(String resourceValue, Map<String, Object> evalContext) {
            if (startsWithAnyChar(resourceValue, quoteChars)) {
                return StringUtils.equals(resourceValue, getExpandedValue(evalContext));
            } else {
                return StringUtils.equalsIgnoreCase(resourceValue, getExpandedValue(evalContext));
            }
        }

        @Override
        public boolean isPrefixMatch(String resourceValue, Map<String, Object> evalContext) {
            if (startsWithAnyChar(resourceValue, quoteChars)) {
                return StringUtils.startsWith(getExpandedValue(evalContext), resourceValue);
            } else {
                return StringUtils.startsWithIgnoreCase(getExpandedValue(evalContext), resourceValue);
            }
        }

        int getPriority() {
            return 2 + (getNeedsDynamicEval() ? DYNAMIC_EVALUATION_PENALTY : 0);
        }
    }

    static final class CaseSensitiveStartsWithMatcher extends AbstractStringResourceMatcher {
        CaseSensitiveStartsWithMatcher(String value, Map<String, String> options) {
            super(value, options);
        }

        @Override
        boolean isMatch(String resourceValue, Map<String, Object> evalContext) {
            return StringUtils.startsWith(resourceValue, getExpandedValue(evalContext));
        }

        @Override
        public boolean isPrefixMatch(String resourceValue, Map<String, Object> evalContext) {
            return StringUtils.startsWith(getExpandedValue(evalContext), resourceValue);
        }

        int getPriority() {
            return 3 + (getNeedsDynamicEval() ? DYNAMIC_EVALUATION_PENALTY : 0);
        }
    }

    static final class CaseInsensitiveStartsWithMatcher extends AbstractStringResourceMatcher {
        CaseInsensitiveStartsWithMatcher(String value, Map<String, String> options) {
            super(value, options);
        }

        @Override
        boolean isMatch(String resourceValue, Map<String, Object> evalContext) {
            return StringUtils.startsWithIgnoreCase(resourceValue, getExpandedValue(evalContext));
        }

        @Override
        public boolean isPrefixMatch(String resourceValue, Map<String, Object> evalContext) {
            return StringUtils.startsWithIgnoreCase(getExpandedValue(evalContext), resourceValue);
        }

        int getPriority() {
            return 4 + (getNeedsDynamicEval() ? DYNAMIC_EVALUATION_PENALTY : 0);
        }
    }

    static final class QuotedCaseSensitiveStartsWithMatcher extends AbstractStringResourceMatcher {
        private final String quoteChars;

        QuotedCaseSensitiveStartsWithMatcher(String value, Map<String, String> options, String quoteChars) {
            super(value, options);

            this.quoteChars = quoteChars;
        }

        @Override
        boolean isMatch(String resourceValue, Map<String, Object> evalContext) {
            if (startsWithAnyChar(resourceValue, quoteChars)) {
                return StringUtils.startsWith(resourceValue, getExpandedValue(evalContext));
            } else {
                return StringUtils.startsWithIgnoreCase(resourceValue, getExpandedValue(evalContext));
            }
        }

        @Override
        public boolean isPrefixMatch(String resourceValue, Map<String, Object> evalContext) {
            if (startsWithAnyChar(resourceValue, quoteChars)) {
                return StringUtils.startsWith(getExpandedValue(evalContext), resourceValue);
            } else {
                return StringUtils.startsWithIgnoreCase(getExpandedValue(evalContext), resourceValue);
            }
        }

        int getPriority() {
            return 4 + (getNeedsDynamicEval() ? DYNAMIC_EVALUATION_PENALTY : 0);
        }
    }

    static final class CaseSensitiveEndsWithMatcher extends AbstractStringResourceMatcher {
        CaseSensitiveEndsWithMatcher(String value, Map<String, String> options) {
            super(value, options);
        }

        @Override
        boolean isMatch(String resourceValue, Map<String, Object> evalContext) {
            return StringUtils.endsWith(resourceValue, getExpandedValue(evalContext));
        }

        @Override
        public boolean isPrefixMatch(String resourceValue, Map<String, Object> evalContext) {
            return true; // isPrefixMatch() is always true for endsWith
        }

        int getPriority() {
            return 3 + (getNeedsDynamicEval() ? DYNAMIC_EVALUATION_PENALTY : 0);
        }
    }

    static final class CaseInsensitiveEndsWithMatcher extends AbstractStringResourceMatcher {
        CaseInsensitiveEndsWithMatcher(String value, Map<String, String> options) {
            super(value, options);
        }

        @Override
        boolean isMatch(String resourceValue, Map<String, Object> evalContext) {
            return StringUtils.endsWithIgnoreCase(resourceValue, getExpandedValue(evalContext));
        }

        @Override
        public boolean isPrefixMatch(String resourceValue, Map<String, Object> evalContext) {
            return true; // isPrefixMatch() is always true for endsWith
        }

        int getPriority() {
            return 4 + (getNeedsDynamicEval() ? DYNAMIC_EVALUATION_PENALTY : 0);
        }
    }

    static final class QuotedCaseSensitiveEndsWithMatcher extends AbstractStringResourceMatcher {
        private final String quoteChars;

        QuotedCaseSensitiveEndsWithMatcher(String value, Map<String, String> options, String quoteChars) {
            super(value, options);

            this.quoteChars = quoteChars;
        }

        @Override
        boolean isMatch(String resourceValue, Map<String, Object> evalContext) {
            if (startsWithAnyChar(resourceValue, quoteChars)) {
                return StringUtils.endsWith(resourceValue, getExpandedValue(evalContext));
            } else {
                return StringUtils.endsWithIgnoreCase(resourceValue, getExpandedValue(evalContext));
            }
        }

        @Override
        public boolean isPrefixMatch(String resourceValue, Map<String, Object> evalContext) {
            return true; // isPrefixMatch() is always true for endsWith
        }

        int getPriority() {
            return 4 + (getNeedsDynamicEval() ? DYNAMIC_EVALUATION_PENALTY : 0);
        }
    }

    static final class CaseSensitiveWildcardMatcher extends AbstractStringResourceMatcher {
        CaseSensitiveWildcardMatcher(String value, Map<String, String> options) {
            super(value, options);
        }

        @Override
        boolean isMatch(String resourceValue, Map<String, Object> evalContext) {
            return FilenameUtils.wildcardMatch(resourceValue, getExpandedValue(evalContext), IOCase.SENSITIVE);
        }

        @Override
        public boolean isPrefixMatch(String resourceValue, Map<String, Object> evalContext) {
            return ResourceMatcher.wildcardPrefixMatch(resourceValue, getExpandedValue(evalContext), IOCase.SENSITIVE);
        }

        int getPriority() {
            return 5 + (getNeedsDynamicEval() ? DYNAMIC_EVALUATION_PENALTY : 0);
        }
    }

    static final class CaseInsensitiveWildcardMatcher extends AbstractStringResourceMatcher {
        CaseInsensitiveWildcardMatcher(String value, Map<String, String> options) {
            super(value, options);
        }

        @Override
        boolean isMatch(String resourceValue, Map<String, Object> evalContext) {
            return FilenameUtils.wildcardMatch(resourceValue, getExpandedValue(evalContext), IOCase.INSENSITIVE);
        }

        @Override
        public boolean isPrefixMatch(String resourceValue, Map<String, Object> evalContext) {
            return ResourceMatcher.wildcardPrefixMatch(resourceValue, getExpandedValue(evalContext), IOCase.INSENSITIVE);
        }

        int getPriority() {
            return 6 + (getNeedsDynamicEval() ? DYNAMIC_EVALUATION_PENALTY : 0);
        }
    }

    static final class QuotedCaseSensitiveWildcardMatcher extends AbstractStringResourceMatcher {
        private final String quoteChars;

        QuotedCaseSensitiveWildcardMatcher(String value, Map<String, String> options, String quoteChars) {
            super(value, options);

            this.quoteChars = quoteChars;
        }

        @Override
        boolean isMatch(String resourceValue, Map<String, Object> evalContext) {
            IOCase caseSensitivity = startsWithAnyChar(resourceValue, quoteChars) ? IOCase.SENSITIVE : IOCase.INSENSITIVE;

            return FilenameUtils.wildcardMatch(resourceValue, getExpandedValue(evalContext), caseSensitivity);
        }

        @Override
        public boolean isPrefixMatch(String resourceValue, Map<String, Object> evalContext) {
            IOCase caseSensitivity = startsWithAnyChar(resourceValue, quoteChars) ? IOCase.SENSITIVE : IOCase.INSENSITIVE;

            return ResourceMatcher.wildcardPrefixMatch(resourceValue, getExpandedValue(evalContext), caseSensitivity);
        }

        int getPriority() {
            return 6 + (getNeedsDynamicEval() ? DYNAMIC_EVALUATION_PENALTY : 0);
        }
    }

    static final class ResourceMatcherWrapper {
        private final boolean               needsDynamicEval;
        private final List<ResourceMatcher> resourceMatchers;

        ResourceMatcherWrapper() {
            this(false, null);
        }

        ResourceMatcherWrapper(boolean needsDynamicEval, List<ResourceMatcher> resourceMatchers) {
            this.needsDynamicEval = needsDynamicEval;
            this.resourceMatchers = resourceMatchers;
        }

        boolean getNeedsDynamicEval() {
            return needsDynamicEval;
        }

        List<ResourceMatcher> getResourceMatchers() {
            return resourceMatchers;
        }
    }
}
