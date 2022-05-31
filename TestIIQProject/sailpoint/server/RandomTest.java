/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */


package sailpoint.server;

import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.apache.commons.beanutils.PropertyUtils;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Query;

import sailpoint.api.CertificationBuilder;
import sailpoint.api.CertificationBuilderFactory;
import sailpoint.api.Certificationer;
import sailpoint.api.Differencer;
import sailpoint.api.IdentityArchiver;
import sailpoint.api.IdIterator;
import sailpoint.api.ManagedAttributer;
import sailpoint.api.Meter;
import sailpoint.api.RoleLifecycler;
import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.AuditEvent;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.CertificationArchive;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.Identity;
import sailpoint.object.IdentityDifference;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.PersistenceOptions;
import sailpoint.object.Profile;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.TaskResult;
import sailpoint.object.Template;
import sailpoint.object.SailPointObject;
import sailpoint.object.UIConfig;
import sailpoint.persistence.UnitTestFactory;
import sailpoint.persistence.ClassMappingUtil;
import sailpoint.search.BeanutilsMatcher;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

public class RandomTest {

    SailPointContext _context;

    public RandomTest(SailPointContext con) {
        _context = con;
    }

    static public void println(Object o) {
        System.out.println(o);
    }

    public void run(List<String> args) throws Exception {

        //roundTripXml(args);
        //runHibernateSearch();
        //runJDBCSearch();
        //drawAPicture();
        //roundTripXml(args);
        //diffLinks(args);
        //moveLink(args);
        //simulateHibernateError(args);
        //testRoleDifferencer(args);
        //testPersistenceWrapper(args);

        //Identity spadmin = _context.getObject(Identity.class, "spadmin");
        //testTarget(spadmin);

        //testBigMaps();
        //testCreationOfNullLinks();

        //testSplit(args);
        //testListSize(args);
        //testIdIterator(args);
        //testListDiff(args);

        //testCacheProblem(args); 
        //testHibernateBatch(args); 
        //testCertArchive(args);
        //testPropertyUtils(args);
        //testExplicitSave(args);
        //testRulePerformance(args);
        //  testCountProperty(args);
        //testPlanCensorship(args);
        //testCamelSplit(args);
        //testPlanLogging(args);

        //testBigTaskResult(args);
        //testLuceneTokenizing(args);
        //testPartitionResult(args);
        //testTemplateConversion(args);
        //testConnectionSpeed(args);
        //testClassMappings(args);
        // testManagedAttributeHash(args);
        testBigRoles(args);
    }

    public void testBigRoles(List<String> args) throws Exception {

        String baseName = "Large";

        genRole(baseName, 5000);
        genRole(baseName, 10000);
        genRole(baseName, 20000);
        genRole(baseName, 30000);
    }

    final String AppName = "Big Role Test";
    
    private void genRole(String baseName, int size)
        throws Exception {

        boolean oneTerm = true;
        
        Application app = _context.getObject(Application.class, AppName);
        if (app == null)
            throw new Exception("Missing application: " + AppName);

        String name = baseName + " " + Util.itoa(size);
        
        Bundle role = _context.getObject(Bundle.class, name);
        if (role == null) {
            role = new Bundle();
            role.setName(name);
            role.setType("business");
        }
        else {
            role.setRequirements(null);
        }
        
        String itname = name + " - IT";
        Bundle itrole = _context.getObject(Bundle.class, itname);
        if (itrole == null) {
            itrole = new Bundle();
            itrole.setName(itname);
            itrole.setType("it");
        }

        List<Profile> profiles = itrole.getProfiles();
        for (Profile p : Util.iterate(profiles)) {
            _context.removeObject(p);
        }
        itrole.setProfiles(null);
        
        Profile profile = new Profile();
        profile.setApplication(app);
        itrole.add(profile);
            
        if (oneTerm) {
            List<String> names = new ArrayList<String>();
            for (int i = 0 ; i < size ; i++) {
                names.add("Machine: " + Util.itoa(i+1));
            }
            Filter containsAll = Filter.containsAll("groups", names);
            profile.addConstraint(containsAll);
        }
        else {
            List<Filter> terms = new ArrayList<Filter>();
            for (int i = 0 ; i < size ; i++) {
                Filter f = Filter.contains("groups", "Machine: " + Util.itoa(i+1));
                terms.add(f);
            }
            
            Filter andFilter = Filter.and(terms);
            profile.addConstraint(andFilter);
        }
        
        role.addRequirement(itrole);

        _context.saveObject(role);
        _context.saveObject(itrole);
        _context.commitTransaction();

        println("Created role: " + name);
    }
    
