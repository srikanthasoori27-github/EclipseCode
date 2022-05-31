/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A cache of Certifications within a CertificationGroup.
 * The cache is keyed by the owner list, and it will initialize new
 * certs if there is a cache miss.
 * 
 * Author: Jeff
 *
 * This is to support more flexible calculation of ownership for
 * entities and items.  Rather than ownership being calculated when the
 * cert schedule, or be calculated up front by the old CertificationContexts
 * we'll allow ownership to change dynamically as we iterate over entities.
 * 
 * The Certificadtion objects are not in the Hibernate session and should
 * not be modified.  If you need to modify one, get a fresh one.  The only
 * thing these provide is the Hibernate id of a cert with a particular
 * collection of certifiers.  You can assign one as to the 
 * CertificationEntity.certification property and save it.  Now that
 * we use <bag> mapping insteas of <list> mapping we do not have contention
 * on the Certification.entities list among partition threads.
 *
 * This is expected to evolve as we get more clarity on ownership options, 
 * but in theory the worst case is an ownership rule that returns a different
 * owner for every entity.
 *
 * TODO: If we hit a stream of entities that have the same certifiers it
 * is going to suck having to generate a key and hit the map over and over.
 * Need a faster cache of the last Certification we returned and just compare
 * the key.  Or maybe look at the Java object.  
 */

package sailpoint.certification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;

import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationGroup;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class CertificationCache {

	private static Log log = LogFactory.getLog(CertificationCache.class);

    SailPointContext _context;
    String _groupId;

    /**
     * Utility class encapsulating the initialization of a new Certification.
     */
    CertificationInitializer _initializer;
    
    /**
     * The cache of certifications.
     * The key is a csv of owner names.  This may grow large, could
     * consider hashing it, but the HashMap does that anyway.  It might
     * make the hash entry collision list search faster though.
     */
    Map<String,Certification> _cache;

    /**
     * Remember this so we can find the root cert easily by name.
     */
    String _defaultCertifier;

    /**
     * Test option to remember the certs we created.
     */
    boolean _saveCreated;
    List<String> _created;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Construction
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * TODO: Will need more work on how certifiers are specified.
     * Continue expecting them in the CertificationDefinition, and
     * default to the owner.
     */
    public CertificationCache(SailPointContext con,
                              CertificationDefinition def,
                              CertificationGroup group,
                              Certification rootCert)
        throws GeneralException {

        _context = con;
        // we don't need the group, but we use it as a semaphor when
        // bootstrapping certs so just save the id
        _groupId = group.getId();
        
        _initializer = new CertificationInitializer(con, def, group);
        _cache = new HashMap<String,Certification>();

        // put the root cert in the cache so ownershiip results that happen
        // to be the same as the backup certifier will find it without
        // creating another one
        List<String> rootCertifiers = rootCert.getCertifiers();
        // better have been checked before now
        if (rootCertifiers == null || rootCertifiers.size() == 0)
            throw new GeneralException("Missing root certifiers list");
            
        // in the current UI there can be only one
        _defaultCertifier = rootCertifiers.get(0);
        _cache.put(_defaultCertifier, rootCert);
    }

    public String getDefaultCertifier() {
        return _defaultCertifier;
    }

    public void setSaveCreated(boolean b) {
        _saveCreated = b;
        _created = new ArrayList<String>();
    }

    public List<String> getCreated() {
        return _created;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Cache
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return all of the Certifictions in the cache.
     */
    public Collection<Certification> getCertifications() {

        return _cache.values();
    }
    
    /**
     * Return the Certification for a single certifier.
     */
    public Certification getCertification(String name)
        throws GeneralException {

        return getCertification(name, name, null);
    }
    
    /**
     * Return a certification for a list of certifiers.
     */
    public Certification getCertification(List<String> names)
        throws GeneralException {

        Certification cert = null;
        
        if (Util.isEmpty(names))
            throw new GeneralException("Empty certifier name list");

        if (names.size() == 1) {
            // simplify
            String name = names.get(0);
            cert = getCertification(name, name, null);
        }
        else {
            // patent pending key generation
            String key = Util.listToCsv(names);
            cert = getCertification(key, null, names);
        }

        return cert;
    }
    
    /**
     * Return the Certification for the given key.
     * Either name or names is given to the initializer.  Would be slightly simpler to 
     * just standardize on List<String> but I hate doing that since it is the exception.
     */
    private Certification getCertification(String key, String name, List<String> names)
        throws GeneralException {

        if (names == null) {
            names = new ArrayList<String>();
            names.add(name);
        }

        Certification cert = _cache.get(key);
        
        if (cert == null) {
            // use the CertifcationGroup as a semaphor
            // could use anything really since the group is not modified
            try {
                //Shouldn't need to decache here, as the PersistenceManager should disable cache -rap
                //IIQPB-949 - Decache was blowing away IdentitySnapshots in some cases
                //_context.decache();
                CertificationGroup group = ObjectUtil.transactionLock(_context, CertificationGroup.class, _groupId);
                if (group == null) {
                    // this really shouldn't happen
                    throw new GeneralException("CertificationGroup evaporated!");
                }
                else {
                    // rebuild the cache with whatever is out there now.
                    // IIQSAW-2948: only look at certs containing the same certifiers as what was
                    // passed into this method. This lets us focus on the certs we care about, and
                    // keep the result set to a reasonable size.
                    QueryOptions ops = new QueryOptions();
                    ops.add(Filter.contains("certificationGroups", group));
                    ops.add(Filter.containsAll("certifiers", names));

                    List<Certification> certs = _context.getObjects(Certification.class, ops);
                    for (Certification newcert : Util.iterate(certs)) {
                        
                        // build the equivalent key
                        List<String> certifiers = newcert.getCertifiers();
                        String newkey = Util.listToCsv(certifiers);
                        // don't trash a cert that is currently in the cache, it may be
                        // accumulating an entitiesToRefresh list
                        if (!_cache.containsKey(newkey)) {
                            _cache.put(newkey, newcert);
                        }
                    }

                    // now look agin
                    cert = _cache.get(key);
                    if (cert == null) {
                        // bootstrap one
                        cert = _initializer.createCertification(names);
                        _cache.put(key, cert);
                        if (log.isInfoEnabled()) {
                            log.info("Created: " + cert.getName());
                        }
                        if (_saveCreated) {
                            _created.add(cert.getName());
                        }
                    }
                }
            }
            finally {
                // make sure the lock is released
                try {
                    _context.rollbackTransaction();
                }
                catch (Throwable t) {
                    log.error("Error rolling back transaction: " + t);
                }
            }
        }
        
        return cert;
    }

}
