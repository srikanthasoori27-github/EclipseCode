/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.certification.CertificationStatCounter;
import sailpoint.object.Application;
import sailpoint.object.Certification;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.Certification.Phase;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLClass;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.FilterConverter;

/**
 * @author peter.holcomb
 * UI bean used to create a listing of certifications categorized by application
 * or by manager.  Used on the dashboard to show completion status.
 *
 */
public class CertificationPercentBean extends BaseObjectBean {

    private static final Log log = LogFactory.getLog(CertificationPercentBean.class);

    public static String ATT_CERT_OFFSET = "CertificationPercentBeanCertOffset";

    private float _percentComplete = -1;
    private boolean _showCerts;
    
    /** a flag to tell the ui that this grouping of certs contains a continuous item
     * this is currently being used to determine the color of the incomplete items in the 
     * progress bar
     */
    private boolean _hasContinuous;
    
    /** Bug 4455 - There is a case where an identity could have a total of 0 items open
     * but they still have an open cert due to reassignment.  In this case, we want to show
     * that they have 0/0 but want to differentiate it from people who have no open certs
     */
    private boolean _hasOpenCert;
    
    private String _type;
    private String _name;
    private String _id;
    private String _recipientId;
    private boolean _noQuery;
    private int _completedItems = -1;
    private int _totalItems = -1;
    private int _certifiedItems = -1;
    private int _certificationRequiredItems = -1;
    private int _overdueItems = -1;
    private int _maxCerts;
    private int _offset;
    private int _start;
    private int _end;
    private QueryOptions _qo;
    List<CertificationExpirationBean> _certs;
    List<CertificationExpirationBean> _myCerts;

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////////////////
    //
    // Type
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * An enumeration of Phases or States that a certification can be in.
     */
    @XMLClass(xmlname="CertificationPercentType")
    public static enum Type {
        Manager("Manager"),
        Group("Group"),
        Application("Application"),
        Owner("Owner"),
        Subordinates("Subordinates");

        private String displayName;