    public void testClassMappings(List<String> args) throws Exception {

        ClassMappingUtil.dump();
    }

    public void testManagedAttributeHash(List<String> args) throws Exception {

        String appname = "Schooner Active Directory";
        Application app = _context.getObject(Application.class, appname);
        ManagedAttribute att = new ManagedAttribute();
        att.setApplication(app);
        att.setType(Connector.TYPE_GROUP);
        att.setAttribute("memberOf");
        att.setValue("CN=MondayTest,OU=sailpointtest,DC=test,DC=sailpoint,DC=com");

        String hash = ManagedAttributer.getHash(att);

        System.out.println(hash);
    }

    public void testConnectionSpeed(List<String> args) throws Exception {

        int iterations = 10000;

        Environment env = Environment.getEnvironment();
        javax.sql.DataSource ds = env.getSpringDataSource();

        for (int i = 0 ; i < iterations ; i++) {

            Connection con = ds.getConnection();
            con.close();
                
        }

        Meter.report();
    }

    public void testTemplateConversion(List<String> args) throws Exception {

        if (args.size() < 1) {
            println("test <appname>");
        }
        else {
            Application app = _context.getObject(Application.class, args.get(0));
            if (app == null) {
                println("Invalid application name");
            }
            else {
                List<Template> templates = app.getTemplates();
                for (Template t : Util.iterate(templates)) {
                    println("*** Template " + t.getUsage().toString());
                    println(t.toXml());
                    println("--- Form ---");
                    Form f = new Form(t);
                    println(f.toXml());
                    println("--- Back to Template ---");
                    Template t2 = new Template(f);
                    println(t2.toXml());
                }
            }
        }
    }
    
    public void testPartitionResult(List<String> args) throws Exception {

        Date now = new Date();
        Date zero = new Date(0);

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.notnull("completed"));
        ops.add(Filter.gt("expiration", zero));
        ops.add(Filter.le("expiration", now));

        List<String> props = new ArrayList<String>();
        props.add("id");

