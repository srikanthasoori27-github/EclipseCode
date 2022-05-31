/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.rapidsetup.tools;

import java.util.ArrayList;
import java.util.List;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.Util;

/**
 * Provides some utility methods for dealing with LDAP
 * distinguished names
 */
public class LdapTools {

    private static Log log = LogFactory.getLog(LdapTools.class);

    /**
     * Return the first OU in the given list that shares the DC as the
     * native id
     * @param ouCsv a pipe-separated list of OU strings
     * @param nativeId nativeId for the account, expected to be a distinguished name
     * @return a compatible OU, or null if none found
     */
    public static String findCompatibleOU(String ouCsv, String nativeId) {
        String compatibleOU = null;

        List<String> ouList = new ArrayList<>();
        if(Util.isNotNullOrEmpty(ouCsv) && Util.isNotNullOrEmpty(nativeId)) {
            if(ouCsv.indexOf("|") > 0){
                String[] moveAccountContainerArray = ouCsv.split("\\|");
                for(String ou :moveAccountContainerArray) {
                    ouList.add(ou);
                }
            }
            else {
                ouList.add(ouCsv);
            }
        }
        log.debug("...ouList " + ouList);
        if(!Util.isEmpty(ouList)) {
            log.debug("...nativeId " + nativeId);
            String dcPathFromDN = getDCPathFromDistinguishedDN(nativeId, true);
            log.debug("...dcPathFromDn " + dcPathFromDN);
            if (dcPathFromDN == null) {
                log.warn("nativeId " + nativeId + " contains no DC components");
                return null;
            }
            String dcPathFromDNUpper = dcPathFromDN.toUpperCase();
            log.debug("...dcPathFromDnUpper " + dcPathFromDNUpper);
            for(String ouStr : ouList) {
                String ouStrUpper = ouStr.toUpperCase();
                log.debug("...ouStrUpper " + ouStrUpper);
                if(ouStrUpper != null && ouStrUpper.contains(dcPathFromDNUpper)) {
                    compatibleOU = ouStr;
                    log.debug("...ouStr "+ouStr);
                    log.debug("...compatibleOU "+ compatibleOU);
                    break;
                }
            }
        }
        return compatibleOU;
    }

    /**
     * Get domain container path from distinguishedName
     * @param distinguishedName the full distinguished name to examine
     * @param warnLog if true, then log warning if the distinguishedName has syntax errors
     *                or has no DC components
     * @return the substring of the distinguishedName which is composed of the DC components
     */
    static String getDCPathFromDistinguishedDN(String distinguishedName, boolean warnLog) {
        String dcPath = null;
        if (Util.isNotNullOrEmpty(distinguishedName)) {
            try {
                LdapName dn = new LdapName(distinguishedName);
                LdapName dc = new LdapName("");
                List<Rdn> rdns = dn.getRdns();
                for(Rdn rdn : Util.safeIterable(rdns)) {
                    if (rdn.getType().equalsIgnoreCase("dc")) {
                        dc.add(rdn);
                    }
                    else {
                        break;
                    }
                }
                if (!dc.isEmpty()) {
                    dcPath = dc.toString();
                }
                else {
                    if (warnLog) {
                        log.warn(distinguishedName + " contains no DC components");
                    }
                }
            } catch (InvalidNameException e) {
                if (warnLog) {
                    log.warn(distinguishedName + " is not a valid DN");
                }
            }
        }
        return dcPath;
    }

}