        private Type(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // CertificationExpirationBean
    //
    //////////////////////////////////////////////////////////////////////
    /**
     * Inner class used to hold certifications and their expiration.  We are storing
     * them in this style because the certification object does not currently have a
     * property for expiration since its expiration is derived from its work items.
     * The expirationStyle is used on the UI to indicate that the certification is
     * expiring soon.
     */

    public static class CertificationExpirationBean 
    {
        Certification _certification;
        Date _expiration;
        String _expirationStyle;
        String _certifierIds;
        SailPointContext _context;
        
        int _daysUntilExpiration;

        public CertificationExpirationBean(Certification cert, Date expiration, SailPointContext context)
        {
            this._certification = cert;
            this._expiration = expiration;
            this._context = context;

            if(expiration!=null)
            {
                //Figure out when the certificaiton is expiring related to today.
                this._daysUntilExpiration = Util.getDaysDifference(expiration, new Date());

                if(this._daysUntilExpiration < 0)
                    this._expirationStyle = "detailsCritDiv";
                else if(this._daysUntilExpiration <=7)
                    this._expirationStyle = "detailsWarnDiv";
                else
                    this._expirationStyle = "detailsDiv";
            }
            else
            {
                this._expirationStyle = "detailsDiv";
                this._daysUntilExpiration = 9999;
            }

        }
        /**
         * @return the _certification
         */
        public Certification getCertification() {
            return _certification;
        }
        public String getShortName() throws GeneralException {
            return _certification.getShortName();
        }
        /**
         * @return the _expiration
         */
        public Date getExpiration() {
            return _expiration;
        }
        /**
         * @return the _expirationStyle
         */
        public String getExpirationStyle() {
            return _expirationStyle;
        }
        /**
         * @return the _daysUntilExpiration
         */
        public int getDaysUntilExpiration() {
            return _daysUntilExpiration;
        }
        
        public String getCertifierIds() throws GeneralException {
            if(_certifierIds==null && _certification!=null) {
                List<String> certifiers = _certification.getCertifiers();
                
                List<Filter> filters = new ArrayList<Filter>();
                List<String> certifierIds = new ArrayList<String>();
                for(String certifier : certifiers) {
                    filters.add(Filter.eq("name", certifier));
                }
                
                QueryOptions qo = new QueryOptions();
                qo.add(Filter.or(filters));
                
                List<String> props = new ArrayList<String>();
                props.add("id");
                Iterator<Object[]> rows = _context.search(Identity.class, qo, props);
                while(rows.hasNext()) {
                    String id = (String)rows.next()[0];
                    certifierIds.add(id);
                }
                
                if(!certifierIds.isEmpty())
                    _certifierIds = certifierIds.toString();
            }
            return _certifierIds;
        }

        /* This method will get the reassignment limit configured on Certification Definition and it will find the current reassignment count from certification id
         * it will compare the count and if exceeds it will return true - indicating reassignment should be limited now.
         */
        public boolean isLimitReassignments() throws GeneralException {
            return (_certification.limitCertReassignment(_context));
        }

        /*
         * Return an indication of whether the certification can be forwarded. In
         * general a certification cannot be forwarded if it is staged, has been
         * completed and signed or has more than one certifier.
         * @return a boolean indicating if this certification can be forwarded
         */
        public boolean isForwardable() throws GeneralException {
            boolean isForwardable = true;

            if (Phase.Staged.equals(_certification.getPhase())) {
                isForwardable = false;
            }
            else if (null != _certification.getCertifiers() && _certification.getCertifiers().size() > 1) {
                isForwardable = false;
            }
            else if (_certification.hasBeenSigned()) {
                isForwardable = false;
            }

            return isForwardable;
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public CertificationPercentBean()
    {
        super();
        init();
    }

    public CertificationPercentBean(String id, Type type, Filter filter) throws GeneralException
    {
        super();
        init();
        _id = id;
        try{
            if(type.equals(Type.Manager) || (type == Type.Owner))
            {
                _recipientId = id;
                
                Identity identity = getContext().getObjectById(Identity.class, id);
                _qo.add(Filter.containsAll("certifiers", Arrays.asList(new String[] { identity.getName() })));
                setName(identity.getDisplayableName());

            }
            else if(type.equals(Type.Application))
            {
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq("id", id));
                List<String> atts = new ArrayList<String>();
                atts.add("name");
                atts.add("owner.id");
                Iterator<Object[]> rows = getContext().search(Application.class, ops, atts);
                if (rows.hasNext()) {
                    Object[] next = rows.next();
                    String name = (String)next[0];
                    _recipientId = (String)next[1];                    
                    setName(name);
                }
                _qo.add(Filter.eq("applicationId", id));
            }
            else if(type.equals(Type.Group))
            {
                if(filter!=null) {
                    _qo.add(Filter.join("certifiers", "Identity.name"));
                    filter = FilterConverter.convertFilter(filter, Identity.class);
                    _qo.add(filter);                    
                }  
            }

            // Only include certs that are in scope or owned by the logged in user.
            List<String> certifiers = new ArrayList<String>();
            certifiers.add(super.getLoggedInUserName());
            List<String> wgs =  getLoggedInUsersWorkgroupNames();
            if ( Util.size(wgs) > 0 ) {
                certifiers.addAll(wgs);
            }
            _qo.setScopeResults(true);
            _qo.extendScope(Filter.containsAll("certifiers", certifiers));
        } 
        catch (GeneralException ge)
        {
            log.error("GeneralException ge: [" + ge.getMessage() + "]");
        }
    }

    private void init() {
        try {
            _qo = new QueryOptions();
            _qo.add(Filter.isnull("signed"));
            _qo.add(Filter.ne("phase", Certification.Phase.Staged));
            
            _qo.addOrdering("expiration", true);
            _qo.addOrdering("name", true);
            
            Configuration sysConfig = getContext().getConfiguration();
            _maxCerts = Integer.parseInt((String)sysConfig.get(Configuration.DASHBOARD_MAX_CERT_PERCENTS));
        } catch( GeneralException ge) {
            log.error("Unable to get Sysconfig from context. Exception " + ge.getMessage());
            _maxCerts = 5;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**Ajax action fired when a user clicks on the name of the manager or of the group/application
     * to display the details.  It will fire off and cause a query to be run to load all of the certifications
     * for this user/app. **/
    public String loadCerts() {        
        _showCerts = true;
        getSessionScope().remove(ATT_CERT_OFFSET  + _id);

        return "loadCerts";
    }

    /**Ajax action to page the certs shown on the dashboard for each user/application **/
    public String nextCerts() {
        return this.pageCerts(true);
    }

    /**Ajax action to page the certs shown on the dashboard for each user/application **/
    public String prevCerts() {
        return this.pageCerts(false);
    }

    private String pageCerts(boolean isNext) {
        Map<String, Object> session = getSessionScope();
        _offset = session.containsKey(ATT_CERT_OFFSET  + _id) ? (Integer)session.get(ATT_CERT_OFFSET  + _id) : 0;
        _offset = isNext ? _offset + _maxCerts : _offset - _maxCerts;
        this.afterPageCerts();

        return isNext ? "nextCerts" : "prevCerts";
    }

    /**
     * After we page certs, update the session with the current offset and fields on the bean.
     */
    private void afterPageCerts() {
        _showCerts = true;
        _myCerts = null;
        getSessionScope().put(ATT_CERT_OFFSET  + _id, _offset);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public void loadCounts() {
        int numCerts = 0;
        try {
            _totalItems = 0;
            _completedItems = 0;
            _certifiedItems = 0;
            _certificationRequiredItems = 0;
            _overdueItems = 0;

            if(!_noQuery) {
                List<String> atts = new ArrayList<String>();
                atts.add("statistics.completedItems");
                atts.add("statistics.certifiedItems");
                atts.add("statistics.certificationRequiredItems");
                atts.add("statistics.overdueItems");
                atts.add("statistics.totalItems");
                atts.add("continuous");
                atts.add("signed");
                
                Iterator<Object[]> rows = getContext().search(Certification.class, _qo, atts);
                
                if (rows != null) {
                    while (rows.hasNext()) {
                        Object[] row = rows.next();
                        int completedItems = ((Integer)(row[0]));
                        int certifiedItems = ((Integer)(row[1]));
                        int certificationRequiredItems = ((Integer)(row[2]));
                        int overdueItems = ((Integer)(row[3]));
                        int totalItems = ((Integer)(row[4]));
                        boolean continuous = (Boolean) row[5];
                        
                        /** If the signed value is null, it's open. */
                        if(row[6]==null)
                            _hasOpenCert = true;

                        numCerts += 1;
                        _completedItems = _completedItems + completedItems;
                        _certifiedItems = _certifiedItems + certifiedItems;
                        _certificationRequiredItems = _certificationRequiredItems + certificationRequiredItems;
                        _overdueItems = _overdueItems + overdueItems;
                        _totalItems = _totalItems + totalItems;

                        // If the cert isn't continuous, we'll add any items that
                        // aren't complete to the "yellow" bar, and any items that
                        // are complete to the "green" bar.
                        if (!continuous) {
                            _certifiedItems += completedItems;
                            _certificationRequiredItems += (totalItems - completedItems);
                        } else {
                            _hasContinuous = true;
                        }
                    }
                }
            }
        } catch (GeneralException ge)
        {
            log.error("GeneralException encountered while getting percent complete.  Exception: [" + 
                    ge.getMessage() + "] " );
        }
        
        _percentComplete = CertificationStatCounter.calculatePercentComplete(_completedItems, _totalItems);
    }

    /**
     * @return the percentComplete
     */
    public float getPercentComplete() {

        if(_percentComplete<0){
            _percentComplete = 0;
            loadCounts();            
        }

        return _percentComplete;
    }

    public List<CertificationExpirationBean> getOpenCertificationsForCurrentUser()
    throws GeneralException
    {
        if(_myCerts == null){
            _certs=null;
            _qo.add(Filter.containsAll("certifiers", Arrays.asList(new String [] { getLoggedInUser().getName() } )));
            _qo.add(Filter.ne("phase", Certification.Phase.Staged));
            _showCerts = true;
            _myCerts = getCertifications();
        }
        return _myCerts;

    }

    public int getOpenCertificationsCountForCurrentUser()
    throws GeneralException
    {
        List<String> names = new ArrayList<String>();
        names.add(getLoggedInUser().getName());
        _qo.add(Filter.containsAll("certifiers", names));
        _qo.add(Filter.ne("phase", Certification.Phase.Staged));
        
        return getContext().countObjects(Certification.class, _qo);
    }

    public String getCurrentUserStatusText() {
        return getMessage(MessageKeys.CERT_PERCENT_LIST_GROUP_STATUS_TEXT, _start, _end);
    }

    /** Returns the number of certifications for this manager/application **/
    public int getCertificationsCount() throws GeneralException 
    {
        return getContext().countObjects(Certification.class, _qo);
    }

    /** Returns the certifications for this manager/application.  Limited by the 
     * dashboardMaxCertPercents configuration setting that limits the number of
     * certs that are shown on the dashboard. **/
    public List<CertificationExpirationBean> getCertifications() throws GeneralException
    {
        if(_showCerts && _certs==null && !_noQuery)
        {
            _start = 1;
            _end = _maxCerts;

            log.debug("Limiting getCertifications to " + _maxCerts + " certifications." );
            _qo.setResultLimit(_maxCerts);
            if(_offset>0) {
                _qo.setFirstRow(_offset);                
                _start = _start + _offset;
                _end = _end + _offset;
            }

            Iterator<Certification> certs = getContext().search(Certification.class, _qo);
            if(certs!=null)
            {
            	int i=0;
                _certs = new ArrayList<CertificationExpirationBean>();
                while(certs.hasNext()) {
                	i++;
                    Certification cert = certs.next();
                    _certs.add(new CertificationExpirationBean(cert, cert.getExpiration(), getContext()));
                }
                if(_end>=i)
                    _end = _start + i-1;
            }
        }

        return _certs;        
    }

    public String getPercentClass()
    {
        return "progressBarComplete";        
    }

    /**
     * @param percentComplete the percentComplete to set
     */
    public void setPercentComplete(float percentComplete) {
        this._percentComplete = percentComplete;
    }

    /**
     * @return the name
     */
    public String getType() {
        return _type;
    }

    /**
     * @param name the name to set
     */
    public void setType(String type) {
        this._type = type;
    }



    /**
     * @return the _name
     */
    public String getName() {
        return _name;
    }

    /**
     * @param _name the _name to set
     */
    public void setName(String _name) {
        this._name = _name;
    }


    /**
     * @return the _openItems
     */
    public int getCompletedItems() {
        if(_completedItems<0)
            loadCounts();
        return _completedItems;
    }


    /**
     * @param items the _openItems to set
     */
    public void setCompletedItems(int items) {
        _completedItems = items;
    }


    /**
     * @return the _totalItems
     */
    public int getTotalItems() {
        if(_totalItems<0)
            loadCounts();
        return _totalItems;
    }


    /**
     * @param items the _totalItems to set
     */
    public void setTotalItems(int items) {
        _totalItems = items;
    }

    /**
     * @return the _maxCerts
     */
    public int getMaxCerts() {
        return _maxCerts;
    }

    /**
     * @param certs the _maxCerts to set
     */
    public void setMaxCerts(int certs) {
        _maxCerts = certs;
    }

    /**
     * @return the _id
     */
    public String getId() {
        return _id;
    }

    /**
     * @param _id the _id to set
     */
    public void setId(String _id) {
        this._id = _id;
    }

    /**
     * @return the _recipientId
     */
    public String getRecipientId() {
        return _recipientId;
    }

    /**
     * @param id the _recipientId to set
     */
    public void setRecipientId(String id) {
        _recipientId = id;
    }

    /**
     * @return the showCerts
     */
    public boolean isShowCerts() {
        return _showCerts;
    }

    /**
     * @param showCerts the showCerts to set
     */
    public void setShowCerts(boolean showCerts) {
        this._showCerts = showCerts;
    }

    /**
     * @return the _offset
     */
    public int getOffset() {
        return _offset;
    }

    /**
     * @param _offset the _offset to set
     */
    public void setOffset(int _offset) {
        this._offset = _offset;
    }

    /**
     * @return the _end
     */
    public int getEnd() {
        return _end;
    }

    /**
     * @param _end the _end to set
     */
    public void setEnd(int _end) {
        this._end = _end;
    }

    /**
     * @return the _start
     */
    public int getStart() {
        return _start;
    }

    /**
     * @param _start the _start to set
     */
    public void setStart(int _start) {
        this._start = _start;
    }

    /**
     * @return the _noQuery
     */
    public boolean is_noQuery() {
        return _noQuery;
    }

    /**
     * @param query the _noQuery to set
     */
    public void set_noQuery(boolean query) {
        _noQuery = query;
    }

	public String getOpenCertsEmailTemplate() {
		return Configuration.OPEN_CERTIFICATIONS_EMAIL_TEMPLATE;
	}

    public int getCertifiedItems() {
        return _certifiedItems;
    }

    public void setCertifiedItems(int items) {
        _certifiedItems = items;
    }

    public int getCertificationRequiredItems() {
        return _certificationRequiredItems;
    }

    public void setCertificationRequiredItems(int items) {
        _certificationRequiredItems = items;
    }

	public int getOverdueItems() {
		return _overdueItems;
	}

	public void setOverdueItems(int items) {
		_overdueItems = items;
	}
    
    public boolean isHasContinuous() {
        return _hasContinuous;
    }
    
    public void setHasContinuous(boolean hasContinuous) {
        _hasContinuous = hasContinuous;
    }

    public boolean isHasOpenCert() {
        return _hasOpenCert;
    }

    public void setHasOpenCert(boolean openCert) {
        _hasOpenCert = openCert;
    }
}