        int pruneTotal = _context.countObjects(TaskResult.class, ops);
    }

    public void testBigTaskResult(List<String> args) throws Exception {

        // assumes this TaskResult from Nedra
        String name = "Elite UNIX Account Aggregation";
        String partName = "unix-drum() - Accounts 217 to 246";

        int iterations = 100;

        for (int i = 0 ; i < iterations ; i++) {

            _context.decache();
            TaskResult res = _context.getObjectByName(TaskResult.class, name);
            // seek to one of the last child results
            /*
            TaskResult part = res.getPartitionResult(partName);
            part.setProgress(Util.itoa(i));
            */
            _context.saveObject(res);
            _context.commitTransaction();

        }

    }

    public void testPlanLogging(List<String> args) throws Exception {


        ProvisioningPlan plan = new ProvisioningPlan();
        AccountRequest account = new AccountRequest();
        AttributeRequest att;

        plan.add(account);

        att = new AttributeRequest("foo", "bar");
        account.add(att);
        att = new AttributeRequest("password", "bar");
        account.add(att);
        att = new AttributeRequest("*password*", "bar");
        account.add(att);
        att = new AttributeRequest("USER_PWD", "bar");
        att.put(ProvisioningPlan.ATT_CURRENT_PASSWORD, "baz");
        account.add(att);

        ProvisioningPlan filtered = ProvisioningPlan.getLoggingPlan(plan);
        println(filtered.toXml());
    }

    public void testCamelSplit(List<String> args) throws Exception {

        String prop = "ABCDoneTwoThree4";
        String col = sailpoint.server.ExtendedSchemaGenerator.getColumnName(prop);

        println(col);
    }

    public void testPlanCensorship(List<String> args) throws Exception {

        openconnector.Plan p = new openconnector.Plan();
        openconnector.Request r = new openconnector.Request();
        openconnector.Item i = new openconnector.Item();

        i.setName("password");
        i.setArgument("current", "xyzzy");
        r.add(i);
        p.add(r);

        println(p.toJson());
    }

    public void testCountProperty(List<String> args) throws Exception {

        String prop = "region";
        if (args.size() > 0)
            prop = args.get(0);

        String prop2 = "count(" + prop + ")";
        String prop3 = "count(distinct " + prop + ")";

        testCountProperty(prop);
        testCountProperty(prop2);
        testCountProperty(prop3);
    }

    private void testCountProperty(String prop) throws Exception {

        println("*** " + prop + " ***");

        Iterator<Object[]> result = _context.search(Identity.class, null, prop);
        while (result.hasNext()) {
            Object[] row = result.next();
            println(row[0]);
        }
    }

    public void testRulePerformance(List<String> args) throws Exception {

        Rule rule = _context.getObjectByName(Rule.class, "Test Rule");
        if (rule == null)
            throw new GeneralException("Missing rule");

        Identity ident = _context.getObject(Identity.class, "spadmin");

        Map<String,Object> params = new HashMap<String,Object>();
        params.put("identity", ident);

        int iterations = 1000;

        for (int i = 0 ; i < iterations ; i++) {
            
            _context.runRule(rule, params);
        }

    }

    public void testExplicitSave(List<String> args) throws Exception {

        PersistenceOptions pops = new PersistenceOptions();
        pops.setExplicitSaveMode(true);
        _context.setPersistenceOptions(pops);
        _context.decache();

        println("*** Reading Objects ***");
        
        Identity ident = _context.getObject(Identity.class, "Mary.Johnson");
        List<Link> links = ident.getLinks();
        if (links != null) {
            int count = 0;
            for (Link link : links) {
                System.out.format("%s: %s\n", link.getApplication().getName(),
                                  link.getNativeIdentity());
                if (count == 0)
                    link.setDirty(true);
                count++;
            }
        }

        println("*** Committing Transaction ***");

        _context.commitTransaction();
    }

    public void testPropertyUtils(List<String> args) throws Exception {

        Bundle obj = _context.getObject(Bundle.class, "Tax Accountant");
        Object name = PropertyUtils.getNestedProperty(obj, "owner.name");
        println("Owner name: " + name);

        Filter f = Filter.and(Filter.eq("owner.name", "Patricia.Jones"),
                              Filter.eq("disabled", false),
                              Filter.or(Filter.isnull("assignedScope.path"),
                                        Filter.like("assignedScope.path", "foo", Filter.MatchMode.START)));

        BeanutilsMatcher m = new BeanutilsMatcher(f);
        if (m.matches(obj))
            println("Object matches!");
        else
            println("Object doesn't match!");
    }

    public void testCertArchive(List<String> args) throws Exception {

        if (args.size() != 1) {
            println("usage: test <filename>");
        }
        else {
            String xml = Util.readFile(args.get(0));
            XMLObjectFactory f = XMLObjectFactory.getInstance();
            Object o = f.parseXml(_context, xml, true);
            if (!(o instanceof CertificationArchive)) {
                println("Not a CertificationArchive!");
            }
            else {
                CertificationArchive arch = (CertificationArchive)o;
                String archxml = arch.getArchive();
                Util.writeFile("archive.xml", archxml);

                //Certification cert = arch.decompress(_context, null);
                //println("Cert decompressed");
            }
        }
    }

    public void testHibernateBatch(List<String> args) throws Exception {

        for (int i = 0 ; i < 10 ; i++) {
            AuditEvent ev = new AuditEvent();
            _context.saveObject(ev);
        }
        _context.commitTransaction();
    }

    public void testListDiff(List<String> args) throws Exception {

        List<String> list1 = new ArrayList<String>();
        list1.add("foo");

        List<String> list2 = new sailpoint.tools.xml.PersistentArrayList<String>();
        list2.add("foo");

        if (list1.getClass().isAssignableFrom(list2.getClass()))
            println("list 1 assignable from list 2");

        else if (list2.getClass().isAssignableFrom(list1.getClass()))
            println("list 2 assignable from list 1");
        
        else
            println("lists are not assignable");

        if (sailpoint.api.Differencer.objectsEqual(list1, list2))
            println("objects are equal");
        else
            println("objects are not equal");
    }

    public void testIdIterator(List<String> args) throws Exception {

        // first a typical query by id
        List<String> props = new ArrayList<String>();
        props.add("id");
        Iterator<Object[]> result = _context.search(Identity.class, null, props);
        println("context.search result");
        while (result.hasNext()) {
            Object[] row = result.next();
            String id = (String)row[0];
            println(id);
        }

        // same thing with IdIterator
        println("IdIterator result");
        result = _context.search(Identity.class, null, props);
        IdIterator idi = new IdIterator(result);
        while (idi.hasNext()) {
            String id = idi.next();
            println(id);
        }
    }


    public void testListSize(List<String> args) {

        Runtime rt = Runtime.getRuntime();
        rt.gc();

        // get an idea for how much heap space is requird to store
        // large query results of object ids
        long start = getMemoryUsage();
        println("Memory usage at start: " + Util.ltoa(start));

        println("Allocating");
        int max = 100000;
        List<String> ids = new ArrayList<String>();
        for (int i = 0 ; i < max ; i++) 
            ids.add(Util.uuid());
        
        long end = getMemoryUsage();
        println("Memory usage at after allocation: " + Util.ltoa(end));
        println("Delta: " + Util.ltoa(end - start));

        println("Freeing");
        ListIterator<String> it = ids.listIterator();
        int count = 0;
        while (it.hasNext()) {
            it.next();
            it.remove();
            count++;
            if (count >= 10000) { 
                count = 0;
                rt.gc();
                long after = getMemoryUsage();
                println("Memory usage at after gc: " + Util.ltoa(after));
                println("Delta: " + Util.ltoa(after - start));
            }
        }

        long last = getMemoryUsage();
        println("Memory usage at end: " + Util.ltoa(last));
        println("Delta: " + Util.ltoa(last - start));
    }

    public long getMemoryUsage() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    public void testSplit(List<String> args) {

        String delimiter = null;
        if (args != null && args.size() > 0)
            delimiter = args.get(0);
        
        if (delimiter == null)
            println("Usage: test <delimiter>");
        else {
            String value = "CN=SecurityAnalysis,CN=Users,DC=oim,DC=localhost|foo";
            String[] tokens = value.split(delimiter);
        
            println("Tokenizing: " + value);
            println("Tokens:");
            for (int i = 0 ; i < tokens.length ; i++) 
                println(tokens[i]);
        }
    }

    
    public void testCreationOfNullLinks() throws Exception {

        println("Num null links: " + fetchNumNullLinks());
        
        // First make sure that an identity with name "identity1" exists
        String id1 = null;
        Identity identity1 = _context.getObjectByName(Identity.class, "identity1");
        if (identity1 == null) {
            println("creating identity for first time");
            identity1 = new Identity();
            identity1.setName("identity1");
            _context.saveObject(identity1);
            _context.commitTransaction();
        }
        
        // Get the existing "identity1"'s id
        identity1 = _context.getObjectByName(Identity.class, "identity1");
        id1 = identity1.getId();
        println("identityId: " + id1);

        // Create a new identity with the same name "identity1"
        // try to create a new link for this identity
        Identity newIdentity = new Identity();
        newIdentity.setName("identity1");
        Link link1 = new Link();
        link1.setName("link1");
        link1.setIdentity(newIdentity);
        link1.setNativeIdentity("nativeIdentity1");
        _context.saveObject(link1);
        _context.saveObject(newIdentity);

        try {
            // Now try to unlock the existing identity "identity1"
            // It will fail.
            identity1 = _context.getObjectById(Identity.class, id1);
            newIdentity.setLock(null);
            _context.commitTransaction();
        } catch (Exception ex) {
            
            // Catch the exception. 
            // decache and try to fetch the identity again
            // and commit.
            println("failed once");
            _context.decache();
            _context.rollbackTransaction();
            identity1 = _context.getObjectById(Identity.class, id1);
            identity1.setLock(null);
            try {
                // this will succeed
                _context.commitTransaction();
            } catch (Exception ex1) {
                // should never get here
                println("failed twice");
            }
        }
        
        println("Num null links: " + fetchNumNullLinks());
    }
    
    private int fetchNumNullLinks() throws Exception {
        
        Filter filter = Filter.isnull("identity");
        QueryOptions options = new QueryOptions(filter);
        List<Link> links = _context.getObjects(Link.class, options);
        return links.size();
    }

    /**
     * See how long it takes to create a big has table.
     */
    public void testBigMaps() {

        int size = 100000;

        HashMap<String,String> map = new HashMap<String,String>();

        println("Building map...");
        for (int i = 0 ; i < size ; i++)  {
            String uuid = Util.uuid();
            map.put(uuid, uuid);
        }
        println("Finished");
    }

    public void testTarget(SailPointObject obj) {
        println("Class: " + obj.getClass().getSimpleName());
    }

    public void testPersistenceWrapper(List<String> args)
        throws Exception {

        List l = new sailpoint.tools.xml.PersistentArrayList<String>();

        if (!(l instanceof java.util.List))
            throw new Exception("not a List");

        if (!(l instanceof java.util.Collection))
            throw new Exception("not a Collection");

        if (!(l instanceof java.util.ArrayList))
            throw new Exception("not a ArrayList");

    }

    public void testRoleDifferencer(List<String> args)
        throws Exception {

        //RoleDifferencer rd = new RoleDifferencer(_context);
        //rd.test(args);

        String roleName = "NT IT Role";

        // simulate a role change and launch the workflow
        Bundle role = _context.getObject(Bundle.class, roleName);
        if (role == null)
            throw new GeneralException("Invalid role: " + roleName);

        // remove inheritance
        role.setInheritance(null);

        // pretent we're the modeler
        RoleLifecycler cycler = new RoleLifecycler(_context);
        cycler.approve(role);

    }

    public void simulateHibernateError(List<String> args)
        throws Exception {


        // load an identity
        Identity ident = _context.getObject(Identity.class, "Adam.Kennedy");

        UIConfig uiConfig = _context.getObjectByName(UIConfig.class, UIConfig.OBJ_NAME);

        List<Bundle> roles = ident.getAssignedRoles();
        if (roles != null) {
            for (Bundle role : roles) {
                String name = role.getName();
            }
        }

        List<Link> links = ident.getLinks();
        for (Link link : links) {
            String linkId = link.getId();
        }

        println("*** Before decache ***");
        _context.printStatistics();

        _context.decache();

        println("*** Before attach ***");
        _context.printStatistics();

        _context.attach(ident);

        println("*** After attach ***");
        _context.printStatistics();

        _context.commitTransaction();
    }

    public void moveLink(List<String> args) {
        try {
            String appname = "Pseudo";
            Application app = _context.getObjectByName(Application.class, appname);
            if (app == null) {
                app = new Application();
                app.setName(appname);
                _context.saveObject(app);
                _context.commitTransaction();
            }

            String srcname = "linksrc";
            Identity ident = _context.getObjectByName(Identity.class, srcname);
            if (ident != null) {
                _context.removeObject(ident);
                _context.commitTransaction();
            }

            ident = new Identity();
            ident.setName(srcname);

            Link link = new Link();
            link.setApplication(app);
            link.setNativeIdentity("obscurelinkidentity1");
            ident.add(link);

            link = new Link();
            link.setApplication(app);
            link.setNativeIdentity("obscurelinkidentity2");
            ident.add(link);

            _context.saveObject(ident);
            _context.commitTransaction();

            String destname = "linkdest";
            ident = _context.getObjectByName(Identity.class, destname);
            if (ident != null) {
                _context.removeObject(ident);
                _context.commitTransaction();
            }

            ident = new Identity();
            ident.setName(destname);
            link = new Link();
            link.setApplication(app);
            link.setNativeIdentity("obscurelinkidentity3");
            ident.add(link);
            _context.saveObject(ident);
            _context.commitTransaction();

            // get a completely new connection to avoid the spooky
            // cached collections crap
            _context.reconnect();

            QueryOptions ops = new QueryOptions();
            ops.add(Filter.ignoreCase(Filter.eq("nativeIdentity", "obscurelinkidentity1")));
            List<Link> links = _context.getObjects(Link.class, ops);
            if (links == null)
                println("No links returned by query!");
            else if (links.size() != 1)
                println("More than one link returned by query!");
            else {
                link = links.get(0);
                Identity src = link.getIdentity();
                Identity dest = _context.getObject(Identity.class, destname);

                src.remove(link);
                _context.saveObject(src);
                _context.commitTransaction();

                link.setManuallyCorrelated(true);
                dest.add(link);
                _context.saveObject(dest);
                _context.commitTransaction();

                _context.reconnect();
                ident = _context.getObject(Identity.class, srcname);
                dumpLinks(ident);

                ident = _context.getObject(Identity.class, destname);
                dumpLinks(ident);
            }
        }
        catch (Throwable t) {
            println(t);
            println(Util.stackToString(t));
        }
    }

    private void dumpLinks(Identity ident) {
        List<Link> links = ident.getLinks();
        if (links == null || links.size() == 0)
            println("Identity " + ident.getName() + " has no links");
        else {
            println("Identity " + ident.getName() + " has " +
                    Util.itoa(links.size()) + " links");
            for (int i = 0 ; i < links.size() ; i++) {
                Link link = links.get(i);
                if (link == null)
                    println(Util.itoa(i) + " is null");
                else
                    println(Util.itoa(i) + " " + link.getNativeIdentity());
            }
        }
    }

    public void diffLinks(List<String> args) {
        try {
            Application app = new Application();
            app.setName("Pseudo");

            Link link = new Link();
            link.setApplication(app);
            List values = new ArrayList();
            values.add("a");
            values.add("b");
            values.add("c");
            link.setAttribute("groupmbr", values);

            Identity i1 = new Identity();
            i1.add(link);

            link = new Link();
            link.setApplication(app);
            values = new ArrayList();
            values.add("b");
            values.add("c");
            values.add("d");
            link.setAttribute("groupmbr", values);

            Identity i2 = new Identity();
            i2.add(link);

            IdentityArchiver arch = new IdentityArchiver(_context);

            IdentitySnapshot s1 = arch.createSnapshot(i1);
            IdentitySnapshot s2 = arch.createSnapshot(i2);

            println(s1.toXml());
            println(s2.toXml());

            Differencer differ = new Differencer(_context);
            IdentityDifference diff = differ.diff(s1, s2, null, false);

            println(diff.toXml());
        }
        catch (Throwable t) {
            println(t);
        }
    }

    public void sendMail() throws GeneralException {
        EmailTemplate tmp = _context.getObject(EmailTemplate.class, "Certification");
        EmailOptions ops = new EmailOptions();
        ops.setTo("jeff.larson@sailpoint.com");
        _context.sendEmailNotification(tmp, ops);
    }

    public void roundTripXml(List<String> args) {
        try {
            String xml = Util.readFile(args.get(0));
            SailPointObject obj = (SailPointObject)SailPointObject.parseXml(_context, xml, true);
            println(obj.toXml());
        }
        catch (Throwable t) {
            println(t);
        }
    }


    public void runJDBCSearch() {

        UnitTestFactory utf = UnitTestFactory.getFactory();
        SessionFactory sf = utf.getSessionFactory();
        Session s = sf.openSession();

        try {
            Connection con = _context.getJdbcConnection();

            String sql = "select distinct identity0_.name as col_0_0_, identity0_.id as col_1_0_ from spt_identity identity0_  where rownum <= ?";
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setInt(1, 100);
            ResultSet rs = ps.executeQuery();
            int count = 0;
            while (rs.next())
                count++;

            println("JDBC result had " + count + " rows");


            sql = "select * from ( select distinct identity0_.name as col_0_0_, identity0_.id as col_1_0_ from spt_identity identity0_ ) where rownum >= ? and rownum <= ?";
            ps = con.prepareStatement(sql);
            ps.setInt(1, 0);
            ps.setInt(2, 100);
            rs = ps.executeQuery();
            count = 0;
            while (rs.next())
                count++;

            println("JDBC second result had " + count + " rows");
        }
        catch (Exception e) {
            println(e);
        }

    }

    public void runJDBCSearch2() {

        UnitTestFactory utf = UnitTestFactory.getFactory();
        SessionFactory sf = utf.getSessionFactory();
        Session s = sf.openSession();

        try {
            Connection con = _context.getJdbcConnection();

            String sql = "select * from ( select distinct identity0_.name as col_0_0_, identity0_.id as col_1_0_ from spt_identity identity0_ ) where rownum <= ?";
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setInt(1, 100);
            ResultSet rs = ps.executeQuery();
            int count = 0;
            while (rs.next())
                count++;

            println("JDBC result had " + count + " rows");


            sql = "select * from ( select distinct identity0_.name as col_0_0_, identity0_.id as col_1_0_ from spt_identity identity0_ ) where rownum >= ? and rownum <= ?";
            ps = con.prepareStatement(sql);
            ps.setInt(1, 0);
            ps.setInt(2, 100);
            rs = ps.executeQuery();
            count = 0;
            while (rs.next())
                count++;

            println("JDBC second result had " + count + " rows");
        }
        catch (Exception e) {
            println(e);
        }

    }

    public void runHibernateSearch() {

        UnitTestFactory utf = UnitTestFactory.getFactory();
        SessionFactory sf = utf.getSessionFactory();
        Session s = sf.openSession();

        int maxResults = 100;

        String hql = "select distinct identityAlias.name, identityAlias.id from sailpoint.object.Identity identityAlias order by identityAlias.name";
        Query q = s.createQuery(hql);
        q.setMaxResults(maxResults);

        List rows = q.list();
        // there will be 10 rows
        println("Result had " + rows.size() + " rows");

        q = s.createQuery(hql);
        q.setFirstResult(1);
        q.setMaxResults(maxResults);

        rows = q.list();
        // there will be 100 rows
        println("Result had " + rows.size() + " rows");
    }

    public void runHibernateSearch2() {

        UnitTestFactory utf = UnitTestFactory.getFactory();
        SessionFactory sf = utf.getSessionFactory();
        Session s = sf.openSession();

        int maxResults = 100;

        String hql = "select distinct identityAlias.name, identityAlias.id from sailpoint.object.Identity identityAlias";
        Query q = s.createQuery(hql);
        q.setMaxResults(maxResults);

        List rows = q.list();
        // there will be 10 rows
        println("Result had " + rows.size() + " rows");

        q = s.createQuery(hql);
        q.setFirstResult(1);
        q.setMaxResults(maxResults);

        rows = q.list();
        // there will be 100 rows
        println("Result had " + rows.size() + " rows");

        q = s.createQuery(hql);
        q.setFirstResult(0);
        q.setMaxResults(maxResults);

        rows = q.list();
        // there will be 100 rows
        println("Result had " + rows.size() + " rows");


        String sql = "select distinct identityAlias.name, identityAlias.id from spt_identity identityAlias";
        q = s.createSQLQuery(sql);
        q.setMaxResults(maxResults);

        rows = q.list();
        // there will be 100 rows
        println("Result had " + rows.size() + " rows");
    }

    public void runSailPointSearch(List<String> args) throws GeneralException {

        List rows;

        rows = doQuery(1);
        println("Result had " + Util.itoa(rows.size()) + " rows");

        rows = doQuery(105);
        println("Result had " + Util.itoa(rows.size()) + " rows");
    }

    private List doQuery(int max) throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.setDistinct(true);
        //ops.setIgnoreCase(true);
        ops.setResultLimit(max);
        List<String> props = new ArrayList<String>();
        props.add("name");
        props.add("id");
        Iterator<Object[]> result = _context.search(Identity.class, ops, props);

        List<Object[]> rows = new ArrayList<Object[]>();
        while (result.hasNext()) {
            rows.add(result.next());
        }

        return rows;
    }

    public void runCertPerformance(List<String> args) throws GeneralException {

        if (args.size() == 0) {

            QueryOptions ops = new QueryOptions();
            // ops.add(Filter.like("name", "a", Filter.MatchMode.START));
            ops.add(Filter.eq("managerStatus", true));
            List<String> props = new ArrayList<String>();
            props.add("name");
            Iterator<Object[]> result = _context.search(Identity.class, ops, props);
            int max = 20;
            int count = 0;

            // load the ids so the certification process can reconnect
            List<String> managers = new ArrayList<String>();
            while (result.hasNext() && count < max) {
                String name = (String)(result.next()[0]);
                managers.add(name);
                count++;
            }

            // now do them
            for (String name : managers) {
                println(name);
                certify(name);
            }
        }
        else {
            String name = args.get(0);

            Meter.reset();
            Meter.enter(195, "RandomTest: outer");

            certify(name);

            Meter.exit(195);
            Meter.report();
        }
    }

    void certify(String name) throws GeneralException {

        Identity user = _context.getObject(Identity.class, name);
        if (user == null)
            println("Invalid user!");
        else {

            CertificationBuilderFactory builderFactory = new CertificationBuilderFactory(_context);
            CertificationBuilder builder = builderFactory.getManagerCertBuilder(user);

            Certificationer c = new Certificationer(_context);
            Meter.enter(196, "RandomTest: generate");
            Certification cert = c.generateCertification(null, builder.getContext());
            Meter.exit(196);
            if (cert == null)
                println("Unable to generate certification!");
            else {
                Meter.enter(197, "RandomTest: start");
                c.start(cert);
                Meter.exit(197);

            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Serialization
    //
    //////////////////////////////////////////////////////////////////////

    void testSerialization() throws GeneralException {

        String filePath = "serialization.tmp";

        try {
            FileOutputStream fos = new FileOutputStream(filePath);
            ObjectOutputStream oos = new ObjectOutputStream(fos);

            //SailPointObject obj _context.getObject(...);
            SailPointObject obj = null;

            oos.writeObject(obj);
            oos.flush();

            FileInputStream fis = new FileInputStream(filePath);
            ObjectInputStream ois = new ObjectInputStream(fis);

            Object o = ois.readObject();

            // compare
        }
        catch (java.io.IOException e) {
            throw new GeneralException(e);
        }
        catch (java.lang.ClassNotFoundException e) {
            throw new GeneralException(e);
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Pretty pictures
    //
    //////////////////////////////////////////////////////////////////////

    public void drawAPicture() throws GeneralException {

        try {

            BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();

            g.setColor(Color.black);
            g.fillRect(0, 0, 100, 100);

            g.setColor(Color.red);
            g.fillOval(20, 20, 20, 20);

            g.fillOval(60, 60, 20, 20);

            int[] xpoints = {40, 60};
            int[] ypoints = {40, 60};
            g.drawPolyline(xpoints, ypoints, 2);

            String name = "picture.jpg";
            File outputFile = new File(name);
            ImageIO.write(image, "jpg", outputFile);
            println("Saved " + name);
        }
        catch (java.io.IOException e) {
            throw new GeneralException(e);
        }
    }

    
}
