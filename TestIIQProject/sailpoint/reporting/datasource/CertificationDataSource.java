/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Certification;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationGroup;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SignOffHistory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.util.WebUtil;

/**
 * @author peter.holcomb
 *
 */
public class CertificationDataSource extends SailPointDataSource<Certification> {

    private static final Log log = LogFactory.getLog(CertificationDataSource.class);

    public CertificationDataSource(List<Filter> filters, Locale locale, TimeZone timezone) {
        super(filters, locale, timezone);
        setScope(Certification.class);
    }

    @Override
    public void internalPrepare() throws GeneralException {
        updateProgress("Querying for certification entries");
        _objects = getContext().search(Certification.class, qo);
    }

    /* (non-Javadoc)
     * @see net.sf.jasperreports.engine.JRDataSource#getFieldValue(net.sf.jasperreports.engine.JRField)
     */
    public Object getFieldValue(JRField jrField) throws JRException {
        Object value = null;
        String fieldName = jrField.getName();
        if ( fieldName != null ) {

            if(fieldName.equals("certifiers")) {
                value = Util.listToCsv(_object.getCertifiers());
            } if(fieldName.equals("Application.name")) {
                try {
                    value = _object.getApplication(getContext()).getName();
                } catch(GeneralException ge) {
                    log.warn("Unable to load application for cert.  Exception : " + ge.getMessage());
                }
            }
            else if(fieldName.equals("roles")) {
                if(_object.getType().equals(Certification.Type.BusinessRoleComposition) ||
                        _object.getType().equals(Certification.Type.BusinessRoleMembership)) {
                    List<CertificationEntity> entities = _object.getEntities();
                    if(entities!=null) {
                        List<String> roles = new ArrayList<String>();
                        for(CertificationEntity entity : entities) {
                            if(!roles.contains(entity.getTargetName())) {
                                roles.add(entity.getTargetName());
                            }
                        }
                        value = Util.listToCsv(roles);
                    }
                }
            } else if(fieldName.equals("accountGroups")) {
                if(_object.getType().equals(Certification.Type.AccountGroupPermissions) ||
                        _object.getType().equals(Certification.Type.AccountGroupMembership)) {
                    List<CertificationEntity> entities = _object.getEntities();
                    if(entities!=null) {
                        List<String> accountGroups = new ArrayList<String>();
                        for(CertificationEntity entity : entities) {
                            if(!accountGroups.contains(entity.getAccountGroup())) {
                                accountGroups.add(entity.getAccountGroup());
                            }
                        }
                        value = Util.listToCsv(accountGroups);
                    }
                }
            } else if(fieldName.equals("tags.id")) {
                try {
                    value = WebUtil.objectListToNameString(_object.getTags());
                } catch(GeneralException ge) {
                    log.warn("Exception caught while getting tags for cert: " + ge.getMessage());
                }
            } else if(fieldName.equals("certificationGroup")) {
                try{
                    if (!Util.isNullOrEmpty(_object.getCertificationDefinitionId())) {
                        QueryOptions qo = new QueryOptions();
                        qo.add(Filter.eq("definition.id", _object.getCertificationDefinitionId()));
                        List<String> props = Arrays.asList(new String[] {"name"});

                        Iterator<Object[]> rows = getContext().search(CertificationGroup.class, qo, props);
                        if (rows.hasNext()) {
                            value = rows.next()[0];
                        }
                    }
                } catch(GeneralException ge) {
                    log.warn("Exception caught while getting certification group for cert: " + ge.getMessage());
                }
                
            }
            /*
             * IIQBUGS-153 :- Adding implementation to allow to export to PDF and CSV the "signed By" column.
             */
            else if (fieldName.equals("signOffHistory.signerDisplayName")) {
                if (_object.getSignOffHistory() != null) {
                    //There is a relationship between certification and signOffHistory of 1:N and
                    //I'm assuming that we are going to have only one signOffHistory record per certification
                    for (SignOffHistory soh : _object.getSignOffHistory()) {
                        if (soh.getDate() != null) { //if sign_date is null, it was not signed off
                            value = soh.getSignerDisplayName();
                        }
                    }
                }
            }

            if(value==null)
                value = super.getFieldValue(jrField);
        }
        return value; 
    }

    public boolean internalNext() throws JRException {
        boolean hasMore = false;
        if ( _objects != null ) {
            hasMore = _objects.hasNext();
            if ( hasMore ) {
                try {
                    // clear the last one...
                    if ( _object != null ) {
                        getContext().decache(_object);
                    }
                } catch (Exception e) {
                    log.warn("Unable to decache certificatoin." + e.toString());
                }
                _object = _objects.next();
                if ( _object != null ) {
                    updateProgress("Certification", _object.getName());
                }
            } 
        }
        return hasMore;
    }
}
