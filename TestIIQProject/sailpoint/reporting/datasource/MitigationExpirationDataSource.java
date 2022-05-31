/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.BaseConstraint;
import sailpoint.object.Bundle;
import sailpoint.object.MitigationExpiration;
import sailpoint.object.Policy;
import sailpoint.tools.GeneralException;

public class MitigationExpirationDataSource extends SailPointDataSource<MitigationExpiration> {
	private static Log log = LogFactory.getLog(MitigationExpirationDataSource.class);

	public MitigationExpirationDataSource(List filters, Locale locale, TimeZone timezone) {
		super(filters, locale, timezone);
		qo.setOrderBy("expiration");
		setScope(MitigationExpiration.class);
	}

	@Override
	public void internalPrepare() throws GeneralException {
		updateProgress("Querying for Mitigations...");
		_objects = getContext().search(MitigationExpiration.class, qo);

	}

	@Override
	public Object getFieldValue(JRField jrField) throws JRException {
		String fieldName = jrField.getName();

		Object value = null;
		try {
			if(fieldName.equals("constraintName")) {
				if(_object.getPolicyViolation()!=null) {
					BaseConstraint constraint = 
                        _object.getPolicyViolation().getConstraint(getContext());
					if(constraint!=null)
						value = constraint.getName();
				}
			} else if(fieldName.equals("businessRoleMapList")) {
                Bundle role = _object.getBusinessRole(getContext());
				if(role != null) {
					List<Map> bundleMapList = new ArrayList<Map>();
					Map<String, String> bundleMap = new HashMap<String, String>();
					bundleMap.put("name", role.getName());
					bundleMap.put("description", role.getDescription());
					bundleMapList.add(bundleMap);
					value = bundleMapList;
				} else if(fieldName.equals("policyViolationMapList")) {
					if(_object.getPolicyViolation()!=null) {
						BaseConstraint constraint =
                            _object.getPolicyViolation().getConstraint(getContext());
						if(constraint!=null) {
							List<Map> violationMapList = new ArrayList<Map>();
							Map<String, String> violationMap = new HashMap<String, String>();
							violationMap.put("constraintDescription", constraint.getDescription());
							violationMap.put("compensatingControl", constraint.getCompensatingControl());
							violationMap.put("violationSummary", constraint.getName());							
							Policy p = constraint.getPolicy();							
							if(p!=null) {
								violationMap.put("policyName", p.getName());
							}
							violationMapList.add(violationMap);
							value = violationMapList;
						}						
					}
				}
			}
		} catch(GeneralException ge) {
			log.info("Unable to get value for fieldName: " + fieldName + ". Exception: " + ge.getMessage());
		}
        
        if(value==null)
			value = super.getFieldValue(jrField);

		return value;
	}

	@Override
	public boolean internalNext() throws JRException {
		boolean hasMore = false;
		if ( _objects != null ) {
			hasMore = _objects.hasNext();
			if ( hasMore ) {
				_object = _objects.next();
			} else {
				_object = null;
			}

			if ( _object != null ) {
				updateProgress("Mitigation", _object.getName());
			}
		}
		return hasMore;
	}

}
